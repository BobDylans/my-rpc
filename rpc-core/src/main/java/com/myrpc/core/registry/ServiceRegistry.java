package com.myrpc.core.registry;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 注册中心抽象 —— 服务名 ↔ 实例地址 的登记与查询。
 *
 * <p>与 {@link com.myrpc.core.server.ServiceRegistry} 区分：
 * <ul>
 *   <li>server.ServiceRegistry —— <b>本地</b>服务注册表：接口名 → 实现对象（本进程内）</li>
 *   <li>registry.ServiceRegistry —— <b>注册中心</b>：服务名 → 实例地址列表（跨进程，ZK 实现）</li>
 * </ul>
 *
 * <p>Provider 侧调 {@link #register}（启动时登记自己的地址），
 * Consumer 侧调 {@link #lookup}（调用前查可用地址）。
 * 对应 ZK 存储结构：
 * <pre>
 * /my-rpc
 *   └── com.myrpc.api.HelloService   (持久节点，服务名)
 *        ├── 127.0.0.1:18090          (临时节点，实例地址)
 *        └── 127.0.0.1:18091          (临时节点，session 断开自动消失)
 * </pre>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/09-Zookeeper集成与服务注册} §1.1
 */
public interface ServiceRegistry {

    /**
     * 注册：把服务名 → 实例地址登记到注册中心（Provider 启动时调用）。
     */
    void register(String serviceName, InetSocketAddress address);

    /**
     * 反注册：删除实例地址（Provider 优雅关闭时调用）。
     */
    void unregister(String serviceName, InetSocketAddress address);

    /**
     * 查询：按服务名拿所有可用实例地址（Consumer 调用前调用）。
     */
    List<InetSocketAddress> lookup(String serviceName);
}
