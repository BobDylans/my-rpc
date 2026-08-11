package com.myrpc.core.protocol;

/**
 * 协议消息类型 —— 对应协议头中的"消息类型"字段（1 byte）。
 *
 * <p>用于在 Pipeline 中区分收到的字节流该反序列化成什么：
 * <ul>
 *   <li>{@link #REQUEST} —— 消费者发的请求，数据体是 {@code RpcRequest}</li>
 *   <li>{@link #RESPONSE} —— 服务端回的响应，数据体是 {@code RpcResponse}</li>
 *   <li>{@link #HEARTBEAT} —— 心跳包，数据体为空（阶段 13 引入）</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器} §1.2
 */
public enum MessageType {

    /** 请求消息（Consumer → Provider） */
    REQUEST((byte) 0),

    /** 响应消息（Provider → Consumer） */
    RESPONSE((byte) 1),

    /** 心跳消息（阶段 13 引入，双向保活） */
    HEARTBEAT((byte) 2);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    /** 协议头中存的数值 */
    public byte getCode() {
        return code;
    }

    /** 从协议头读出的 byte 反查枚举 */
    public static MessageType of(byte code) {
        for (MessageType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知消息类型 code: " + code);
    }
}
