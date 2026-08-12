package com.myrpc.core.registry;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Curator 工具类 —— Zookeeper 客户端的单例管理与节点操作。
 *
 * <p>封装四个操作：
 * <ul>
 *   <li>{@link #getZkClient()}：获取全局单例客户端（双检锁）</li>
 *   <li>{@link #createPersistentNode}：创建持久节点（服务名，全下线也不删）</li>
 *   <li>{@link #createEphemeralNode}：创建临时节点（实例地址，session 断开自动消失）</li>
 *   <li>{@link #clearRegistry}：清空本机注册的所有节点（优雅关闭时）</li>
 * </ul>
 *
 * <h2>为什么实例地址用临时节点（EPHEMERAL）？</h2>
 * 临时节点绑定 ZK session：Provider 宕机（甚至没来得及反注册），
 * session 超时后 ZK 自动删除节点 —— 这就是"自动感知下线"的机制。
 * 如果用持久节点，Provider 挂了自己不会删，Consumer 会一直拿到死地址。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/09-Zookeeper集成与服务注册} §1.2 §1.3
 */
public final class CuratorUtils {

    private static final Logger log = LoggerFactory.getLogger(CuratorUtils.class);

    /** 注册中心根路径：/my-rpc/服务名/实例地址 */
    public static final String ZK_REGISTER_ROOT_PATH = "/my-rpc";

    /** 默认 ZK 地址（本机 Docker 起的） */
    public static final String DEFAULT_ZOOKEEPER_ADDRESS = "127.0.0.1:2181";

    private static final int BASE_SLEEP_TIME_MS = 1000;
    private static final int MAX_RETRIES = 3;
    private static final int SESSION_TIMEOUT_MS = 60_000;

    /** 本进程注册过的节点路径集合，优雅关闭时按它清理 */
    private static final Set<String> REGISTERED_PATH_SET = ConcurrentHashMap.newKeySet();

    private static final Object LOCK = new Object();
    private static volatile CuratorFramework zkClient;

    private CuratorUtils() {}

    /**
     * 获取全局唯一 Curator 客户端（双检锁单例）。
     *
     * <p>为什么单例？Curator 客户端内部维护与 ZK 的长连接和 session，
     * 每次 new 都会建立新 session（连接数爆炸 + 临时节点归属错乱）。
     * 整个进程共享一个即可。
     */
    public static CuratorFramework getZkClient() {
        CuratorFramework current = zkClient;
        if (current != null && current.isStarted()) {
            return current;
        }
        // 使用一个单独的实例来确保线程安全
        synchronized (LOCK) {
            current = zkClient;
            if (current != null && current.isStarted()) {
                return current;
            }
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(BASE_SLEEP_TIME_MS, MAX_RETRIES);
            zkClient = CuratorFrameworkFactory.builder()
                    .connectString(DEFAULT_ZOOKEEPER_ADDRESS)
                    .sessionTimeoutMs(SESSION_TIMEOUT_MS)
                    .retryPolicy(retryPolicy)
                    .build();
            // 创建好zk后启动
            // 启动之后就进入了一个session
            // 如果session结束这个节点就会自动从zk server中删除
            zkClient.start();
            // start() 是异步的！这里阻塞等 session 真正建立，连不上立刻抛异常
            // （否则后续 create 会无限卡在重试上）
            try {
                if (!zkClient.blockUntilConnected(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("连接 Zookeeper 超时: " + DEFAULT_ZOOKEEPER_ADDRESS
                            + "（docker run -d -p 2181:2181 zookeeper:3.9）");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断标志，避免吞掉中断
                throw new IllegalStateException("等待 ZK 连接被中断", e);
            }
            log.info("ZK 客户端已连接: {}", DEFAULT_ZOOKEEPER_ADDRESS);
            return zkClient;
        }
    }

    /** 拼注册路径：/my-rpc/服务名/主机:端口 */
    public static String buildRegisterPath(String serviceName, InetSocketAddress address) {
        return ZK_REGISTER_ROOT_PATH + "/" + serviceName + "/"
                + address.getHostString() + ":" + address.getPort();
    }

    /**
     * 创建持久节点（服务名）。已存在则跳过。
     * creatingParentsIfNeeded：自动创建缺失的父路径（/my-rpc 等）。
     */
    public static void createPersistentNode(CuratorFramework client, String path) {
        try {
            // 先检查是否已经存在,如果已经有了就直接退出
            if (client.checkExists().forPath(path) != null) {
                return;
            }
            client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(path);
            log.debug("创建持久节点: {}", path);
        } catch (Exception e) {
            log.error("创建持久节点失败: {}", path, e);
        }
    }

    /**
     * 创建临时节点（实例地址）。已存在则跳过。
     * session 断开自动消失 —— 服务下线自动感知的关键。
     */
    public static void createEphemeralNode(CuratorFramework client, String path) {
        try {
            if (client.checkExists().forPath(path) != null) {
                return;
            }
            client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    // 只传递了path没有传data
                    // 相当于将节点的信息放到了路径中  /my-rpc / com.myrpc.HelloService / 192.168.1.5:7400
                    .forPath(path);
            // 添加到本进程中的注册了哪些节点中
            REGISTERED_PATH_SET.add(path);
            log.info("注册服务节点: {}", path);
        } catch (Exception e) {
            log.error("创建临时节点失败: {}", path, e);
        }
    }

    /**
     * 清空本进程注册的所有节点（优雅关闭 / shutdown hook 时调用）。
     * 只删本机地址结尾的临时节点，不影响其他 Provider。
     */
    public static void clearRegistry(CuratorFramework client, InetSocketAddress address) {
        String addr = address.getHostString() + ":" + address.getPort();
        for (String path : new HashSet<>(REGISTERED_PATH_SET)) {
            if (!path.endsWith("/" + addr)) {
                continue; // 只清理自己的节点
            }
            try {
                if (client.checkExists().forPath(path) != null) {
                    client.delete().forPath(path);
                }
                REGISTERED_PATH_SET.remove(path);
                log.info("反注册服务节点: {}", path);
            } catch (Exception e) {
                log.error("反注册失败: {}", path, e);
            }
        }
    }
}
