package com.myrpc.api.dto;

import java.io.Serializable;
import java.util.Arrays;

/**
 * RPC 请求体（在协议数据体里被序列化）。
 *
 * <p>消费者把一次方法调用打包成这个对象，包含：
 * <ul>
 *   <li>{@code requestId} —— 唯一标识，用于匹配响应（TCP 全双工，可能乱序）</li>
 *   <li>{@code interfaceName} —— 目标接口全限定名，服务端据此查注册表</li>
 *   <li>{@code methodName} —— 方法名</li>
 *   <li>{@code paramTypes} —— 参数类型 Class 对象数组（支持重载）</li>
 *   <li>{@code parameters} —— 实际参数值</li>
 * </ul>
 *
 * <p>阶段 06/07 的核心数据结构。对应学习文档：
 * {@link /后端知识/中间件/06-服务端-Netty与反射调用} 和
 * {@link /后端知识/中间件/07-客户端-动态代理与Future异步}
 */
public class RpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 唯一请求 ID，用于异步匹配响应 */
    private long requestId;

    /** 目标接口全限定名，如 com.myrpc.api.HelloService */
    private String interfaceName;

    /** 方法名 */
    private String methodName;

    /** 参数类型（Class 对象数组），反射 getMethod 需要 */
    private Class<?>[] paramTypes;

    /** 实际参数值 */
    private Object[] parameters;

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public Class<?>[] getParamTypes() { return paramTypes; }
    public void setParamTypes(Class<?>[] paramTypes) { this.paramTypes = paramTypes; }

    public Object[] getParameters() { return parameters; }
    public void setParameters(Object[] parameters) { this.parameters = parameters; }

    @Override
    public String toString() {
        return "RpcRequest{" +
                "requestId=" + requestId +
                ", interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", paramTypes=" + Arrays.toString(paramTypes) +
                ", parameters=" + Arrays.toString(parameters) +
                '}';
    }
}
