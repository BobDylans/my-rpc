package com.myrpc.core.annotation;

import com.myrpc.core.client.RpcClient;
import com.myrpc.core.proxy.RpcClientProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

/**
 * Consumer 侧 BeanPostProcessor —— 扫描 {@link RpcReference} 注解，自动注入代理。
 *
 * <p>原理：Spring 容器在每个 bean 初始化<b>之前</b>调 {@link #postProcessBeforeInitialization}。
 * 在这里反射扫 bean 的所有字段，找有没有 {@code @RpcReference}：
 * <ul>
 *   <li>有 → 用 {@code RpcClientProxy} 生成接口代理 → 反射塞进字段</li>
 *   <li>没有 → 跳过</li>
 * </ul>
 *
 * <h2>为什么选 beforeInitialization 而不是 after？</h2>
 * <p>注入的代理是<b>依赖</b>，应该在 bean 使用前就位。
 * before 阶段注入，bean 的 {@code @PostConstruct} 或 {@code afterPropertiesSet}
 * 执行时就能直接用这个代理字段了。after 阶段注入的话，那些初始化方法里用不了代理。
 *
 * <h2>为什么不用 {@code @Autowired}？</h2>
 * <p>{@code @Autowired} 找的是 Spring 容器里的 bean。但 RPC 代理不是预注册的 bean，
 * 是运行时用 {@code Proxy.newProxyInstance} 动态生成的。
 * 用专用注解 + BeanPostProcessor，才能在注入时机动态生成代理塞进字段。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/12-注解驱动与Spring集成} §1.3 §1.4
 */
public class RpcReferenceBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RpcReferenceBeanPostProcessor.class);

    private final RpcClient rpcClient;

    public RpcReferenceBeanPostProcessor(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        // 扫所有字段（包括父类的）
        while (beanClass != null && beanClass != Object.class) {
            for (Field field : beanClass.getDeclaredFields()) {
                RpcReference annotation = field.getAnnotation(RpcReference.class);
                if (annotation == null) {
                    continue;
                }
                injectProxy(bean, field, annotation);
            }
            beanClass = beanClass.getSuperclass();
        }
        return bean;
    }

    /**
     * 往字段里注入代理对象。
     */
    private void injectProxy(Object bean, Field field, RpcReference annotation) {
        Class<?> serviceInterface = resolveInterface(annotation, field);
        log.info("发现 @RpcReference：字段 [{}] → 注入 [{}] 代理",
                field.getName(), serviceInterface.getName());

        // 生成代理（RpcClientProxy 内部用 JDK Proxy.newProxyInstance）
        RpcClientProxy proxy = new RpcClientProxy(rpcClient, serviceInterface);
        Object proxyInstance = proxy.getProxy();

        // 反射塞进字段（private 字段也要 accessible）
        field.setAccessible(true);
        try {
            field.set(bean, proxyInstance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("注入 @RpcReference 失败: " + field, e);
        }
    }

    /**
     * 解析注入的接口：
     * <ol>
     *   <li>注解显式指定了 → 用注解的</li>
     *   <li>没指定 → 用字段声明类型</li>
     *   <li>字段类型不是接口 → 报错（RPC 必须基于接口代理）</li>
     * </ol>
     */
    private Class<?> resolveInterface(RpcReference annotation, Field field) {
        Class<?> explicit = annotation.iface();
        if (explicit != void.class) {
            return explicit;
        }

        Class<?> fieldType = field.getType();
        if (!fieldType.isInterface()) {
            throw new IllegalStateException("@" + RpcReference.class.getSimpleName()
                    + " 标在非接口字段上: " + field.getName()
                    + "（类型: " + fieldType.getName() + "）。"
                    + "RPC 代理必须基于接口，请把字段类型改成接口。");
        }
        return fieldType;
    }
}
