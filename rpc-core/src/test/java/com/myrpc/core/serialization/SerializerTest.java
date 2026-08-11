package com.myrpc.core.serialization;

import com.myrpc.api.dto.RpcRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 序列化器单元测试 + JDK vs Kryo 性能对比。
 *
 * <p>验证两件事：
 * <ul>
 *   <li>功能正确性：序列化→反序列化 round-trip 数据一致</li>
 *   <li>性能对比：Kryo 应比 JDK 更快、更紧凑</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/05-序列化层-JDK到Kryo} §动手任务
 */
class SerializerTest {

    @Test
    void testJdkRoundTrip() {
        Serializer serializer = new JdkSerializer();
        RpcRequest original = buildSampleRequest();

        byte[] bytes = serializer.serialize(original);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        RpcRequest restored = serializer.deserialize(bytes, RpcRequest.class);
        assertEquals(original.getRequestId(), restored.getRequestId());
        assertEquals(original.getInterfaceName(), restored.getInterfaceName());
        assertEquals(original.getMethodName(), restored.getMethodName());
        assertArrayEquals(original.getParamTypes(), restored.getParamTypes());
        assertArrayEquals(original.getParameters(), restored.getParameters());
    }

    @Test
    void testKryoRoundTrip() {
        Serializer serializer = new KryoSerializer();
        RpcRequest original = buildSampleRequest();

        byte[] bytes = serializer.serialize(original);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        RpcRequest restored = serializer.deserialize(bytes, RpcRequest.class);
        assertEquals(original.getRequestId(), restored.getRequestId());
        assertEquals(original.getInterfaceName(), restored.getInterfaceName());
        assertEquals(original.getMethodName(), restored.getMethodName());
        assertArrayEquals(original.getParamTypes(), restored.getParamTypes());
        assertArrayEquals(original.getParameters(), restored.getParameters());
    }

    @Test
    void testSerializersRegistry() {
        // 验证注册表按 code 查找
        assertInstanceOf(JdkSerializer.class, Serializers.of(0));
        assertInstanceOf(KryoSerializer.class, Serializers.of(1));
    }

    @Test
    void testKryoThreadSafety() throws Exception {
        // 多线程并发使用，验证 ThreadLocal 生效
        Serializer serializer = new KryoSerializer();
        int threads = 10;
        Thread[] ts = new Thread[threads];
        boolean[] results = new boolean[threads];

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            ts[i] = new Thread(() -> {
                RpcRequest req = buildSampleRequest();
                req.setRequestId(idx);
                byte[] bytes = serializer.serialize(req);
                RpcRequest restored = serializer.deserialize(bytes, RpcRequest.class);
                results[idx] = (idx == restored.getRequestId());
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        for (boolean r : results) assertTrue(r, "线程并发序列化失败");
    }

    @Test
    void testPerformanceComparison() {
        Serializer jdk = new JdkSerializer();
        Serializer kryo = new KryoSerializer();
        RpcRequest sample = buildSampleRequest();

        int warmup = 1_000;   // 预热，让 JIT 生效
        int runs = 100_000;  // 正式跑

        // 预热
        for (int i = 0; i < warmup; i++) {
            byte[] b = jdk.serialize(sample);
            jdk.deserialize(b, RpcRequest.class);
            b = kryo.serialize(sample);
            kryo.deserialize(b, RpcRequest.class);
        }

        // JDK 计时
        long jdkStart = System.nanoTime();
        byte[] jdkBytes = null;
        for (int i = 0; i < runs; i++) {
            jdkBytes = jdk.serialize(sample);
            jdk.deserialize(jdkBytes, RpcRequest.class);
        }
        long jdkTime = System.nanoTime() - jdkStart;

        // Kryo 计时
        long kryoStart = System.nanoTime();
        byte[] kryoBytes = null;
        for (int i = 0; i < runs; i++) {
            kryoBytes = kryo.serialize(sample);
            kryo.deserialize(kryoBytes, RpcRequest.class);
        }
        long kryoTime = System.nanoTime() - kryoStart;

        // 打印对比结果
        System.out.println("==== 序列化性能对比 (" + runs + " 次 round-trip) ====");
        System.out.println("JDK  字节数: " + jdkBytes.length
                + "  耗时: " + (jdkTime / 1_000_000) + " ms");
        System.out.println("Kryo 字节数: " + kryoBytes.length
                + "  耗时: " + (kryoTime / 1_000_000) + " ms");
        System.out.println("体积比: Kryo/JDK = "
                + String.format("%.2f", kryoBytes.length * 100.0 / jdkBytes.length) + "%");
        System.out.println("速度比: Kryo 比 JDK 快 "
                + String.format("%.1f", jdkTime * 1.0 / kryoTime) + " 倍");

        // 断言 Kryo 更紧凑
        assertTrue(kryoBytes.length < jdkBytes.length,
                "Kryo 体积应小于 JDK");
    }

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
