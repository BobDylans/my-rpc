package com.myrpc.core.loadbalancer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡 —— 按顺序依次选，N 个实例轮流命中，分布绝对均匀。
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li>用 {@link AtomicInteger} 自增取模，无锁 CAS，性能好</li>
 *   <li><b>取绝对值</b>：{@code getAndIncrement()} 在溢出到 {@code Integer.MIN_VALUE} 时
 *       会变成负数，{@code % size} 也为负，导致 {@code List#get} 抛
 *       {@code IndexOutOfBoundsException}。必须 {@code Math.abs}</li>
 * </ul>
 *
 * <h2>轮询 vs 随机</h2>
 * <table>
 *   <tr><th></th><th>轮询</th><th>随机</th></tr>
 *   <tr><td>分布</td><td>绝对均匀</td><td>概率均匀（小样本可能偏）</td></tr>
 *   <tr><td>状态</td><td>有状态（计数器）</td><td>无状态</td></tr>
 *   <tr><td>实例变化</td><td>计数器不变，但列表 size 变 → 取模结果错位</td><td>不受影响</td></tr>
 * </table>
 *
 * <p>轮询的"实例变化错位"问题：假设 3 个实例 [A,B,C]，counter=5（下次选 B），
 * 此时 B 下线变成 [A,C]，counter=5 % 2=1 → C。原本该选 B 的请求被转到 C，
 * 短期内分布会偏。生产环境可用"加权轮询"或"最活跃数"缓解，本实现是基础版。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/11-负载均衡-随机轮询一致性哈希} §1.2
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    /** 全局自增计数器（所有调用共享，保证依次轮询） */
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses, String key) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        int size = addresses.size();
        // 取绝对值防止 Integer 溢出到负数导致取模为负
        int idx = Math.abs(counter.getAndIncrement()) % size;
        return addresses.get(idx);
    }
}
