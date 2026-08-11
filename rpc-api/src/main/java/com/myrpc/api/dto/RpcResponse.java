package com.myrpc.api.dto;

import java.io.Serializable;

/**
 * RPC 响应体（在协议数据体里被序列化）。
 *
 * <p>服务端反射调用目标方法后，把结果包装成本对象回传给消费者：
 * <ul>
 *   <li>{@code requestId} —— 与对应请求一致，消费者据此找到等待中的 Future</li>
 *   <li>{@code data} —— 方法返回值（void 方法为 null）</li>
 *   <li>{@code message} —— 异常信息（调用抛异常时填这里）</li>
 * </ul>
 *
 * <p>阶段 06 的核心数据结构。对应学习文档：
 * {@link /后端知识/中间件/06-服务端-Netty与反射调用}
 */
// 服务方调用结束后将结果包装好后返回
public class RpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对应请求的 ID，消费者据此完成等待中的 Future */
    private long requestId;

    /** 方法返回值（void 方法为 null） */
    private Object data;

    /** 异常信息：方法抛异常时填这里，而不是让 Netty 吞掉 */
    private String message;

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String toString() {
        return "RpcResponse{" +
                "requestId=" + requestId +
                ", data=" + data +
                ", message='" + message + '\'' +
                '}';
    }
}
