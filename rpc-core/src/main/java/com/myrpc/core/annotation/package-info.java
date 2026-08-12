/**
 * 注解驱动层 —— 自定义注解 + Spring BeanPostProcessor 自动装配。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@link com.myrpc.core.annotation.RpcService} —— 标在实现类上，Provider 侧自动暴露服务</li>
 *   <li>{@link com.myrpc.core.annotation.RpcReference} —— 标在字段上，Consumer 侧自动注入代理</li>
 *   <li>{@code RpcServiceBeanPostProcessor} —— 扫描 @RpcService 注册到 RpcServer</li>
 *   <li>{@code RpcReferenceBeanPostProcessor} —— 扫描 @RpcReference 注入代理对象</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/12-注解驱动与Spring集成}
 */
package com.myrpc.core.annotation;
