/**
 * 客户端：基于 Netty Bootstrap 发送请求，Future 异步等待响应。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@code RpcClient} —— 封装 Bootstrap + Channel 管理</li>
 *   <li>{@code NetTransport} —— 底层网络传输抽象</li>
 *   <li>{@code RpcResponseHandler} —— Pipeline 入站 Handler，收到响应完成 Future</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/07-客户端-动态代理与Future异步}
 */
package com.myrpc.core.client;
