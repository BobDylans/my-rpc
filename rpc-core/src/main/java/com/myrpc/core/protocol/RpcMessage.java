package com.myrpc.core.protocol;

/**
 * RPC 协议消息包装类 —— 协议头 + 数据体。
 *
 * <p>这是编解码器直接处理的对象。编码时把它拆成字节流写出去，
 * 解码时把字节流组装回它。真正的业务对象（{@code RpcRequest}/{@code RpcResponse}）
 * 作为 {@link #data} 字段存在，需要序列化/反序列化。
 *
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │              RpcMessage                  │
 * │ ┌─────────────┬───────────────────────┐ │
 * │ │   头部信息    │      数据体 (data)     │ │
 * │ │ messageType │  序列化后的业务对象字节  │ │
 * │ │ serializer  │                        │ │
 * │ └─────────────┴───────────────────────┘ │
 * └─────────────────────────────────────────┘
 * </pre>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器} §1.3
 */
// RpcMessage实际上是外层的信纸
public class RpcMessage {

    /** 消息类型：请求 / 响应 / 心跳 */
    private byte messageType;

    /** 序列化器类型 code（对应 Serializer#getCode） */
    // 不同的序列化器最终反序列调用的也不一样
    private byte serializerType;

    /** 数据体：业务对象（RpcRequest / RpcResponse）或心跳内容 */
    // 相当于无论是消费者还是生产者都会将data存放到这个RpcMessage中
    private Object data;

    public RpcMessage() {}

    public RpcMessage(byte messageType, byte serializerType, Object data) {
        this.messageType = messageType;
        this.serializerType = serializerType;
        this.data = data;
    }

    public byte getMessageType() { return messageType; }
    public void setMessageType(byte messageType) { this.messageType = messageType; }

    public byte getSerializerType() { return serializerType; }
    public void setSerializerType(byte serializerType) { this.serializerType = serializerType; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    @Override
    public String toString() {
        return "RpcMessage{" +
                "messageType=" + MessageType.of(messageType) +
                ", serializerType=" + serializerType +
                ", data=" + data +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RpcMessage that)) return false;
        return messageType == that.messageType
                && serializerType == that.serializerType
                && java.util.Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(messageType, serializerType, data);
    }
}
