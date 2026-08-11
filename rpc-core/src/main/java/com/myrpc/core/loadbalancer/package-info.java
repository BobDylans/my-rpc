/**
 * 负载均衡层：从多个服务端实例中选一个。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@code LoadBalancer} —— 接口（{@code select}）</li>
 *   <li>{@code RandomLoadBalancer} —— 随机策略</li>
 *   <li>{@code RoundRobinLoadBalancer} —— 轮询策略</li>
 *   <li>{@code ConsistentHashLoadBalancer} —— 一致性哈希策略</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/11-负载均衡-随机轮询一致性哈希}
 */
package com.myrpc.core.loadbalancer;
