package com.myrpc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在字段上 —— Consumer 侧自动注入 RPC 代理。
 *
 * <p>使用方式：
 * <pre>
 * &#64;Service
 * public class OrderService {
 *     &#64;RpcReference
 *     private HelloService helloService;  // 注入的是代理对象，调用走网络
 *
 *     public void greet() { helloService.sayHi("world"); }
 * }
 * </pre>
 *
 * <p>或者显式指定接口：
 * <pre>
 * &#64;RpcReference(iface = HelloService.class)
 * private HelloService helloService;
 * </pre>
 *
 * <p>由 {@code RpcReferenceBeanPostProcessor} 在 bean 初始化前扫描处理：
 * 反射扫字段 → 找到 {@code @RpcReference} → 生成代理 → {@code field.set(bean, proxy)}
 *
 * <h2>为什么不直接用 {@code @Autowired}？</h2>
 * <p>{@code @Autowired} 是从 Spring 容器找一个已注册的 bean 注入。
 * 但 RPC 代理不是容器里的 bean——它是运行时动态生成的（JDK Proxy.newProxyInstance）。
 * 用专用注解 + BeanPostProcessor，才能在注入时机生成代理塞进字段。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/12-注解驱动与Spring集成} §1.1 §1.3
 */
@Target(ElementType.FIELD)          // 标在字段上
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReference {

    /**
     * 指定接口类型。默认 {@code void.class} 表示用字段声明类型。
     * 大多数情况下字段类型就是接口，不用显式指定。
     */
    Class<?> iface() default void.class;
}
