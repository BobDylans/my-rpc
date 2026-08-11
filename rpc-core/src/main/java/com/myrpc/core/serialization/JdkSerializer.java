package com.myrpc.core.serialization;

import java.io.*;

/**
 * JDK 原生序列化实现 —— 基于 {@link ObjectOutputStream} / {@link ObjectInputStream}。
 *
 * <p>作为学习起点，能最快跑通端到端调用。缺点（生产不推荐）：
 * <ul>
 *   <li>体积大（带类信息）</li>
 *   <li>性能差</li>
 *   <li>有安全漏洞（历史反序列化 RCE）</li>
 *   <li>要求对象实现 {@link Serializable}</li>
 * </ul>
 *
 * <p>阶段 5 会实现 Kryo 版本替换它。对应学习文档：
 * {@link /后端知识/中间件/05-序列化层-JDK到Kryo}
 */
public class JdkSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("JDK 序列化失败", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("JDK 反序列化失败", e);
        }
    }

    @Override
    public int getCode() {
        return 0;
    }
}
