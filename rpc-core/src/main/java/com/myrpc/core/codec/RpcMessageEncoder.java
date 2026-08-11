package com.myrpc.core.codec;

import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.ProtocolConstants;
import com.myrpc.core.protocol.RpcMessage;
import com.myrpc.core.serialization.Serializer;
import com.myrpc.core.serialization.Serializers;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 协议编码器 —— 出站：把 {@link RpcMessage} 写成协议格式的字节流。
 *
 * <p>继承 {@link MessageToByteEncoder}，Pipeline 出站方向自动调用。
 * 数据流：业务对象 → {@code RpcMessage} → 本编码器 → 字节流 → 网络。
 *
 * <p>编码顺序严格按 {@link ProtocolConstants} 的协议格式：
 * <pre>
 * 魔数 → 版本 → 总长度 → 消息类型 → 序列化类型 → 数据体
 * </pre>
 *
 * <p>注意：长度字段写的是<b>整个消息的总长度</b>（头 + 数据体），
 * 而不是单纯数据体长度。解码时 {@link RpcMessageDecoder} 据此判断是否够一整条。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器} §1.3
 */
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {

    // 这个方法相当于将结构化的信息序列化并包裹到对应的信纸中
    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        // 1. 序列化数据体 —— 先序列化才能知道长度
        Serializer serializer = Serializers.of(msg.getSerializerType());
        byte[] body = serializer.serialize(msg.getData());

        // 2. 计算总长度 = 头部固定长度 + 数据体长度
        int fullLength = ProtocolConstants.HEADER_LENGTH + body.length;

        // 3. 按协议格式依次写入
        //    魔数：4 byte，标识本框架协议
        out.writeInt(ProtocolConstants.MAGIC);

        //    版本号：1 byte
        out.writeByte(ProtocolConstants.VERSION);

        //    总长度：4 byte（防粘包的核心字段）
        out.writeInt(fullLength);

        //    消息类型：1 byte
        out.writeByte(msg.getMessageType());

        //    序列化类型：1 byte
        out.writeByte(msg.getSerializerType());

        //    数据体：N byte
        out.writeBytes(body);
    }
}
