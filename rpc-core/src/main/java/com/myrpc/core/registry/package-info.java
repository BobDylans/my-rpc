/**
 * 注册中心层：服务注册与发现抽象。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@code ServiceRegistry} —— 接口（{@code register}/{@code unregister}/{@code lookup}）</li>
 *   <li>{@code ZkServiceRegistry} —— 基于 Zookeeper + Curator 的实现</li>
 *   <li>{@code ServiceDiscoveryCache} —— 消费者侧的本地地址缓存 + Watcher</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/09-Zookeeper集成与服务注册} 和
 * {@link /后端知识/中间件/10-服务发现与客户端缓存}
 */
package com.myrpc.core.registry;
