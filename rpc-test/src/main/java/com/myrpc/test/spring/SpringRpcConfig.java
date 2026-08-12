package com.myrpc.test.spring;

import com.myrpc.core.annotation.RpcReferenceBeanPostProcessor;
import com.myrpc.core.annotation.RpcServiceBeanPostProcessor;
import com.myrpc.core.client.RpcClient;
import com.myrpc.core.server.RpcServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 阶段 12 Spring 配置类 —— 装配 RPC 框架与 Spring 容器。
 *
 * <h2>关键点：RpcServer 必须先于 BeanPostProcessor 启动</h2>
 * <p>{@code @RpcService} 标在 SpringHelloServiceImpl 上，容器创建它时会触发
 * {@code RpcServiceBeanPostProcessor.postProcessAfterInitialization}，
 * 它要调 {@code rpcServer.registerService} —— 所以 RpcServer 必须先 ready。
 *
 * <p>这里用 {@code initMethod = "start"} 让 RpcServer bean 创建时立即启动监听。
 * Spring 保证依赖的 bean 先初始化，所以 RpcServer bean 先于
 * SpringHelloServiceImpl 创建，顺序正确。
 *
 * <p>本 demo 用直连模式（不走 ZK，阶段 8 兼容），专注验证注解驱动。
 * ZK 模式只需把 RpcClient/RpcServer 换成带 ServiceDiscovery 的构造即可。
 */
@Configuration
@ComponentScan(basePackages = "com.myrpc.test.spring")
public class SpringRpcConfig {

    private static final int PORT = 18092;

    /**
     * RPC 服务端 —— 监听端口，等客户端连接。
     * initMethod = "start" 保证 bean 创建时就启动监听。
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public RpcServer rpcServer() {
        return new RpcServer(PORT);
    }

    /**
     * RPC 客户端 —— 直连本机端口（不走 ZK）。
     */
    @Bean(destroyMethod = "close")
    public RpcClient rpcClient() {
        return new RpcClient("127.0.0.1", PORT);
    }

    /**
     * Provider 侧 BeanPostProcessor —— 扫 @RpcService。
     * 依赖 rpcServer，Spring 保证 rpcServer bean 先创建。
     */
    @Bean
    public RpcServiceBeanPostProcessor rpcServiceBeanPostProcessor(RpcServer rpcServer) {
        return new RpcServiceBeanPostProcessor(rpcServer);
    }

    /**
     * Consumer 侧 BeanPostProcessor —— 扫 @RpcReference。
     * 依赖 rpcClient，Spring 保证 rpcClient bean 先创建。
     */
    @Bean
    public RpcReferenceBeanPostProcessor rpcReferenceBeanPostProcessor(RpcClient rpcClient) {
        return new RpcReferenceBeanPostProcessor(rpcClient);
    }
}
