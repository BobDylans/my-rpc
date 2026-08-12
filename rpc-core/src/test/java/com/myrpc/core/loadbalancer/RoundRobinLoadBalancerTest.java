package com.myrpc.core.loadbalancer;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 轮询负载均衡单元测试。
 *
 * <p>验证：
 * <ul>
 *   <li>空列表 → null</li>
 *   <li>单个地址 → 始终返回这一个</li>
 *   <li>多个地址 → 严格按顺序轮询</li>
 * </ul>
 */
class RoundRobinLoadBalancerTest {

    private final RoundRobinLoadBalancer balancer = new RoundRobinLoadBalancer();

    @Test
    void emptyListReturnsNull() {
        assertNull(balancer.select(null, "k"));
        assertNull(balancer.select(List.of(), "k"));
    }

    @Test
    void singleAddressAlwaysSelected() {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 9000);
        InetSocketAddress selected = balancer.select(List.of(addr), "k");
        assertEquals(addr, selected);
    }

    @Test
    void multipleAddressesStrictlyRoundRobin() {
        InetSocketAddress a = new InetSocketAddress("10.0.0.1", 9000);
        InetSocketAddress b = new InetSocketAddress("10.0.0.2", 9000);
        InetSocketAddress c = new InetSocketAddress("10.0.0.3", 9000);
        List<InetSocketAddress> addrs = Arrays.asList(a, b, c);

        // 前 6 次应该是 A B C A B C
        assertEquals(a, balancer.select(addrs, "k"));
        assertEquals(b, balancer.select(addrs, "k"));
        assertEquals(c, balancer.select(addrs, "k"));
        assertEquals(a, balancer.select(addrs, "k"));
        assertEquals(b, balancer.select(addrs, "k"));
        assertEquals(c, balancer.select(addrs, "k"));
    }

    @Test
    void selectWorksWithAnyKey() {
        // key 参数被轮询忽略（所有 key 都按计数器走）
        InetSocketAddress a = new InetSocketAddress("10.0.0.1", 9000);
        InetSocketAddress b = new InetSocketAddress("10.0.0.2", 9000);
        List<InetSocketAddress> addrs = Arrays.asList(a, b);

        assertEquals(a, balancer.select(addrs, "request-1"));
        assertEquals(b, balancer.select(addrs, "request-2"));
        assertEquals(a, balancer.select(addrs, "request-3"));
    }
}
