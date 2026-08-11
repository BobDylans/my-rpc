/**
 * 服务端：基于 Netty ServerBootstrap 启动，接收请求并通过反射调用目标方法。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@code RpcServer} —— 封装 ServerBootstrap，绑定端口启动</li>
 *   <li>{@code ServiceRegistry} —— 本地服务实例注册表（接口名 → 实现对象）</li>
 *   <li>{@code RpcRequestHandler} —— Pipeline 业务 Handler，反射调用方法返回响应</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/06-服务端-Netty与反射调用}
 */
package com.myrpc.core.server;
