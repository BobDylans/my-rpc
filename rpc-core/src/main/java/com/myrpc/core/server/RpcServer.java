package com.myrpc.core.server;

import com.myrpc.core.codec.RpcMessageDecoder;
import com.myrpc.core.codec.RpcMessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC 服务端 —— 封装 Netty ServerBootstrap，监听端口接收请求并反射调用。
 * 这个只是服务端的,实际上还需要有客户端
 * 这个类的作用就是接受来自客户端的连接,并且解析,并且调用
 *
 * <h2>Reactor 主从模型（为什么两个 EventLoopGroup）</h2>
 * <ul>
 *   <li><b>bossGroup</b>：只负责 accept 新连接（一个线程足够，NioEventLoopGroup(1)）</li>
 *   <li><b>workerGroup</b>：负责已建立连接的 read/write（IO 密集型，默认 2×CPU 核数线程）</li>
 * </ul>
 * boss 和 worker 分开，accept 慢不会拖累已有连接的读写；合并成一个组也可以工作，
 * 但接受连接和读写同一批线程，高并发下 accept 会挤占 IO 线程。
 *
 * <h2>Pipeline 顺序（关键）</h2>
 * <pre>
 * 入站（字节流 → 对象）:  RpcMessageDecoder → RpcRequestHandler
 * 出站（对象 → 字节流）:  RpcRequestHandler → RpcMessageEncoder
 * </pre>
 * Decoder 和 Encoder 在同一个 pipeline，但方向相反：
 * Decoder 处理入站（ByteToMessageDecoder），Encoder 处理出站（MessageToByteEncoder）。
 * Handler 收到的是解码后的 RpcMessage，写回 RpcMessage 后由 Encoder 编码成字节。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/06-服务端-Netty与反射调用} §1.1
 */
public class RpcServer {

    private static final Logger log = LoggerFactory.getLogger(RpcServer.class);

    private final int port;
    private final ServiceRegistry registry = new ServiceRegistry();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public RpcServer(int port) {
        this.port = port;
    }

    /**
     * 注册服务实现（本阶段手动注册）。
     */
    public void registerService(Class<?> iface, Object impl) {
        registry.registerService(iface, impl);
        log.info("注册服务: {} -> {}", iface.getName(), impl.getClass().getName());
    }

    /**
     * 启动服务端。bind 成功后返回（不阻塞等待连接关闭），
     * 调用方后续可用 {@link #stop()} 优雅关闭。
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);            // accept 线程，1 个足够
        workerGroup = new NioEventLoopGroup();           // IO 线程，默认 2×CPU
        // ServerBootstrap 这个类相当于包工头,将各个组建集合在一起
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                // 这个对应的实际上就是处理的流程
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // 这里传入的encoder和decoder是我们自己实现的
                                // decoder解码过程中就涉及相关的粘包和半包的处理
                                // 入站：字节流 → RpcMessage（含粘包/半包处理）
                                .addLast(new RpcMessageDecoder())
                                // 出站：RpcMessage → 字节流
                                .addLast(new RpcMessageEncoder())
                                // 业务：反射调用
                                .addLast(new RpcRequestHandler(registry));
                    }
                })
                // 服务端 socket 选项：等待队列长度
                .option(ChannelOption.SO_BACKLOG, 128)
                // 连接 socket 选项：TCP keepalive
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        // 这里的sync才是真正启动
        ChannelFuture future = bootstrap.bind(port).sync();
        log.info("Server started on port {}", port);
    }

    /**
     * 优雅关闭：停止接收新连接 + 释放已连接资源。
     * shutdownGracefully 会等待在途请求处理完再退出，不会粗暴断开。
     */
    public void stop() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}
