package com.myrpc.core.codec;

import com.myrpc.api.dto.RpcRequest;
import com.myrpc.api.dto.RpcResponse;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.ProtocolConstants;
import com.myrpc.core.protocol.RpcMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编解码器单元测试 —— 用 {@link EmbeddedChannel} 模拟 Pipeline，
 * 验证 {@code 编码 → 解码} round-trip 数据一致。
 *
 * <p>测试场景：
 * <ul>
 *   <li>正常 round-trip：请求/响应各一遍</li>
 *   <li>半包：只给半个消息，解码应返回 null（不抛异常）</li>
 *   <li>粘包模拟：两条消息拼一起，应解出两条</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器} §动手任务
 */
class RpcMessageCodecTest {

    @Test
    void testRequestRoundTrip() {
        // 编码器在出站方向，解码器在入站方向
        EmbeddedChannel channel = new EmbeddedChannel(
                new RpcMessageEncoder(), new RpcMessageDecoder());

        // 构造一个请求消息
        RpcRequest request = new RpcRequest();
        request.setRequestId(1L);
        request.setInterfaceName("com.myrpc.api.HelloService");
        request.setMethodName("sayHi");
        request.setParamTypes(new Class[]{String.class});
        request.setParameters(new Object[]{"world"});

        RpcMessage msg = new RpcMessage();
        msg.setMessageType(MessageType.REQUEST.getCode());
        msg.setSerializerType((byte) 0); // JDK
        msg.setData(request);

        // 写出站 → 触发 Encoder → 读出编码后的 ByteBuf
        assertTrue(channel.writeOutbound(msg));
        ByteBuf encoded = channel.readOutbound();
        assertNotNull(encoded, "编码后应得到 ByteBuf");

        // 写入站 → 触发 Decoder → 读出解码后的 RpcMessage
        assertTrue(channel.writeInbound(encoded));
        RpcMessage decoded = channel.readInbound();
        assertNotNull(decoded, "解码后应得到 RpcMessage");

        // 验证头部
        assertEquals(MessageType.REQUEST.getCode(), decoded.getMessageType());
        assertEquals(0, decoded.getSerializerType());

        // 验证数据体
        assertInstanceOf(RpcRequest.class, decoded.getData());
        RpcRequest decodedReq = (RpcRequest) decoded.getData();
        assertEquals(1L, decodedReq.getRequestId());
        assertEquals("com.myrpc.api.HelloService", decodedReq.getInterfaceName());
        assertEquals("sayHi", decodedReq.getMethodName());
        assertArrayEquals(new Class[]{String.class}, decodedReq.getParamTypes());
        assertArrayEquals(new Object[]{"world"}, decodedReq.getParameters());

        channel.finish();
    }

    @Test
    void testResponseRoundTrip() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new RpcMessageEncoder(), new RpcMessageDecoder());

        RpcResponse response = new RpcResponse();
        response.setRequestId(42L);
        response.setData("hello, world");
        response.setMessage(null);

        RpcMessage msg = new RpcMessage();
        msg.setMessageType(MessageType.RESPONSE.getCode());
        msg.setSerializerType((byte) 0);
        msg.setData(response);

        channel.writeOutbound(msg);
        ByteBuf encoded = channel.readOutbound();
        channel.writeInbound(encoded);
        RpcMessage decoded = channel.readInbound();

        assertInstanceOf(RpcResponse.class, decoded.getData());
        RpcResponse decodedResp = (RpcResponse) decoded.getData();
        assertEquals(42L, decodedResp.getRequestId());
        assertEquals("hello, world", decodedResp.getData());
        assertNull(decodedResp.getMessage());

        channel.finish();
    }

    @Test
    void testHalfPacket() {
        // 只 Decoder，模拟半包场景
        EmbeddedChannel channel = new EmbeddedChannel(new RpcMessageDecoder());

        // 先完整编码一条消息
        EmbeddedChannel encoder = new EmbeddedChannel(new RpcMessageEncoder());
        RpcRequest request = buildSampleRequest();
        RpcMessage msg = new RpcMessage(
                MessageType.REQUEST.getCode(), (byte) 0, request);
        encoder.writeOutbound(msg);
        ByteBuf full = encoder.readOutbound();

        // 只截取协议头 + 一点点数据体（不够整条）
        int headerLen = ProtocolConstants.HEADER_LENGTH;
        ByteBuf half = full.retainedSlice(0, headerLen + 2); // 头 + 2 字节

        // 解码应返回 false（没产出消息），且不抛异常
        boolean produced = channel.writeInbound(half);
        assertFalse(produced, "半包不应产出消息");
        assertNull(channel.readInbound(), "半包时 readInbound 应为 null");

        channel.finish();
        encoder.finish();
    }

    @Test
    void testMagicNumberMismatch() {
        EmbeddedChannel channel = new EmbeddedChannel(new RpcMessageDecoder());

        // 构造一个魔数错误的 ByteBuf
        ByteBuf bad = Unpooled.buffer();
        bad.writeInt(0xDEADBEEF); // 错误魔数
        bad.writeByte(1);         // version
        bad.writeInt(ProtocolConstants.HEADER_LENGTH); // 长度
        bad.writeByte(MessageType.REQUEST.getCode());
        bad.writeByte(0);

        // ByteToMessageDecoder 会把异常包成 DecoderException
        assertThrows(DecoderException.class, () -> channel.writeInbound(bad));
        channel.finish();
    }

    @Test
    void testStickyPackets() {
        // 模拟粘包：两条消息的字节拼在一起，应解出两条
        EmbeddedChannel channel = new EmbeddedChannel(new RpcMessageDecoder());

        EmbeddedChannel encoder = new EmbeddedChannel(new RpcMessageEncoder());

        RpcMessage msg1 = new RpcMessage(
                MessageType.REQUEST.getCode(), (byte) 0, buildSampleRequest());

        RpcResponse resp = new RpcResponse();
        resp.setRequestId(2L);
        resp.setData("ok");
        RpcMessage msg2 = new RpcMessage(
                MessageType.RESPONSE.getCode(), (byte) 0, resp);

        encoder.writeOutbound(msg1);
        encoder.writeOutbound(msg2);
        ByteBuf buf1 = encoder.readOutbound();
        ByteBuf buf2 = encoder.readOutbound();

        // 合并两条
        ByteBuf combined = Unpooled.wrappedBuffer(buf1, buf2);

        // 写入站，应解出两条
        channel.writeInbound(combined);
        RpcMessage d1 = channel.readInbound();
        RpcMessage d2 = channel.readInbound();

        assertNotNull(d1);
        assertNotNull(d2);
        assertInstanceOf(RpcRequest.class, d1.getData());
        assertInstanceOf(RpcResponse.class, d2.getData());

        channel.finish();
        encoder.finish();
    }

    // ---- helpers ----

    private RpcRequest buildSampleRequest() {
        RpcRequest r = new RpcRequest();
        r.setRequestId(1L);
        r.setInterfaceName("com.myrpc.api.HelloService");
        r.setMethodName("sayHi");
        r.setParamTypes(new Class[]{String.class});
        r.setParameters(new Object[]{"world"});
        return r;
    }
}
