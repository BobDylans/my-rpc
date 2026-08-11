package com.myrpc.core.server;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册表 —— 接口全限定名 → 实现实例。
 *
 * <p>这是服务端的心脏：消费者请求里带着 {@code interfaceName}（如
 * {@code com.myrpc.api.HelloService}），服务端靠它查到具体的实现对象，
 * 才能做反射调用。
 *
 * <p>本阶段（06）服务实现<b>手动注册</b>，不接注册中心：
 * <pre>
 *   RpcServer server = new RpcServer(8080);
 *   server.registerService(HelloService.class, new HelloServiceImpl());
 * </pre>
 * 阶段 12 会用注解驱动（{@code @RpcService}）自动收集并注册。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/06-服务端-Netty与反射调用} §1.2
 */
public class ServiceRegistry {

    /**
     * key = 接口全限定名，value = 实现类实例。
     *
     * <p>为什么用 ConcurrentHashMap 而不是 HashMap？
     * 服务端是 Netty 多线程模型：多个 worker 线程可能同时处理请求并发读。
     * ConcurrentHashMap 保证并发读安全且无锁竞争（读操作不加锁）。
     * 将对应的方法注入
     */
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    /**
     * 注册一个服务实现。
     *
     * @param iface 服务接口（如 HelloService.class）
     * @param impl  实现类实例（如 new HelloServiceImpl()）
     */
    public void registerService(Class<?> iface, Object impl) {
        serviceMap.put(iface.getName(), impl);
    }

    /**
     * 按接口全限定名查服务实现。
     *
     * @param interfaceName 接口全限定名
     * @return 实现实例；未注册返回 null
     */
    public Object getService(String interfaceName) {
        return serviceMap.get(interfaceName);
    }

    /**
     * 服务是否已注册。
     */
    public boolean contains(String interfaceName) {
        return serviceMap.containsKey(interfaceName);
    }

    /**
     * 返回所有已注册的服务名（接口全限定名）。
     * 阶段 09 用：启动时把所有服务批量登记到注册中心。
     */
    public Set<String> getAllServiceNames() {
        return serviceMap.keySet();
    }
}
