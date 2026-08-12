package com.myrpc.core.loadbalancer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡 —— 相同 key 的请求始终落到同一实例，实例增减只影响相邻区段。
 *
 * <h2>核心思想</h2>
 * <ol>
 *   <li>把所有实例映射到 0 ~ 2^32-1 的哈希环上</li>
 *   <li>请求 key 也映射到环上，<b>顺时针</b>找最近一个实例</li>
 *   <li>实例增减只影响它所在的相邻区段，不全局洗牌</li>
 * </ol>
 *
 * <h2>为什么需要虚拟节点</h2>
 * <p>真实实例数少时（比如 3 个），直接映射到哈希环会导致<b>数据倾斜</b>：
 * 某些实例覆盖的环区间远大于其他实例，流量分配不均。
 * 解决：每个真实实例映射成 N 个虚拟节点（如 160 个），虚拟节点在环上均匀分布，
 * 3 × 160 = 480 个点，统计上覆盖区间接近相等。
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>需要<b>会话粘性</b>：同一用户/同一 key 的请求总落同一实例（利用本地缓存）</li>
 *   <li>分布式缓存场景：实例增减时只迁移部分 key，不全量洗牌</li>
 * </ul>
 *
 * <h2>实现说明</h2>
 * <ul>
 *   <li>哈希环用 {@link TreeMap}（红黑树，tailMap/firstEntry 都是 O(log N)）</li>
 *   <li>按 serviceName 分组维护各自哈希环：不同服务的实例列表不同</li>
 *   <li>实例列表变化时重建该服务的环（调用方在实例变化时调 {@link #refresh}）</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/11-负载均衡-随机轮询一致性哈希} §1.2
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    /** 每个真实实例映射的虚拟节点数（越大越均匀，但内存占用越多） */
    private static final int VIRTUAL_NODES = 160;

    /**
     * 哈希函数：FNV-1a 32 位变体。
     *
     * <p>为什么不用 {@code String.hashCode()}？JDK 的 hashCode 分布性一般，
     * 在哈希环上容易聚集。FNV-1a 简单且分布均匀，是哈希环的经典选择。
     */
    private static int hash(String str) {
        final int FNV_32_PRIME = 0x01000193;
        int hash = 0x811C9DC5;  // FNV offset basis
        for (int i = 0; i < str.length(); i++) {
            hash ^= str.charAt(i);
            hash *= FNV_32_PRIME;
        }
        return hash & 0x7FFFFFFF;  // 保证非负（TreeMap 不支持负 key 的 tailMap 逻辑）
    }

    /** 每个服务一个哈希环（服务名 → TreeMap<hash, 真实地址>） */
    private final Map<String, TreeMap<Integer, InetSocketAddress>> rings = new ConcurrentHashMap<>();

    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses, String key) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        // 用服务维度（这里用 addresses 的 toString 当 key）查/建环
        // 注意：真实场景 key 是 requestId 或业务 key（如 userId）
        String ringKey = addresses.toString();  // 地址列表相同时复用同一环
        TreeMap<Integer, InetSocketAddress> ring = rings.computeIfAbsent(ringKey, k -> buildRing(addresses));

        // key 映射到环上，顺时针找最近节点
        int hash = hash(key);
        // tailMap：返回 >= hash 的子映射（顺时针方向）
        SortedMap<Integer, InetSocketAddress> tail = ring.tailMap(hash);
        // 环尾：没有更大的节点 → 绕回首节点
        return tail.isEmpty() ? ring.firstEntry().getValue() : tail.get(tail.firstKey());
    }

    /**
     * 实例列表变化时重建该服务的哈希环。
     * 调用方（如 ServiceDiscoveryCache 变化回调）应主动触发。
     */
    public void refresh(String ringKey, List<InetSocketAddress> addresses) {
        rings.put(ringKey, buildRing(addresses));
    }

    /** 构建哈希环：每个实例 × VIRTUAL_NODES 个虚拟节点 */
    private TreeMap<Integer, InetSocketAddress> buildRing(List<InetSocketAddress> addresses) {
        TreeMap<Integer, InetSocketAddress> ring = new TreeMap<>();
        for (InetSocketAddress addr : addresses) {
            String addrStr = addr.getHostString() + ":" + addr.getPort();
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                // 虚拟节点名：真实地址 + #序号，保证各虚拟节点哈希不同
                int hash = hash(addrStr + "#" + i);
                ring.put(hash, addr);
            }
        }
        return ring;
    }
}
