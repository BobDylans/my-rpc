package com.myrpc.test;

import com.myrpc.api.HelloService;
import com.myrpc.core.registry.CuratorUtils;
import com.myrpc.core.registry.ServiceRegistry;
import com.myrpc.core.registry.ZkServiceRegistry;
import com.myrpc.core.server.RpcServer;
import com.myrpc.test.HelloServiceImpl;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 09 集成测试 —— ZK 服务注册与发现（需要本地 Docker ZK）。
 *
 * <p>验证：
 * <ul>
 *   <li>Provider 启动后，ZK 上出现 /my-rpc/服务名/本机地址 节点</li>
 *   <li>lookup 能从 ZK 查到实例地址</li>
 *   <li>Provider 关闭后，临时节点消失（session 断开自动清理）</li>
 * </ul>
 *
 * <p>前置条件：本地 Docker 已启动 ZK（docker run -p 2181:2181 zookeeper:3.9）。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/09-Zookeeper集成与服务注册} §产出物
 */
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
            CuratorFramework client = CuratorUtils.getZkClient();
            String expectedPath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + HelloService.class.getName()
                    + "/" + InetAddress.getLocalHost().getHostAddress() + ":" + PORT;
            assertNotNull(client.checkExists().forPath(expectedPath),
                    "服务节点应已注册到 ZK: " + expectedPath);

            // ③ lookup 能查到实例
            ServiceRegistry registry = new ZkServiceRegistry();
            List<InetSocketAddress> addresses = registry.lookup(HelloService.class.getName());
            assertEquals(1, addresses.size(), "应发现 1 个实例");
            assertEquals(PORT, addresses.get(0).getPort());
        } finally {
            server.stop();
        }

        // ④ 优雅关闭（stop → clearRegistry）：临时节点应已被主动反注册
        CuratorFramework client = CuratorUtils.getZkClient();
        String path = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + HelloService.class.getName()
                + "/" + InetAddress.getLocalHost().getHostAddress() + ":" + PORT;
        assertNull(client.checkExists().forPath(path), "stop 后临时节点应被主动反注册");

        // ⑤ 补充验证：临时节点随 session 断开自动消失（模拟 Provider 进程崩溃来不及反注册）
        String tmpPath = CuratorUtils.buildRegisterPath("TmpCrashCheckService",
                new InetSocketAddress("127.0.0.1", 9999));
        CuratorUtils.createEphemeralNode(client, tmpPath);
        assertNotNull(client.checkExists().forPath(tmpPath), "临时节点应先存在");
        client.close(); // 关闭 session = 模拟进程退出
        // 已关闭的 client 不能再用，getZkClient 检测到旧客户端已停止会自动重建新连接
        CuratorFramework checkClient = CuratorUtils.getZkClient();
        for (int i = 0; i < 10; i++) { // 轮询等 ZK 清理
            if (checkClient.checkExists().forPath(tmpPath) == null) {
                break;
            }
            Thread.sleep(500);
        }
        assertNull(checkClient.checkExists().forPath(tmpPath),
                "session 断开后临时节点应自动消失");
        checkClient.close(); // 查询完清理，不污染后续测试
    }
}
