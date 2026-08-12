package com.myrpc.core.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务发现本地缓存 —— 减少 ZK 查询 + ZK 抖动时仍可用。
 *
 * <p>这是 {@link ZkServiceDiscovery} 上层的一层包装，职责：
 * <ol>
 *   <li>首次查询走 ZK，结果缓存到本地 Map</li>
 *   <li>订阅 ZK 变化，变化时刷新本地缓存</li>
 *   <li>后续查询直接从本地缓存取，不再访问 ZK</li>
 * </ol>
 *
 * <h2>为什么要这一层缓存？</h2>
 * <ul>
 *   <li><b>性能</b>：每次调用都查 ZK 太慢（毫秒级网络往返），缓存是纳秒级</li>
 *   <li><b>容错</b>：ZK 抖动时，本地缓存还能撑着用旧地址，避免调用链雪崩</li>
 *   <li><b>减轻 ZK 压力</b>：高并发下避免 ZK 成为查询瓶颈</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/10-服务发现与客户端缓存} §1.2
 */
public class ServiceDiscoveryCache {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryCache.class);

    private final ServiceDiscovery discovery;

    /** 服务名 → 地址列表缓存（ZK 变化时由 listener 刷新） */
    private final Map<String, List<InetSocketAddress>> cache = new ConcurrentHashMap<>();

    public ServiceDiscoveryCache(ServiceDiscovery discovery) {
        this.discovery = discovery;
    }

    /**
     * 首次查询走 ZK 并订阅变化，之后直接返回缓存。
     *
     * <p>{@code computeIfAbsent} 保证"首次查询"只走一次 ZK，多线程并发时
     * 不会对同一服务重复查询 ZK。
     */
    public List<InetSocketAddress> get(String serviceName) {
        return cache.computeIfAbsent(serviceName, name -> {
            // 首次查询：从 ZK 拉 + 订阅变化（订阅后，变化时自动更新这个 cache）
            List<InetSocketAddress> addrs = discovery.lookup(name);
            discovery.subscribe(name, this::onAddressChange);
            log.info("首次拉取服务 [{}] 地址: {} 个实例", name, addrs.size());
            return addrs;
        });
    }

    /**
     * ZK 变化回调：用新地址列表刷新缓存。
     *
     * <p>注意用 {@code put} 而非 {@code computeIfAbsent} —— 这是更新不是初始化。
     * 即使此时调用方正在 get()，{@code ConcurrentHashMap} 的写不会阻塞读。
     */
    private void onAddressChange(String serviceName, List<InetSocketAddress> newAddresses) {
        if (newAddresses.isEmpty()) {
            // 保留空列表（不要删 key），让调用方知道"当前没可用实例"
            cache.put(serviceName, Collections.emptyList());
            log.warn("服务 [{}] 无可用实例", serviceName);
        } else {
            cache.put(serviceName, newAddresses);
            log.info("服务 [{}] 缓存已刷新: {} 个实例", serviceName, newAddresses.size());
        }
    }

    /** 当前缓存的服务数（测试/监控用） */
    public int cachedServiceCount() {
        return cache.size();
    }
}
