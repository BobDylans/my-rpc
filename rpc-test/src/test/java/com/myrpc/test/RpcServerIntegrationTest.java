package com.myrpc.test;

import com.myrpc.api.HelloService;
import com.myrpc.api.dto.RpcRequest;
import com.myrpc.api.dto.RpcResponse;
import com.myrpc.core.codec.RpcMessageDecoder;
import com.myrpc.core.codec.RpcMessageEncoder;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import com.myrpc.core.server.RpcServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 06 端到端集成测试 —— 真实端口 + 裸 Netty 客户端。
 *
 * <p>验证完整链路：
 * <pre>
 * 客户端构造 RpcRequest → 编码 → TCP → 服务端解码 → 反射调用 HelloServiceImpl
 *   → 包 RpcResponse → 编码 → TCP → 客户端解码 → 拿到结果
 * </pre>
 *
 * <p>本阶段没有正式客户端（阶段 07 才做动态代理），
 * 这里用"裸 Netty 客户端"模拟消费者，验证服务端能正确处理请求并返回响应。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/06-服务端-Netty与反射调用} §动手任务
 */
class RpcServerIntegrationTest {

    /** 测试端口：避开常用端口，测试进程内固定使用 */
    private static final int PORT = 18090;

    /**
     * 测试客户端 handler：收到响应后 complete Future，测试线程阻塞等待结果。
     * 这就是阶段 07 正式客户端的雏形（requestId → Future 匹配）。
     */
    private static class TestClientHandler extends SimpleChannelInboundHandler<RpcMessage> {
        private final CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, RpcMessage msg) {
            if (msg.getMessageType() == MessageType.RESPONSE.getCode()) {
                future.complete((RpcResponse) msg.getData());
            }
        }

        /** 阻塞等待响应（最多 5 秒），超时抛异常让测试失败 */
        RpcResponse await() throws Exception {
            return future.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testEndToEndCall() throws Exception {
        // ① 启动服务端，注册服务
        RpcServer server = new RpcServer(PORT);
        server.registerService(HelloService.class, new HelloServiceImpl());
        server.start();

        try {
            // ② 裸 Netty 客户端
            EventLoopGroup group = new NioEventLoopGroup(1);
            TestClientHandler clientHandler = new TestClientHandler();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new RpcMessageDecoder()) // 入站：字节 → RpcMessage
                                    .addLast(new RpcMessageEncoder()) // 出站：RpcMessage → 字节
                                    .addLast(clientHandler);
                        }
                    });

            Channel channel = bootstrap.connect("127.0.0.1", PORT).sync().channel();

            // ③ 构造请求并发出去（模拟消费者调用 sayHi）
            RpcRequest req = new RpcRequest();
            req.setRequestId(1L);
            req.setInterfaceName(HelloService.class.getName());
            req.setMethodName("sayHi");
            req.setParamTypes(new Class<?>[]{String.class});
            req.setParameters(new Object[]{"世界"});
            // 序列化类型用 Kryo（1）
            RpcMessage msg = new RpcMessage(MessageType.REQUEST.getCode(), (byte) 1, req);
            channel.writeAndFlush(msg).sync();

            // ④ 等响应并断言
            RpcResponse resp = clientHandler.await();
            assertEquals(1L, resp.getRequestId(), "响应 requestId 应与请求一致");
            assertNull(resp.getMessage(), "正常调用不应有异常");
            assertEquals("Hi, 世界 (from RPC server)", resp.getData(), "反射调用结果应正确");

            channel.close().sync();
            group.shutdownGracefully();
        } finally {
            server.stop();
        }
    }
}
