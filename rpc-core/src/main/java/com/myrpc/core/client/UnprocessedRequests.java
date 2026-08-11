package com.myrpc.core.client;

import com.myrpc.api.dto.RpcResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 未完成请求表 —— requestId → CompletableFuture 的映射。
 *
 * <p>这是客户端"异步转同步"的核心枢纽：
 * <ol>
 *   <li>发送请求前：{@link #put} 注册一个 Future，等待响应</li>
 *   <li>收到响应时：{@link #complete} 按 requestId 找到 Future 并 complete</li>
 *   <li>调用线程：{@code future.get()} 阻塞等待结果</li>
 * </ol>
 *
 * <h2>为什么必须要有 requestId 配对？</h2>
 * TCP 是<b>全双工</b>：客户端可以连续发多个请求，服务端响应到达的
 * <b>顺序不一定和请求一致</b>（乱序）。没有 requestId，收到响应时
 * 无法知道它属于哪个请求。有了 id → Future 映射，响应一来就能精确配对。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/07-客户端-动态代理与Future异步} §1.2
 */
public final class UnprocessedRequests {

    /**
     * 注意：complete 后要 remove —— 否则已完成的请求留在表里，长时间运行会内存泄漏。
     * complete 方法里 remove，保证"配对即清理"。
     * 实际上这里存储着多个future任务,通过唯一的requestId将他们串联起来
     */
    private static final Map<Long, CompletableFuture<RpcResponse>> MAP = new ConcurrentHashMap<>();

    private UnprocessedRequests() {}

    /**
     * 注册一个待处理请求，返回对应的 Future。
     * 调用线程拿着这个 Future 阻塞等待结果。
     */
    public static CompletableFuture<RpcResponse> put(long requestId) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        MAP.put(requestId, future);
        return future;
    }

    /**
     * 收到响应时调用：按 requestId 找到 Future 并 complete，同时移除（配对即清理）。
     * 找不到（超时已被移除 / 重复响应）则忽略。
     */
    public static void complete(long requestId, RpcResponse response) {
        // 前面将future返回后,就需要对应的调用complete将结果返回
        // 实际上是根据requestId确认对应位置的future
        CompletableFuture<RpcResponse> future = MAP.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * 手动移除（超时等场景）：Future 仍在，只是从表里摘掉，避免泄漏。
     * 调用线程的 get 会继续阻塞到超时 —— 所以调用方应配合 completeExceptionally。
     */
    public static void remove(long requestId) {
        MAP.remove(requestId);
    }

    /**
     * 连接断开时调用：把表里所有未完成请求标记为失败。
     * 这样等待的调用线程立刻抛异常，而不是永久阻塞。
     *
     * <p>注意：本阶段是单连接场景，failAll 合理；将来多连接/连接池
     * 需要按 Channel 维度区分，避免误伤其他连接的请求。
     */
    public static void failAll(Throwable cause) {
        MAP.values().forEach(f -> f.completeExceptionally(cause));
        MAP.clear();
    }

    /** 当前未完成请求数（测试/监控用） */
    public static int pendingCount() {
        return MAP.size();
    }
}
