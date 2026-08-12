package com.myrpc.core.annotation;

import com.myrpc.core.server.RpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Arrays;

/**
 * Provider 侧 BeanPostProcessor —— 扫描 {@link RpcService} 注解，自动暴露服务。
 *
 * <p>原理：Spring 容器在每个 bean 初始化<b>之后</b>调 {@link #postProcessAfterInitialization}。
 * 在这里检查 bean 的类上有没有 {@code @RpcService}：
 * <ul>
 *   <li>有 → 取注解的 {@code iface}（或实现类第一个接口）→ 调 {@code RpcServer.registerService}</li>
 *   <li>没有 → 跳过，正常返回 bean</li>
 * </ul>
 *
 * <h2>为什么选 afterInitialization 而不是 before？</h2>
 * <p>{@code @RpcService} 标在实现类上，bean 要先实例化完成（属性注入完）才能暴露。
 * 在 before 阶段 bean 还没构造完，暴露出去被调用会 NPE。
 * after 阶段 bean 已经 ready，可以安全暴露。
 *
 * <h2>启动顺序</h2>
 * <p>必须保证 {@link RpcServer#start()} 在 BeanPostProcessor 处理之前完成，
 * 否则注册了服务但服务端还没监听端口。实践中用 {@code @DependsOn} 或
 * 把 RpcServer 声明为静态 bean 保证优先初始化。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/12-注解驱动与Spring集成} §1.2 §1.4
 */
public class RpcServiceBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RpcServiceBeanPostProcessor.class);

    private final RpcServer rpcServer;

    public RpcServiceBeanPostProcessor(RpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        RpcService annotation = beanClass.getAnnotation(RpcService.class);
        if (annotation == null) {
            return bean;  // 没标 @RpcService，跳过
        }

        // 取要暴露的接口
        Class<?> serviceInterface = resolveInterface(annotation, beanClass);
        log.info("发现 @RpcService：{} → 暴露接口 [{}]", beanClass.getSimpleName(), serviceInterface.getName());

        // 注册到 RpcServer（服务端会把这个 bean 当作接口的实现）
        rpcServer.registerService(serviceInterface, bean);

        return bean;
    }

    /**
     * 解析要暴露的接口：
     * <ol>
     *   <li>注解显式指定了 → 用注解的</li>
     *   <li>没指定 → 取实现类第一个接口（约定：实现类实现的第一个接口就是要暴露的）</li>
     *   <li>实现类没实现任何接口 → 报错（RPC 必须基于接口代理）</li>
     * </ol>
     */
    private Class<?> resolveInterface(RpcService annotation, Class<?> beanClass) {
        Class<?> explicit = annotation.iface();
        if (explicit != void.class) {
            return explicit;
        }

        Class<?>[] interfaces = beanClass.getInterfaces();
        if (interfaces.length == 0) {
            throw new IllegalStateException("@" + RpcService.class.getSimpleName()
                    + " 标在 " + beanClass.getName() + " 上，但该类没实现任何接口。"
                    + "RPC 必须基于接口代理，请实现一个接口或显式指定 iface。");
        }
        if (interfaces.length > 1) {
            log.warn("{} 实现了多个接口 {}，默认取第一个。"
                    + "如需指定，请用 @RpcService(iface = Xxx.class)",
                    beanClass.getSimpleName(), Arrays.toString(interfaces));
        }
        return interfaces[0];
    }
}
