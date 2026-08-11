/**
 * 序列化层：把 Java 对象转成可传输的字节流。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@code Serializer} —— 序列化器接口（{@code serialize}/{@code deserialize}）</li>
 *   <li>{@code JdkSerializer} —— 基于原生 {@code ObjectOutputStream}，学习起点</li>
 *   <li>{@code KryoSerializer} —— 基于 Kryo + ThreadLocal，高性能版本</li>
 *   <li>{@code Serializers} —— 注册表，按协议头 code 查找实现</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/05-序列化层-JDK到Kryo}
 */
package com.myrpc.core.serialization;
