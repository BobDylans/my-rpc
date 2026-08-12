package com.myrpc.core.loadbalancer;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 负载均衡接口 —— 从多个服务端实例中选一个。
 *
 * <p>阶段 10 先用最小实现（随机），阶段 11 会完整实现：
 * <ul>
 *   <li>{@code RandomLoadBalancer} —— 随机</li>
 *   <li>{@code RoundRobinLoadBalancer} —— 轮询</li>
 *   <li>{@code ConsistentHashLoadBalancer} —— 一致性哈希</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/11-负载均衡-随机轮询一致性哈希}
 */
public interface LoadBalancer {

    /**
     * @param addresses 候选地址列表
     * @param key       用于一致性哈希的 key（如 requestId）；随机/轮询可忽略
     * @return 选中的地址；列表为空返回 null
     */
    InetSocketAddress select(List<InetSocketAddress> addresses, String key);
}
