package com.myrpc.core.registry;

import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 注册中心的 Zookeeper 实现 —— 服务名 → 实例地址 存到 ZK 节点树。
 *
 * <p>对应 ZK 结构（见 {@link ServiceRegistry} 接口注释）：
 * <pre>
 * /my-rpc/com.myrpc.api.HelloService        ← register 创建（持久）
 *      └── 127.0.0.1:18090                  ← register 创建（临时）
 * </pre>
 *
 * <p>本阶段实现 {@link #register}/{@link #unregister} 和基础 {@link #lookup}；
 * 阶段 10 会给 lookup 加本地缓存 + watcher（服务变更自动通知）。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/09-Zookeeper集成与服务注册} §1.3
 */
public class ZkServiceRegistry implements ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ZkServiceRegistry.class);

    @Override
    public void register(String serviceName, InetSocketAddress address) {
        CuratorFramework client = CuratorUtils.getZkClient();
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + serviceName;
        // 服务名是持久节点：所有 Provider 共享，全下线也不删（留着给后来者挂）
        CuratorUtils.createPersistentNode(client, servicePath);
        // 实例地址是临时节点：本 Provider 独占，session 断开自动消失
        CuratorUtils.createEphemeralNode(client, CuratorUtils.buildRegisterPath(serviceName, address));
    }

    @Override
    public void unregister(String serviceName, InetSocketAddress address) {
        CuratorFramework client = CuratorUtils.getZkClient();
        String path = CuratorUtils.buildRegisterPath(serviceName, address);
        try {
            if (client.checkExists().forPath(path) != null) {
                client.delete().forPath(path);
                log.info("反注册服务节点: {}", path);
            }
        } catch (Exception e) {
            log.error("反注册失败: {}", path, e);
        }
    }

    @Override
    public List<InetSocketAddress> lookup(String serviceName) {
        CuratorFramework client = CuratorUtils.getZkClient();
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + serviceName;
        try {
            if (client.checkExists().forPath(servicePath) == null) {
                return Collections.emptyList(); // 服务还没注册过
            }
            List<String> children = client.getChildren().forPath(servicePath);
            List<InetSocketAddress> addresses = new ArrayList<>(children.size());
            for (String child : children) {
                // 节点名格式 "127.0.0.1:18090"，拆成 host + port
                int idx = child.lastIndexOf(':');
                String host = child.substring(0, idx);
                int port = Integer.parseInt(child.substring(idx + 1));
                addresses.add(new InetSocketAddress(host, port));
            }
            log.info("服务 {} 发现 {} 个实例: {}", serviceName, addresses.size(), addresses);
            return addresses;
        } catch (Exception e) {
            log.error("查询服务地址失败: {}", servicePath, e);
            return Collections.emptyList();
        }
    }
}
