package com.myrpc.core.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Kryo 序列化实现 —— 高性能二进制序列化。
 *
 * <p>对比 {@link JdkSerializer} 的优势：
 * <ul>
 *   <li>体积小 —— 不带冗余类信息</li>
 *   <li>性能高 —— 直接操作字节，不走反射</li>
 *   <li>不需要实现 {@link java.io.Serializable}</li>
 * </ul>
 *
 * <h2>线程安全说明</h2>
 * <p><b>Kryo 实例本身不是线程安全的</b>。如果多线程共用同一个 Kryo 实例，
 * 会抛 {@code KryoException} 或数据错乱。解决方法是用 {@link ThreadLocal}
 * 给每个线程一个独立的 Kryo 实例：
 *
 * <pre>
 *   线程A → 自己的 Kryo 实例 → 序列化
 *   线程B → 自己的 Kryo 实例 → 序列化
 *   互不干扰
 * </pre>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/05-序列化层-JDK到Kryo} §1.3
 */
public class KryoSerializer implements Serializer {

    /**
     * 每个线程独占一个 Kryo 实例。
     *
     * <p>为什么用 ThreadLocal 而不是 synchronized？
     * <ul>
     *   <li>synchronized 会让所有线程串行执行，高并发下成瓶颈</li>
     *   <li>ThreadLocal 每个线程一个实例，无锁，性能最佳</li>
     * </ul>
     */
    private static final ThreadLocal<Kryo> KRYO_HOLDER = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 不强制注册类：允许序列化任意类的对象
        // 代价：每个对象多写一点类名信息，性能略低
        // 好处：使用方不用预先 register(Xxx.class)，开发友好
        kryo.setRegistrationRequired(false);
        // Kryo 5 默认禁用引用跟踪，序列化图无环时更高效
        // 如需序列化循环引用对象，可用 Pool 方式并自行配置
        return kryo;
    });

    /** 从当前线程拿 Kryo 实例 */
    private Kryo getKryo() {
        return KRYO_HOLDER.get();
    }

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            // writeClassAndObject：把对象的类信息 + 字段值都写进去
            // 这样反序列化时不需要预先知道类型也能还原
            getKryo().writeClassAndObject(output, obj);
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo 序列化失败: " + obj.getClass(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             Input input = new Input(bais)) {
            // readClassAndObject：根据字节里的类信息还原对象
            // 因为序列化时用了 writeClassAndObject，这里不需要传 clazz
            Object obj = getKryo().readClassAndObject(input);
            return clazz.cast(obj);
        } catch (Exception e) {
            throw new RuntimeException("Kryo 反序列化失败: " + clazz, e);
        }
    }

    @Override
    public int getCode() {
        return 1; // 协议头"序列化类型"字段：1 = Kryo
    }
}
