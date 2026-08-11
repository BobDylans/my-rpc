package com.myrpc.core.codec;

import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.ProtocolConstants;
import com.myrpc.core.protocol.RpcMessage;
import com.myrpc.core.serialization.Serializer;
import com.myrpc.core.serialization.Serializers;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 协议解码器 —— 入站：把字节流还原成 {@link RpcMessage}。
 *
 * <p>继承 {@link ByteToMessageDecoder}，Pipeline 入站方向自动调用。
 * 数据流：网络 → 字节流 → 本解码器 → {@code RpcMessage} → 业务 Handler。
 *
 * <h2>处理粘包/半包的核心逻辑</h2>
 * <ol>
 *   <li>先检查可读字节数 ≥ 协议头长度，不够就 {@code return}</li>
 *   <li>读长度字段，得知整条消息多长</li>
 *   <li>再检查可读字节数 ≥ 整条消息长度，不够就 {@code return}</li>
 *   <li>够 → 读取完整消息，组装 {@code RpcMessage}，{@code out.add(msg)}</li>
 * </ol>
 *
 * <p>关键：{@link ByteToMessageDecoder} 会维护一个累积缓冲区，
 * 每次 {@code return} 后 Netty 会等更多数据到达再重新调用 {@code decode}。
 * 这就是处理半包（数据没到齐）的机制。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器} §1.4
 */
// 这里是将数据重新反序列回去
public class RpcMessageDecoder extends ByteToMessageDecoder {

    // 注意decoder的in和encoder的out的类型都是ByteBuf但是含义刚好相反
    // 这里是接收ByteBuf然后生成结构化的数据
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // ① 至少要能读到协议头，否则等更多数据
        // 只要调用in.readableBytes()就会将数据读入到缓冲区,先和协议头的长度(11)进行对比
        // 小于直接return就行
        if (in.readableBytes() < ProtocolConstants.HEADER_LENGTH) {
            return;
        }

        // ② 读协议头（注意：readXxx 会推进读指针，后面失败需 reset）
        // 先标记当前消息起点：半包时 reset 回到这里，而不是回退到整个缓冲区开头。
        // 否则多条消息粘包时，前一条已解出、后一条半包，reset 会回到 0 重新解码前一条 → 死循环。
        in.markReaderIndex();
        int magic = in.readInt();
        byte version = in.readByte();
        int fullLength = in.readInt();
        byte messageType = in.readByte();
        byte serializerType = in.readByte();

        // ③ 校验魔数 —— 拒绝非法连接/脏数据
        if (magic != ProtocolConstants.MAGIC) {
            throw new IllegalArgumentException("非法魔数: 0x" + Integer.toHexString(magic)
                    + "，期望: 0x" + Integer.toHexString(ProtocolConstants.MAGIC));
        }

        // ④ 计算数据体长度，检查是否够一整条消息
        // 可以读到协议头部分的整体长度字段,计算是否接收完全
        // 总长度减去header头的部分,如果和记载的body长度一致
        int bodyLength = fullLength - ProtocolConstants.HEADER_LENGTH;
        if (in.readableBytes() < bodyLength) {
            // 半包：数据体还没到齐，退回读指针等下次
            in.resetReaderIndex();
            return;
        }

        // ⑤ 完整消息，读取数据体字节
        byte[] body = new byte[bodyLength];
        in.readBytes(body);

        // ⑥ 按消息类型决定反序列化成什么
        Serializer serializer = Serializers.of(serializerType);
        Object data = null;
        if (bodyLength > 0) {
            // 心跳包没有数据体
            data = switch (MessageType.of(messageType)) {
                case REQUEST -> serializer.deserialize(body, com.myrpc.api.dto.RpcRequest.class);
                case RESPONSE -> serializer.deserialize(body, com.myrpc.api.dto.RpcResponse.class);
                case HEARTBEAT -> null;
            };
        }

        // ⑦ 组装消息，交给下游 Handler
        RpcMessage message = new RpcMessage();
        message.setMessageType(messageType);
        message.setSerializerType(serializerType);
        message.setData(data);

        out.add(message);
    }
}
