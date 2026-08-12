package com.myrpc.core.loadbalancer;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 一致性哈希负载均衡单元测试 —— 核心验证"实例增减时 key 迁移最少"。
 *
 * <p>测试场景：
 * <ul>
 *   <li>空列表 → null</li>
 *   <li>同一 key 两次查询 → 同一地址（粘性）</li>
 *   <li>加一个节点 → 只有约 1/N 的 key 迁移</li>
 *   <li>删一个节点 → 只有受影响区段的 key 迁移</li>
 *   <li>分布相对均匀（虚拟节点的作用）</li>
 * </ul>
 */
class ConsistentHashLoadBalancerTest {

    private final ConsistentHashLoadBalancer balancer = new ConsistentHashLoadBalancer();

    private InetSocketAddress addr(String host, int port) {
        return new InetSocketAddress(host, port);
    }

    private List<InetSocketAddress> addrs(InetSocketAddress... a) {
        return Arrays.asList(a);
    }

    @Test
    void emptyListReturnsNull() {
        assertNull(balancer.select(null, "k"));
        assertNull(balancer.select(List.of(), "k"));
    }

    @Test
    void sameKeyAlwaysSameAddress() {
        List<InetSocketAddress> addrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000));

        // 同一 key 调多次，必须始终返回同一地址（粘性）
        InetSocketAddress first = balancer.select(addrs, "user-42");
        for (int i = 0; i < 20; i++) {
            assertEquals(first, balancer.select(addrs, "user-42"),
                    "同一 key 必须始终命中同一实例（会话粘性）");
        }
    }

    @Test
    void differentKeysDistributeAcrossNodes() {
        List<InetSocketAddress> addrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000));

        // 1000 个不同 key，统计每个节点命中数
        Map<InetSocketAddress, Integer> hits = new HashMap<>();
        for (int i = 0; i < 3000; i++) {
            InetSocketAddress sel = balancer.select(addrs, "user-" + i);
            hits.merge(sel, 1, Integer::sum);
        }

        // 虚拟节点 160，3000 次 / 3 实例 ≈ 1000 次，一致性哈希分布有统计波动，放宽阈值
        // 重点不是"严格均匀"，而是"没有实例被饿死"
        for (InetSocketAddress a : addrs) {
            int count = hits.getOrDefault(a, 0);
            assertTrue(count > 400, "实例 " + a + " 命中数应 > 400，实际: " + count);
        }
    }

    @Test
    void addNodeOnlyMigratesFractionOfKeys() {
        // 一致性哈希的核心特性：加一个节点，只有约 1/N 的 key 迁移
        List<InetSocketAddress> threeAddrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000));
        List<InetSocketAddress> fourAddrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000),
                addr("10.0.0.4", 9000));  // 新增节点

        int total = 3000;
        int migrated = 0;
        for (int i = 0; i < total; i++) {
            String key = "user-" + i;
            InetSocketAddress before = balancer.select(threeAddrs, key);
            InetSocketAddress after = balancer.select(fourAddrs, key);
            if (!before.equals(after)) {
                migrated++;
            }
        }

        // 加 1 个节点到 4 个里，理论迁移率 1/4 = 25%
        // 虚拟节点可能略有偏差，允许 15%-35%
        double ratio = (double) migrated / total;
        assertTrue(ratio > 0.15 && ratio < 0.35,
                "迁移比例应接近 25%（1/4），实际: " + ratio + " (" + migrated + "/" + total + ")");
    }

    @Test
    void removeNodeOnlyMigratesAffectedSegment() {
        // 删一个节点 → 只有该节点负责的区段迁移到其他节点
        List<InetSocketAddress> fourAddrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000),
                addr("10.0.0.4", 9000));
        List<InetSocketAddress> threeAddrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000));  // 删除 0.4

        int total = 3000;
        int migrated = 0;
        for (int i = 0; i < total; i++) {
            String key = "user-" + i;
            InetSocketAddress before = balancer.select(fourAddrs, key);
            InetSocketAddress after = balancer.select(threeAddrs, key);
            if (!before.equals(after)) {
                migrated++;
            }
        }

        // 删 1 个节点 → 只有原本属于 0.4 的 1/4 key 迁移
        double ratio = (double) migrated / total;
        assertTrue(ratio > 0.15 && ratio < 0.35,
                "迁移比例应接近 25%，实际: " + ratio);
    }

    @Test
    void migratedKeysGoToNewNodeOnAdd() {
        // 加节点时，迁移的 key 应该都落到新节点上
        List<InetSocketAddress> threeAddrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000));
        InetSocketAddress newNode = addr("10.0.0.4", 9000);
        List<InetSocketAddress> fourAddrs = addrs(
                addr("10.0.0.1", 9000),
                addr("10.0.0.2", 9000),
                addr("10.0.0.3", 9000),
                newNode);

        int migratedToNew = 0;
        int migratedToOld = 0;
        for (int i = 0; i < 3000; i++) {
            String key = "user-" + i;
            InetSocketAddress before = balancer.select(threeAddrs, key);
            InetSocketAddress after = balancer.select(fourAddrs, key);
            if (!before.equals(after)) {
                if (after.equals(newNode)) migratedToNew++;
                else migratedToOld++;
            }
        }

        // 迁移的 key 绝大多数应该到新节点
        assertTrue(migratedToNew > migratedToOld * 4,
                "迁移的 key 应主要落到新节点，新节点: " + migratedToNew + " 旧节点: " + migratedToOld);
    }
}
