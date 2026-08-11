package com.myrpc.core.protocol;

/**
 * 协议常量 —— 定义二进制协议格式中各字段的长度与固定值。
 *
 * <p>协议格式（共 12 字节头 + N 字节数据体）：
 * <pre>
 * +--------+--------+--------+----------+-----------+----------+
 * | 魔数   | 版本号 | 长度   | 消息类型 | 序列化类型| 数据体   |
 * | 4 byte | 1 byte | 4 byte | 1 byte   | 1 byte    | N byte   |
 * +--------+--------+--------+----------+-----------+----------+
 * </pre>
 *
 * <ul>
 *   <li>魔数 (magic) —— 4 byte，标识本框架协议，拒绝非法连接</li>
 *   <li>版本号 —— 1 byte，预留协议升级</li>
 *   <li>长度 (length) —— 4 byte，数据体字节数，<b>防粘包核心</b></li>
 *   <li>消息类型 —— 1 byte，见 {@link MessageType}</li>
 *   <li>序列化类型 —— 1 byte，0=JDK, 1=Kryo（阶段 5 实现）</li>
 *   <li>数据体 —— N byte，序列化后的对象字节</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器} §1.2
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    /** 协议魔数：固定 0x4D595250 = "MYRP" 的 ASCII */
    public static final int MAGIC = 0x4D595250;

    /** 当前协议版本号 */
    public static final byte VERSION = 1;

    /** 协议头总长度（魔数4 + 版本1 + 长度4 + 消息类型1 + 序列化类型1 = 11 byte） */
    public static final int HEADER_LENGTH = 4 + 1 + 4 + 1 + 1;

    // 字段长度（解码时按这些偏移读取）
    public static final int MAGIC_LENGTH = 4;
    public static final int VERSION_LENGTH = 1;
    public static final int FULL_LENGTH_LENGTH = 4;
    public static final int MESSAGE_TYPE_LENGTH = 1;
    public static final int SERIALIZER_TYPE_LENGTH = 1;

    // 各字段在协议头中的偏移量
    public static final int MAGIC_OFFSET = 0;
    public static final int VERSION_OFFSET = MAGIC_OFFSET + MAGIC_LENGTH;            // 4
    public static final int FULL_LENGTH_OFFSET = VERSION_OFFSET + VERSION_LENGTH;     // 5
    public static final int MESSAGE_TYPE_OFFSET = FULL_LENGTH_OFFSET + FULL_LENGTH_LENGTH; // 9
    public static final int SERIALIZER_TYPE_OFFSET = MESSAGE_TYPE_OFFSET + MESSAGE_TYPE_LENGTH; // 10
}
