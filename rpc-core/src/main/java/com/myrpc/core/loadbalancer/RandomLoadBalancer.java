package com.myrpc.core.loadbalancer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡 —— 从列表里随机挑一个。
 *
 * <p>实现最简单，实例性能相近时够用。阶段 11 会补轮询和一致性哈希。
 * 随机策略在实例数多时近似均匀。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/11-负载均衡-随机轮询一致性哈希} §1.2
 */
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses, String key) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        int idx = ThreadLocalRandom.current().nextInt(addresses.size());
        return addresses.get(idx);
    }
}
