package com.myrpc.test;

import com.myrpc.api.HelloService;
import com.myrpc.core.registry.CuratorUtils;
import com.myrpc.core.registry.ServiceRegistry;
import com.myrpc.core.registry.ZkServiceRegistry;
import com.myrpc.core.server.RpcServer;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 09 集成测试 —— 验证 ZK 注册与发现。
 *
 * <p>这个测试需要本地启动 Zookeeper：
 * <pre>
 *   docker run -d -p 2181:2181 zookeeper:3.9
 * </pre>
 *
 * <p>不起 ZK 时会自动跳过（需设置环境变量 {@code ZK_ENABLED=true} 才运行）。
 * 这样 {@code mvn test} 不会因为缺 ZK 而全红。
 *
 * <p>要跑这个测试时：
 * <pre>
 *   ZK_ENABLED=true mvn test -Dtest=ZkServiceRegistryIntegrationTest
 * </pre>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/09-Zookeeper集成与服务注册} §产出物
 */
@EnabledIfEnvironmentVariable(named = "ZK_ENABLED", matches = "true")
class ZkServiceRegistryIntegrationTest {

    private static final int PORT = 18092;

    @Test
    void testRegisterAndLookup() throws Exception {
        // ① 启动带 ZK 的服务端，自动注册
        RpcServer server = new RpcServer(PORT, new ZkServiceRegistry());
        server.registerService(HelloService.class, new HelloServiceImpl());
        server.start();

        try {
            // ② 验证 ZK 上有节点：/my-rpc/com.myrpc.api.HelloService/本机IP:18092
            CuratorFramework zkClient = CuratorUtils.getZkClient();
            String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH
                    + "/" + HelloService.class.getName();

            // 查 ZK 子节点
            List<String> children = zkClient.getChildren().forPath(servicePath);
            assertFalse(children.isEmpty(), "ZK 上应有服务实例节点");

            // 解析出地址
            InetSocketAddress registeredAddr = null;
            for (String child : children) {
                int idx = child.lastIndexOf(':');
                String host = child.substring(0, idx);
                int port = Integer.parseInt(child.substring(idx + 1));
                registeredAddr = new InetSocketAddress(host, port);
                if (port == PORT) break;
            }
            assertNotNull(registeredAddr, "应找到本端口注册的实例");
            assertEquals(PORT, registeredAddr.getPort(), "端口应匹配");
            log("ZK 注册验证通过: " + registeredAddr);

            // ③ lookup 验证
            List<InetSocketAddress> found = ((ServiceRegistry) new ZkServiceRegistry())
                    .lookup(HelloService.class.getName());
            // 注意：ZkServiceRegistry 实现了 ServiceRegistry.lookup
            assertFalse(found.isEmpty(), "lookup 应返回地址列表");

        } finally {
            server.stop();
        }
    }

    private void log(String msg) {
        System.out.println("[ZK-TEST] " + msg);
    }
}
