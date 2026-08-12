package com.myrpc.test;

import com.myrpc.api.HelloService;
import com.myrpc.core.client.RpcClient;
import com.myrpc.core.proxy.RpcClientProxy;
import com.myrpc.core.server.RpcServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 13 心跳与重连集成测试 —— 验证服务端重启后客户端能自动重连。
 *
 * <p>场景：
 * <ol>
 *   <li>启动服务端，调一次 RPC 验证正常</li>
 *   <li>关闭服务端（模拟服务挂了）</li>
 *   <li>调一次 RPC → 应该失败（连接断了）</li>
 *   <li>重启服务端</li>
 *   <li>调一次 RPC → 客户端自动重连成功，返回结果</li>
 * </ol>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/13-心跳保活与重连机制}
 */
class HeartbeatReconnectIntegrationTest {

    private static final int PORT = 18093;

    @Test
    void reconnectsAfterServerRestart() throws Exception {
        // ① 启动服务端
        RpcServer server = startServer();
        try {
            RpcClient client = new RpcClient("127.0.0.1", PORT);
            HelloService proxy = new RpcClientProxy(client, HelloService.class).getProxy();

            // ② 首次调用验证正常
            String result = proxy.sayHi("first");
            assertEquals("Hi, first (from RPC server)", result);

            // ③ 关闭服务端（模拟挂了）
            server.stop();
            Thread.sleep(500);  // 等服务端完全关闭

            // ④ 此时调用应该失败（连接断了）
            // RpcClient 的 getChannel 会检测 isActive() 失败，尝试重连
            // 但服务端还没起，重连也失败
            assertThrows(Exception.class, () -> proxy.sayHi("during-down"),
                    "服务端关闭期间调用应失败");

            // ⑤ 重启服务端
            server = startServer();

            // ⑥ 再调用 → 客户端自动重连成功
            //    注意：这里可能需要重试（getChannel 内置 3 次指数退避重试）
            String result2 = proxy.sayHi("after-restart");
            assertEquals("Hi, after-restart (from RPC server)", result2,
                    "服务端重启后客户端应自动重连成功");

            client.close();
        } finally {
            if (server != null) server.stop();
        }
    }

    private RpcServer startServer() throws InterruptedException {
        RpcServer server = new RpcServer(PORT);
        server.registerService(HelloService.class, new HelloServiceImpl());
        server.start();
        return server;
    }
}
