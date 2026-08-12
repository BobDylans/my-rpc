package com.myrpc.core.loadbalancer;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 随机负载均衡单元测试 —— 不依赖网络，纯逻辑验证。
 *
 * <p>验证：
 * <ul>
 *   <li>空列表 / null → 返回 null</li>
 *   <li>单个地址 → 必返回这一个</li>
 *   <li>多个地址 → 随机命中，统计 1000 次的分布大致均匀</li>
 * </ul>
 */
class RandomLoadBalancerTest {

    private final RandomLoadBalancer balancer = new RandomLoadBalancer();

    @Test
    void emptyListReturnsNull() {
        assertNull(balancer.select(null, "k"));
        assertNull(balancer.select(Collections.emptyList(), "k"));
    }

    @Test
    void singleAddressAlwaysSelected() {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 9000);
        InetSocketAddress selected = balancer.select(List.of(addr), "k");
        assertEquals(addr, selected);
    }

    @Test
    void multipleAddressesAllHitOverManyTries() {
        InetSocketAddress a = new InetSocketAddress("10.0.0.1", 9000);
        InetSocketAddress b = new InetSocketAddress("10.0.0.2", 9000);
        InetSocketAddress c = new InetSocketAddress("10.0.0.3", 9000);
        List<InetSocketAddress> addrs = Arrays.asList(a, b, c);

        int hitsA = 0, hitsB = 0, hitsC = 0;
        for (int i = 0; i < 3000; i++) {
            InetSocketAddress sel = balancer.select(addrs, "k");
            if (sel == a) hitsA++;
            else if (sel == b) hitsB++;
            else if (sel == c) hitsC++;
        }
        // 3000 次 / 3 个地址 ≈ 1000 次，允许 ±20% 浮动
        assertTrue(hitsA > 800 && hitsA < 1200, "A 命中次数应接近 1000，实际: " + hitsA);
        assertTrue(hitsB > 800 && hitsB < 1200, "B 命中次数应接近 1000，实际: " + hitsB);
        assertTrue(hitsC > 800 && hitsC < 1200, "C 命中次数应接近 1000，实际: " + hitsC);
    }
}
