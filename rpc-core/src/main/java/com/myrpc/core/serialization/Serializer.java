package com.myrpc.core.serialization;

/**
 * 序列化器接口 —— 把 Java 对象 ↔ 字节流互转。
 *
 * <p>这是 RPC 框架的网络传输与业务对象之间的桥梁：
 * <ul>
 *   <li>编码时：{@code serialize(data)} → 字节流 → 写入网络</li>
 *   <li>解码时：从网络读字节流 → {@code deserialize(bytes, clazz)} → 业务对象</li>
 * </ul>
 *
 * <p>{@link #getCode()} 返回的 code 会写入协议头"序列化类型"字段，
 * 解码时据此选择对应的反序列化器。
 *
 * <p>本阶段先实现 JDK 版本，阶段 5 会完整实现 Kryo 版本并做性能对比。
 * 对应学习文档：{@link /后端知识/中间件/05-序列化层-JDK到Kryo}
 */
public interface Serializer {

    /**
     * 把对象序列化成字节数组。
     *
     * @param obj 要序列化的对象
     * @return 字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 把字节数组反序列化成对象。
     *
     * @param bytes 字节数组
     * @param clazz 目标类型（Kryo 等需要）
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);

    /**
     * 序列化器编号，写入协议头。
     * <ul>
     *   <li>0 = JDK</li>
     *   <li>1 = Kryo</li>
     * </ul>
     */
    int getCode();
}
