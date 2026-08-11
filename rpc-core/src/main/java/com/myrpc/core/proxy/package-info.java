/**
 * 代理层：消费者侧生成接口的代理对象，把方法调用转成网络请求。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@code ClientProxyFactory} —— 基于 JDK {@code Proxy.newProxyInstance}，实现 {@code InvocationHandler}</li>
 *   <li>{@code UnprocessedRequests} —— {@code requestId → CompletableFuture} 映射，异步匹配响应</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/07-客户端-动态代理与Future异步}
 */
package com.myrpc.core.proxy;
