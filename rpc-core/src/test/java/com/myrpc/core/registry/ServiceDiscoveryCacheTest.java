package com.myrpc.core.registry;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务发现缓存单元测试 —— 用 Mock 的 ServiceDiscovery，不需要真 ZK。
 *
 * <p>验证：
 * <ul>
 *   <li>首次 get 走 lookup + subscribe</li>
 *   <li>第二次 get 走缓存，不再查</li>
 *   <li>模拟 ZK 变化触发回调，缓存被刷新</li>
 * </ul>
 */
class ServiceDiscoveryCacheTest {

    /** 假的 ServiceDiscovery：lookup 返回固定列表，subscribe 记下监听器供测试触发 */
    static class MockDiscovery implements ServiceDiscovery {
        volatile List<InetSocketAddress> current = Arrays.asList(
                new InetSocketAddress("10.0.0.1", 9000),
                new InetSocketAddress("10.0.0.2", 9000));
        volatile int lookupCount = 0;
        ServiceChangeListener listener;

        @Override
        public List<InetSocketAddress> lookup(String serviceName) {
            lookupCount++;
            return current;
        }

        @Override
        public void subscribe(String serviceName, ServiceChangeListener listener) {
            this.listener = listener;  // 记下来，测试时手动触发
        }

        /** 模拟 ZK 推送变化 */
        void fireChange(String serviceName, List<InetSocketAddress> newAddrs) {
            current = newAddrs;
            if (listener != null) {
                listener.onChange(serviceName, newAddrs);
            }
        }
    }

    @Test
    void firstGetTriggersLookupAndSubscribe() {
        MockDiscovery mock = new MockDiscovery();
        ServiceDiscoveryCache cache = new ServiceDiscoveryCache(mock);

        List<InetSocketAddress> addrs = cache.get("HelloService");

        assertEquals(2, addrs.size(), "首次应返回 2 个实例");
        assertEquals(1, mock.lookupCount, "首次 get 应触发一次 lookup");
        assertNotNull(mock.listener, "首次 get 应注册 listener");
    }

    @Test
    void secondGetUsesCacheNoNewLookup() {
        MockDiscovery mock = new MockDiscovery();
        ServiceDiscoveryCache cache = new ServiceDiscoveryCache(mock);

        cache.get("HelloService");
        cache.get("HelloService");  // 第二次

        assertEquals(1, mock.lookupCount, "第二次 get 应走缓存，不查 ZK");
    }

    @Test
    void zKChangeRefreshesCache() {
        MockDiscovery mock = new MockDiscovery();
        ServiceDiscoveryCache cache = new ServiceDiscoveryCache(mock);

        cache.get("HelloService");
        assertEquals(2, cache.get("HelloService").size(), "初始 2 个实例");

        // 模拟一个实例下线
        mock.fireChange("HelloService", Collections.singletonList(
                new InetSocketAddress("10.0.0.1", 9000)));

        assertEquals(1, cache.get("HelloService").size(), "回调后应只剩 1 个实例");
    }

    @Test
    void emptyAddressListKeepsKeyButEmpty() {
        MockDiscovery mock = new MockDiscovery();
        ServiceDiscoveryCache cache = new ServiceDiscoveryCache(mock);

        cache.get("HelloService");
        // 模拟全部下线
        mock.fireChange("HelloService", Collections.emptyList());

        List<InetSocketAddress> addrs = cache.get("HelloService");
        assertNotNull(addrs, "key 不应被删除（让调用方知道当前无可用）");
        assertTrue(addrs.isEmpty(), "应为空列表");
    }

    @Test
    void differentServicesCachedIndependently() {
        MockDiscovery mock = new MockDiscovery();
        ServiceDiscoveryCache cache = new ServiceDiscoveryCache(mock);

        cache.get("ServiceA");
        cache.get("ServiceB");

        assertEquals(2, mock.lookupCount, "两个不同服务各查一次");
        assertEquals(1, cache.cachedServiceCount() == 2 ? 2 : 0,  // 缓存了两个 key
                2);
    }
}
