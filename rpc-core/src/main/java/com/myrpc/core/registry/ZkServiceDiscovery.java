package com.myrpc.core.registry;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务发现的 Zookeeper 实现 —— 查询地址 + 监听变化。
 *
 * <h2>两个核心能力</h2>
 * <ol>
 *   <li>{@link #lookup} —— 从 ZK 查当前服务的所有实例地址</li>
 *   <li>{@link #subscribe} —— 用 {@link PathChildrenCache} 监听子节点变化，
 *       实例增减时触发回调</li>
 * </ol>
 *
 * <h2>为什么用 PathChildrenCache 而不是原生 Watcher？</h2>
 * <p>ZK 原生 Watcher 是<b>一次性</b>的：触发一次后失效，要重新注册。
 * 如果两次注册之间发生了节点变化，就会丢事件（典型竞态条件）。
 * Curator 的 PathChildrenCache 内部持续维护缓存 + 自动重新注册 Watcher，
 * 不会丢事件，用起来更可靠。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/10-服务发现与客户端缓存} §1.2 §1.3
 */
public class ZkServiceDiscovery implements ServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ZkServiceDiscovery.class);

    private final CuratorFramework client;
    private final String rootPath;

    /** 每个服务的 PathChildrenCache 实例（启动时建，关闭时统一释放） */
    private final Map<String, PathChildrenCache> watchers = new ConcurrentHashMap<>();

    /**
     * @param client   已连接的 Curator 客户端
     * @param rootPath  注册根路径（和 Provider 侧的 ZkServiceRegistry 必须一致）
     */
    public ZkServiceDiscovery(CuratorFramework client, String rootPath) {
        this.client = client;
        this.rootPath = rootPath;
    }

    @Override
    public List<InetSocketAddress> lookup(String serviceName) {
        String servicePath = rootPath + "/" + serviceName;
        try {
            if (client.checkExists().forPath(servicePath) == null) {
                return Collections.emptyList();
            }
            List<String> children = client.getChildren().forPath(servicePath);
            List<InetSocketAddress> addresses = new ArrayList<>(children.size());
            for (String child : children) {
                addresses.add(parseAddress(child));
            }
            return addresses;
        } catch (Exception e) {
            log.error("查询服务地址失败: {}", servicePath, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void subscribe(String serviceName, ServiceChangeListener listener) {
        String servicePath = rootPath + "/" + serviceName;

        // 复用已建的 watcher（避免对同一服务重复订阅）
        if (watchers.containsKey(serviceName)) {
            log.debug("服务 [{}] 已订阅，跳过重复订阅", serviceName);
            return;
        }

        try {
            PathChildrenCache cache = new PathChildrenCache(client, servicePath, true);
            PathChildrenCacheListener cacheListener = (client, event) -> {
                PathChildrenCacheEvent.Type type = event.getType();
                if (type == PathChildrenCacheEvent.Type.CHILD_ADDED
                        || type == PathChildrenCacheEvent.Type.CHILD_REMOVED
                        || type == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
                    // 有变化 → 重新查全量地址 → 回调通知上层刷新缓存
                    List<InetSocketAddress> latest = lookup(serviceName);
                    log.info("服务 [{}] 地址列表变化 ({}): {} 个实例", serviceName, type, latest.size());
                    listener.onChange(serviceName, latest);
                }
            };
            cache.getListenable().addListener(cacheListener);
            cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
            watchers.put(serviceName, cache);
            log.info("已订阅服务 [{}] 的地址变化", serviceName);
        } catch (Exception e) {
            log.error("订阅服务变化失败: {}", servicePath, e);
        }
    }

    /** 关闭所有 watcher，释放资源 */
    public void close() {
        watchers.values().forEach(c -> {
            try { c.close(); } catch (Exception e) {
                log.warn("关闭 PathChildrenCache 失败", e);
            }
        });
        watchers.clear();
    }

    /** 把 "127.0.0.1:18090" 拆成 InetSocketAddress */
    private InetSocketAddress parseAddress(String child) {
        int idx = child.lastIndexOf(':');
        String host = child.substring(0, idx);
        int port = Integer.parseInt(child.substring(idx + 1));
        return new InetSocketAddress(host, port);
    }
}
