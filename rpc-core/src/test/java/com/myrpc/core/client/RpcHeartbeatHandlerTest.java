package com.myrpc.core.client;

import com.myrpc.api.HelloService;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import com.myrpc.core.server.RpcServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 13 心跳处理器单元测试 —— 不起真实网络，用 EmbeddedChannel 验证逻辑。
 *
 * <p>验证：
 * <ul>
 *   <li>写空闲事件 → 发出心跳包（MessageType=HEARTBEAT）</li>
 *   <li>读空闲事件 → 透传给下游（不处理）</li>
 * </ul>
 */
class RpcHeartbeatHandlerTest {

    @Test
    void writerIdleEventSendsHeartbeat() {
        // 注意：EmbeddedChannel 的出站方向，writeOutbound 会经过整个 pipeline
        EmbeddedChannel channel = new EmbeddedChannel(new RpcHeartbeatHandler());

        // 触发写空闲事件
        IdleStateEvent event = IdleStateEvent.WRITER_IDLE_STATE_EVENT;
        channel.pipeline().fireUserEventTriggered(event);

        // 应该有个出站的 RpcMessage（心跳）
        Object outbound = channel.readOutbound();
        assertNotNull(outbound, "写空闲应触发心跳包");
        assertInstanceOf(RpcMessage.class, outbound);
        assertEquals(MessageType.HEARTBEAT.getCode(), ((RpcMessage) outbound).getMessageType(),
                "心跳包的消息类型应为 HEARTBEAT");

        channel.finish();
    }

    @Test
    void readerIdleEventPassesThrough() {
        EmbeddedChannel channel = new EmbeddedChannel(new RpcHeartbeatHandler());

        // 读空闲事件应该被传给下游，不产生心跳包
        IdleStateEvent event = IdleStateEvent.READER_IDLE_STATE_EVENT;
        channel.pipeline().fireUserEventTriggered(event);

        // 不应有出站消息（读空闲不发心跳）
        assertNull(channel.readOutbound(), "读空闲不应发心跳包");

        channel.finish();
    }
}
