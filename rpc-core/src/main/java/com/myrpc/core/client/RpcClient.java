package com.myrpc.core.client;

import com.myrpc.api.dto.RpcRequest;
import com.myrpc.core.codec.RpcMessageDecoder;
import com.myrpc.core.codec.RpcMessageEncoder;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC 客户端 —— 封装 Netty Bootstrap：连接服务端、发送请求。
 * 客户端,和服务端对应
 *
 * <p>职责：
 * <ul>
 *   <li>建立到服务端的连接（懒连接 + 断线重连）</li>
 *   <li>发送 RpcRequest（共享一条长连接）</li>
 *   <li>关闭资源</li>
 * </ul>
 *
 * <h2>为什么共享一条长连接？</h2>
 * 每次调用都建连/断连开销巨大（TCP 三次握手 + 四次挥手）。
 * 生产环境客户端复用一个 Channel 长连接；Netty 保证
 * 同一 Channel 的 writeAndFlush 是线程安全的，所以多个线程
 * 可以并发往里写请求 —— 这也是 requestId 配对机制的前提：
 * 多个请求共享一条连接，靠 id 区分响应。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/07-客户端-动态代理与Future异步} §1.5
 */
public class RpcClient {

    private static final Logger log = LoggerFactory.getLogger(RpcClient.class);

    private final String host;
    private final int port;
    private final EventLoopGroup group;

    /** 共享长连接（volatile：多线程可见；双检锁保护创建） */
    // 使用volatile修饰保证可见性
    private volatile Channel channel;

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.group = new NioEventLoopGroup();
    }

    /**
     * 发送请求（异步）：写入共享连接，不等待响应。
     * 响应由 {@link RpcResponseHandler} 配对到 UnprocessedRequests。
     */
    public void send(RpcRequest request) throws InterruptedException {
        Channel ch = getChannel();
        // 序列化类型固定 Kryo（1），服务端会沿用请求里的 serializerType 回包
        RpcMessage msg = new RpcMessage(MessageType.REQUEST.getCode(), (byte) 1, request);
        ch.writeAndFlush(msg).sync(); // sync：确保写入成功（或抛异常），不等待响应
    }

    /**
     * 获取共享连接：懒连接 + 断线自动重连。
     * 双检锁：多个线程同时发现连接失效时，只允许一个重建，避免连接风暴。
     * 注意这里使用的懒加载单例模式,保证全局单例,避免每次建立tcp连接的消耗
     * 这里和server不一样,server相当于接客,开启后等待连接,client是启动后保持一个连接
     */
    private Channel getChannel() throws InterruptedException {
        Channel cur = channel;
        if (cur != null && cur.isActive()) {
            return cur;
        }
        // 对当前类上锁,尝试创建channel
        synchronized (this) {
            cur = channel;
            // 拿到锁之后还是要看看是否创建成功,和懒汉式的单例模式类似
            if (cur != null && cur.isActive()) {
                return cur;
            }
            log.info("连接 {}:{} ...", host, port);
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new RpcMessageDecoder()) // 入站：字节 → RpcMessage
                                    .addLast(new RpcMessageEncoder()) // 出站：RpcMessage → 字节
                                    .addLast(new RpcResponseHandler()); // 配对响应
                        }
                    });
            channel = bootstrap.connect(host, port).sync().channel();
            return channel;
        }
    }

    /** 关闭客户端，释放线程组 */
    public void close() {
        group.shutdownGracefully();
    }
}
