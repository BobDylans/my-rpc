package com.myrpc.core.client;

import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端心跳处理器 —— 写空闲时发心跳包，保持连接活性。
 *
 * <p>Pipeline 中放在 {@link io.netty.handler.timeout.IdleStateHandler} 之后：
 * <pre>
 *   IdleStateHandler(0, 15, 0) → RpcHeartbeatHandler → RpcResponseHandler
 * </pre>
 *
 * <h2>工作原理</h2>
 * <ol>
 *   <li>{@code IdleStateHandler} 检测到 15s 没写数据 → 触发 {@code WRITER_IDLE_STATE_EVENT}</li>
 *   <li>事件通过 {@code userEventTriggered} 传到本 Handler</li>
 *   <li>判断是写空闲 → 发一个空 {@code RpcMessage}(HEARTBEAT)</li>
 *   <li>服务端收到心跳包 → 解码出 HEARTBEAT 类型 → {@code RpcRequestHandler} 忽略（不回响应）</li>
 *   <li>但"有数据写出"刷新了 IdleStateHandler 的写时钟 → 15s 重新计时</li>
 * </ol>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/13-心跳保活与重连机制} §1.3
 */
public class RpcHeartbeatHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RpcHeartbeatHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            IdleState state = event.state();
            if (state == IdleState.WRITER_IDLE) {
                // 写空闲 → 发心跳包
                RpcMessage heartbeat = new RpcMessage(
                        MessageType.HEARTBEAT.getCode(), (byte) 1, null);
                ctx.writeAndFlush(heartbeat);
                log.debug("发送心跳 → {}", ctx.channel().remoteAddress());
            } else {
                // 读空闲也传给下游（RpcResponseHandler 据此触发重连）
                super.userEventTriggered(ctx, evt);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
