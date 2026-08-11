package com.myrpc.test;

import com.myrpc.api.HelloService;
import com.myrpc.core.client.RpcClient;
import com.myrpc.core.proxy.RpcClientProxy;
import com.myrpc.core.server.RpcServer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 07 客户端集成测试 —— 动态代理 + Future 异步。
 *
 * <p>验证：
 * <ul>
 *   <li>端到端：拿到 HelloService 代理，调 sayHi 像本地方法一样返回结果</li>
 *   <li>并发 10 请求：共享一条长连接，requestId 配对正确（每个线程拿到自己的结果）</li>
 *   <li>Object 方法：代理的 toString/equals/hashCode 走本地，不触发远程调用</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/07-客户端-动态代理与Future异步} §动手任务
 */
class RpcClientIntegrationTest {

    private static final int PORT = 18091;

    /** 启动服务端，返回 server 实例（finally 里 stop） */
    private RpcServer startServer() throws InterruptedException {
        RpcServer server = new RpcServer(PORT);
        server.registerService(HelloService.class, new HelloServiceImpl());
        server.start();
        return server;
    }

    /** 获取代理：RpcClient → RpcClientProxy → HelloService 代理 */
    private HelloService getProxy() {
        RpcClient client = new RpcClient("127.0.0.1", PORT);
        return new RpcClientProxy(client, HelloService.class).getProxy();
    }

    @Test
    void testRemoteCallLikeLocal() throws Exception {
        RpcServer server = startServer();
        try {
            HelloService service = getProxy();

            // 调用远程方法 —— 语法上完全像本地方法
            String result = service.sayHi("世界");
            assertEquals("Hi, 世界 (from RPC server)", result, "应拿到服务端反射调用的结果");
        } finally {
            server.stop();
        }
    }

    @Test
    void testConcurrentRequests() throws Exception {
        RpcServer server = startServer();
        try {
            HelloService service = getProxy(); // 共享同一个代理 = 共享同一条连接

            int threads = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            AtomicBoolean anyError = new AtomicBoolean(false);

            // 10 个线程同时调用，每个传不同的名字
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    startLatch.await(); // 同时起跑，制造并发
                    return service.sayHi("user-" + idx);
                }));
            }
            startLatch.countDown();

            // 断言：每个线程都拿到**自己的**结果（requestId 配对正确的证明）
            for (int i = 0; i < threads; i++) {
                try {
                    assertEquals("Hi, user-" + i + " (from RPC server)",
                            futures.get(i).get(10, java.util.concurrent.TimeUnit.SECONDS),
                            "线程 " + i + " 应拿到自己的响应（响应未乱序配对）");
                } catch (Exception e) {
                    anyError.set(true);
                    throw e;
                }
            }
            assertFalse(anyError.get());
            pool.shutdown();
        } finally {
            server.stop();
        }
    }

    @Test
    void testObjectMethodsAreLocal() throws Exception {
        RpcServer server = startServer();
        try {
            HelloService service = getProxy();

            // toString 走本地（不触发 RPC），能看到代理描述
            assertTrue(service.toString().contains("RpcClientProxy"));
            // equals 本地比较
            assertTrue(service.equals(service));
            // hashCode 本地
            assertTrue(service.hashCode() != 0);
        } finally {
            server.stop();
        }
    }
}
