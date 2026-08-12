package com.myrpc.test.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 12 注解驱动集成测试 —— Spring 容器 + @RpcService + @RpcReference。
 *
 * <p>验证流程：
 * <ol>
 *   <li>启动 Spring 容器（{@link SpringRpcConfig}）</li>
 *   <li>{@code @RpcService SpringHelloServiceImpl} 被扫描 → BeanPostProcessor 注册到 RpcServer</li>
 *   <li>{@code @RpcReference HelloService} 字段被 BeanPostProcessor 注入代理</li>
 *   <li>调 {@code businessService.greet("world")} → 代理走网络 → 服务端反射调用 → 返回结果</li>
 * </ol>
 *
 * <p>这就是"像用 @Service/@Autowired 一样用 RPC"的完整验证。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/12-注解驱动与Spring集成}
 */
class SpringAnnotationIntegrationTest {

    @Test
    void annotationDrivenRpcCallWorks() {
        // 启动 Spring 容器，会自动：起 RpcServer → 注册 @RpcService → 注入 @RpcReference 代理
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(SpringRpcConfig.class)) {

            // 拿到业务类，它里面有个 @RpcReference 字段已经被注入了代理
            SpringBusinessService businessService = ctx.getBean(SpringBusinessService.class);

            // 调业务方法 —— 内部调的是 helloService 代理，走网络到服务端
            String result = businessService.greet("world");

            // 验证：服务端返回的带 "from Spring RPC server"
            assertEquals("Hi, world (from Spring RPC server)", result,
                    "注解驱动的 RPC 调用应成功返回结果");
        }
    }

    @Test
    void proxyIsInjectedNotRealImpl() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(SpringRpcConfig.class)) {

            SpringBusinessService businessService = ctx.getBean(SpringBusinessService.class);

            // 拿到 helloService 字段的实际对象类型，应该是 JDK 代理（不是 SpringHelloServiceImpl）
            try {
                java.lang.reflect.Field f = SpringBusinessService.class.getDeclaredField("helloService");
                f.setAccessible(true);
                Object injected = f.get(businessService);

                // JDK 动态代理的类名形如 "com.sun.proxy.$Proxy0"
                assertTrue(injected.getClass().getName().contains("Proxy"),
                        "注入的应该是代理对象，实际类型: " + injected.getClass().getName());

                // 不应该是真实实现类
                assertFalse(injected instanceof SpringHelloServiceImpl,
                        "注入的不应是真实实现类，而是代理");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                fail("反射访问 helloService 字段失败: " + e.getMessage());
            }
        }
    }
}
