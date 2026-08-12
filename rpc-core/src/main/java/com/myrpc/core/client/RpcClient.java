package com.myrpc.core.client;

import com.myrpc.api.dto.RpcRequest;
import com.myrpc.core.codec.RpcMessageDecoder;
import com.myrpc.core.codec.RpcMessageEncoder;
import com.myrpc.core.loadbalancer.LoadBalancer;
import com.myrpc.core.loadbalancer.RandomLoadBalancer;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import com.myrpc.core.registry.ServiceDiscoveryCache;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

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

    // ───── 阶段 8：直连模式（保留，向后兼容）─────
    /** 直连地址（host/port 模式专用，null 表示走 ZK 发现） */
    private final String directHost;
    private final int directPort;

    // ───── 阶段 10：ZK 服务发现模式 ─────
    /** 服务发现缓存（null 表示直连模式） */
    private final ServiceDiscoveryCache discovery;
    private final LoadBalancer loadBalancer;

    private final EventLoopGroup group;

    /** 共享长连接（volatile：多线程可见；双检锁保护创建） */
    // 使用volatile修饰保证可见性
    private volatile Channel channel;
    /** 当前连接的目标地址（连接断开重连时据此判断是否换地址） */
    private volatile InetSocketAddress connectedAddr;

    /**
     * 阶段 8 构造：直连模式（地址写死，不用 ZK）。
     * 保留它是为了兼容阶段 8 的直连集成测试。
     */
    public RpcClient(String host, int port) {
        this.directHost = host;
        this.directPort = port;
        this.discovery = null;
        this.loadBalancer = null;
        this.group = new NioEventLoopGroup();
    }

    /**
     * 阶段 10 构造：ZK 服务发现模式。
     * 地址不再写死，每次发送时从 {@link ServiceDiscoveryCache} 查 + 负载均衡选。
     */
    public RpcClient(ServiceDiscoveryCache discovery, LoadBalancer loadBalancer) {
        this.directHost = null;
        this.directPort = 0;
        this.discovery = discovery;
        this.loadBalancer = loadBalancer;
        this.group = new NioEventLoopGroup();
    }

    /** ZK 模式默认负载均衡（随机）的便捷构造 */
    public RpcClient(ServiceDiscoveryCache discovery) {
        this(discovery, new RandomLoadBalancer());
    }

    /**
     * 发送请求（异步）：写入共享连接，不等待响应。
     * 响应由 {@link RpcResponseHandler} 配对到 UnprocessedRequests。
     *
     * <p>阶段 10 改造：如果走 ZK 模式，先查地址列表再连。
     * 如果当前连接还活着且指向同一服务，复用；否则重新选择并建连。
     */
    public void send(RpcRequest request) throws InterruptedException {
        InetSocketAddress target = resolveTarget(request.getInterfaceName());
        if (target == null) {
            throw new RuntimeException("无可用服务实例: " + request.getInterfaceName());
        }
        Channel ch = getChannel(target);
        // 序列化类型固定 Kryo（1），服务端会沿用请求里的 serializerType 回包
        RpcMessage msg = new RpcMessage(MessageType.REQUEST.getCode(), (byte) 1, request);
        ch.writeAndFlush(msg).sync(); // sync：确保写入成功（或抛异常），不等待响应
    }

    /**
     * 解析目标地址：直连模式直接用配置地址，ZK 模式查缓存 + 负载均衡。
     */
    private InetSocketAddress resolveTarget(String serviceName) {
        if (discovery == null) {
            return new InetSocketAddress(directHost, directPort);
        }
        List<InetSocketAddress> addrs = discovery.get(serviceName);
        if (addrs.isEmpty()) {
            return null;
        }
        return loadBalancer.select(addrs, serviceName);
    }

    /**
     * 获取共享连接：懒连接 + 断线自动重连。
     * 双检锁：多个线程同时发现连接失效时，只允许一个重建，避免连接风暴。
     *
     * <p>阶段 10 改造：连接以 target 地址为准。如果 target 变了（负载均衡选了新地址），
     * 旧连接必须重建。
     */
    private Channel getChannel(InetSocketAddress target) throws InterruptedException {
        Channel cur = channel;
        // 连接还活着且目标是同一个 → 复用
        if (cur != null && cur.isActive() && target.equals(connectedAddr)) {
            return cur;
        }
        synchronized (this) {
            cur = channel;
            if (cur != null && cur.isActive() && target.equals(connectedAddr)) {
                return cur;
            }
            log.info("连接 {}:{} ...", target.getHostString(), target.getPort());
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
            channel = bootstrap.connect(target.getHostString(), target.getPort()).sync().channel();
            connectedAddr = target;
            return channel;
        }
    }

    /** 关闭客户端，释放线程组 */
    public void close() {
        group.shutdownGracefully();
    }
}
