package com.myrpc.core.registry;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 服务发现接口 —— Consumer 侧从注册中心查询服务地址列表。
 *
 * <p>与 {@link ServiceRegistry}（Provider 侧"写"地址）对称：
 * <pre>
 *   Provider 侧:  ServiceRegistry.register(name, addr)    ← 写：往 ZK 挂临时节点
 *   Consumer 侧:  ServiceDiscovery.lookup(name) → [addr]  ← 读：从 ZK 查地址
 * </pre>
 *
 * <h2>为什么除了 lookup 还要 subscribe？</h2>
 * <p>ZK 的 Watcher 是"事件推送"机制：节点变化时 ZK 主动通知客户端，
 * 而不是让客户端轮询。{@link #subscribe} 注册监听，地址变化时回调更新本地缓存，
 * 实现"加机器即生效、宕机即剔除"。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/10-服务发现与客户端缓存} §1.1 §1.3
 */
public interface ServiceDiscovery {

    /**
     * 查询服务的所有实例地址。
     *
     * @param serviceName 服务名（接口全限定名）
     * @return 地址列表，可能为空（服务未注册或全部下线）
     */
    List<InetSocketAddress> lookup(String serviceName);

    /**
     * 订阅服务变化：当该服务的实例列表变化时（新增/下线），触发回调。
     * 实现方应配合本地缓存，变更时自动刷新。
     *
     * @param serviceName 服务名
     * @param listener    变化回调
     */
    void subscribe(String serviceName, ServiceChangeListener listener);

    /** 服务地址变化监听器（函数式接口） */
    @FunctionalInterface
    interface ServiceChangeListener {
        /**
         * @param serviceName 发生变化的服务名
         * @param newAddresses 变化后的地址列表
         */
        void onChange(String serviceName, List<InetSocketAddress> newAddresses);
    }
}
