package com.myrpc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * 标在实现类上 —— Provider 侧自动暴露为 RPC 服务。
 *
 * <p>使用方式：
 * <pre>
 * &#64;RpcService
 * public class HelloServiceImpl implements HelloService {
 *     public String sayHi(String name) { return "Hi, " + name; }
 * }
 * </pre>
 *
 * <p>或者显式指定接口（当实现类实现了多个接口时）：
 * <pre>
 * &#64;RpcService(iface = HelloService.class)
 * public class HelloServiceImpl implements HelloService, Serializable { ... }
 * </pre>
 *
 * <p>由 {@code RpcServiceBeanPostProcessor} 在 bean 初始化后扫描处理：
 * 扫到这个注解 → 取 {@link #iface}（或实现类第一个接口）→ 调 {@code RpcServer.registerService}
 *
 * <h2>为什么加 {@code @Component} 元注解？</h2>
 * <p>加了后，标 {@code @RpcService} 的类会被 Spring 当作组件扫描注册为 bean。
 * 否则 Spring 不会创建它，{@code RpcServiceBeanPostProcessor} 也扫不到它。
 * 这样使用方标一个 {@code @RpcService} 就够了，不用再加 {@code @Component}。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/12-注解驱动与Spring集成} §1.1 §1.2
 */
@Target(ElementType.TYPE)          // 标在类上
@Retention(RetentionPolicy.RUNTIME) // 运行时保留（反射能读到）
@Component                          // 让 Spring 当作组件扫描，无需再标 @Component
public @interface RpcService {

    /**
     * 指定暴露的接口。默认 {@code void.class} 表示自动推断（取实现类的第一个接口）。
     * 实现多个接口时必须显式指定。
     */
    Class<?> iface() default void.class;
}
