package com.myrpc.core.proxy;

import com.myrpc.api.dto.RpcRequest;
import com.myrpc.api.dto.RpcResponse;
import com.myrpc.core.client.RpcClient;
import com.myrpc.core.client.UnprocessedRequests;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端动态代理 —— 让"调用远程方法像调用本地方法一样"。
 *
 * <p>JDK 动态代理原理：运行时生成一个实现了目标接口的代理类，
 * 接口上的<b>所有方法调用都转发到 {@link #invoke}</b>。
 * 业务方拿到的 HelloService 其实是代理对象，方法调用根本不走本地实现，
 * 而是被包装成 RpcRequest 发到服务端，等响应回来再返回结果。
 *
 * <h2>invoke 的完整流程</h2>
 * <ol>
 *   <li>Object 方法（toString/equals/hashCode）走本地，不远程调用</li>
 *   <li>组装 RpcRequest：requestId + 接口名 + 方法名 + 参数类型 + 参数</li>
 *   <li>注册 Future 到 {@link UnprocessedRequests}，异步发送请求</li>
 *   <li>future.get(超时) 阻塞等待 —— 响应一到，RpcResponseHandler 就 complete 它</li>
 *   <li>检查响应：message 非空说明服务端调用失败，抛异常；否则返回 data</li>
 * </ol>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/07-客户端-动态代理与Future异步} §1.3
 */
public class RpcClientProxy implements InvocationHandler {

    /** 全局自增 requestId（AtomicLong 线程安全） */
    private static final AtomicLong REQUEST_ID = new AtomicLong(0);

    /** 调用超时：5 秒（生产应可配置） */
    private static final long TIMEOUT_SECONDS = 5;

    private final RpcClient client;
    private final Class<?> serviceInterface;

    public RpcClientProxy(RpcClient client, Class<?> serviceInterface) {
        this.client = client;
        this.serviceInterface = serviceInterface;
    }

    /**
     * 生成代理对象。调用方拿到后直接当真实接口用：
     * <pre>
     *   HelloService service = new RpcClientProxy(client, HelloService.class).getProxy();
     *   String result = service.sayHi("world"); // 这行背后是完整 RPC 调用
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy() {
        return (T) Proxy.newProxyInstance(
                // serviceInterface 这个实际上被代理的对象 
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // ① Object 方法本地处理：toString/equals/hashCode 不能远程调用
        //    否则调用方打印代理对象都会触发一次 RPC！
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }

        // ② 组装请求
        RpcRequest request = new RpcRequest();
        request.setRequestId(REQUEST_ID.incrementAndGet());
        request.setInterfaceName(serviceInterface.getName());
        request.setMethodName(method.getName());
        request.setParamTypes(method.getParameterTypes());
        request.setParameters(args);

        // ③ 注册 Future + 异步发送
        CompletableFuture<RpcResponse> future = UnprocessedRequests.put(request.getRequestId());
        try {
            client.send(request);
            // ④ 阻塞等响应（带超时，防止服务端挂掉导致永久阻塞）
            RpcResponse response = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // ⑤ 服务端异常通过 message 传回（见服务端 RpcRequestHandler）
            if (response.getMessage() != null) {
                throw new RuntimeException("远程调用失败: " + response.getMessage());
            }
            return response.getData();
        } catch (TimeoutException e) {
            // 超时：从表里摘掉这个请求，避免泄漏
            UnprocessedRequests.remove(request.getRequestId());
            throw new RuntimeException("RPC 调用超时: " + serviceInterface.getName()
                    + "#" + method.getName(), e);
        }
    }

    /**
     * Object 方法本地实现。
     */
    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "RpcClientProxy[" + serviceInterface.getName() + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new IllegalStateException("未知 Object 方法: " + method.getName());
        };
    }
}
