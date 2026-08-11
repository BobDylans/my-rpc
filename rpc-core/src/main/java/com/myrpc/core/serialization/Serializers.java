package com.myrpc.core.serialization;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器注册表 —— 按协议头 code 查找对应的实现。
 *
 * <p>协议头有"序列化类型"字段，编码时记下用了哪个序列化器，
 * 解码时按这个 code 找回同一个序列化器反序列化，实现协议可切换。
 *
 * <pre>
 *   编码：serializer.getCode() 写入协议头 → serialize()
 *   解码：读协议头 code → Serializers.of(code) → deserialize()
 * </pre>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/05-序列化层-JDK到Kryo} §1.4
 */
public final class Serializers {

    private static final Map<Integer, Serializer> REGISTRY = new ConcurrentHashMap<>();

    static {
        // 注册两种序列化器，按 code 查找
        // code=0 → JDK，code=1 → Kryo
        register(new JdkSerializer());
        register(new KryoSerializer());
    }

    private Serializers() {}

    /** 注册一个序列化器，key 是它的 {@link Serializer#getCode()} */
    public static void register(Serializer serializer) {
        REGISTRY.put(serializer.getCode(), serializer);
    }

    /** 按 code 查找序列化器，找不到抛异常 */
    public static Serializer of(int code) {
        Serializer s = REGISTRY.get(code);
        if (s == null) {
            throw new IllegalArgumentException("未注册的序列化器 code: " + code);
        }
        return s;
    }

    /** 默认序列化器（JDK） */
    public static Serializer getDefault() {
        return REGISTRY.get(0);
    }
}
