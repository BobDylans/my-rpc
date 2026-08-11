package com.myrpc.core.server;

import com.myrpc.api.dto.RpcRequest;
import com.myrpc.api.dto.RpcResponse;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 请求处理器 —— 收到 RpcRequest，反射调用目标方法，返回 RpcResponse。
 *
 * <p>职责链（Pipeline 入站方向）：
 * <pre>
 * 字节流 → RpcMessageDecoder → RpcMessage → 本 Handler → 业务方法
 *                                          ↘ 写回 RpcResponse → RpcMessageEncoder → 字节流
 * </pre>
 *
 * <p>核心流程（{@link #channelRead0}）：
 * <ol>
 *   <li>从 RpcMessage 取 RpcRequest</li>
 *   <li>查 {@link ServiceRegistry} 拿目标实现</li>
 *   <li>反射调用目标方法（getMethod + invoke）</li>
 *   <li>包 RpcResponse（成功放 data，异常放 message）</li>
 *   <li>写回（Encoder 自动编码成字节）</li>
 * </ol>
 *
 * <h2>为什么继承 SimpleChannelInboundHandler&lt;RpcMessage&gt; 而不是 ChannelInboundHandlerAdapter？</h2>
 * <ol>
 *   <li><b>泛型参数决定入站消息类型</b>：自动把 Object 强转成 RpcMessage，
 *       不用自己 instanceof + 强转</li>
 *   <li><b>自动释放引用计数</b>：channelRead0 返回后，Netty 自动 release 入站消息
 *       （若消息是 ReferenceCounted）。用 ChannelInboundHandlerAdapter 就必须手动
 *       ReferenceCountUtil.release，忘了就内存泄漏</li>
 *   <li>只处理"接到的消息"，不关心链上其他事件，语义聚焦</li>
 * </ol>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/06-服务端-Netty与反射调用} §1.3
 */
public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(RpcRequestHandler.class);

    private final ServiceRegistry registry;

    public RpcRequestHandler(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        // 本阶段只处理请求消息；心跳等消息后续阶段再说
        if (msg.getMessageType() != MessageType.REQUEST.getCode()) {
            log.debug("忽略非请求消息: {}", MessageType.of(msg.getMessageType()));
            return;
        }

        RpcRequest request = (RpcRequest) msg.getData();
        log.debug("收到请求: {}#{}({})", request.getInterfaceName(), request.getMethodName(),
                java.util.Arrays.toString(request.getParameters()));

        RpcResponse response = invoke(request);

        // 写回：序列化类型沿用请求的，保证收发对称
        RpcMessage respMsg = new RpcMessage(MessageType.RESPONSE.getCode(),
                msg.getSerializerType(), response);
        ctx.writeAndFlush(respMsg);
    }

    /**
     * 反射调用目标方法。所有异常都塞进 RpcResponse.message ——
     * 消费者能感知调用失败，而不是被 Netty 吞掉或直接断开连接。
     */
    private RpcResponse invoke(RpcRequest request) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(request.getRequestId());
        try {
            // ① 查注册表
            Object target = registry.getService(request.getInterfaceName());
            if (target == null) {
                throw new IllegalStateException("服务未注册: " + request.getInterfaceName());
            }
            // ② 反射定位方法：getMethod(name, paramTypes) 需要精确参数类型（支持重载）
            Class<?> clazz = Class.forName(request.getInterfaceName());
            Method method = clazz.getMethod(request.getMethodName(), request.getParamTypes());
            // ③ 反射调用
            Object result = method.invoke(target, request.getParameters());
            response.setData(result);
        } catch (Throwable t) {
            // 目标方法抛的异常 / 服务未注册 / 方法找不到，统一走 message 返回
            // 注意：invoke 会把目标方法自身抛的异常包成 InvocationTargetException，
            // 必须解包才能把真实的业务异常传回消费者（否则只看到一层包装）
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException
                    && t.getCause() != null) ? t.getCause() : t;
            log.warn("RPC 调用失败: {}.{}", request.getInterfaceName(), request.getMethodName(), cause);
            response.setMessage(String.valueOf(cause));
        }
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 兜底：编解码异常、协议错误等链上异常走这里，记日志并断开连接
        log.error("连接处理异常", cause);
        ctx.close();
    }
}
