package com.myrpc.core.server;

import com.myrpc.api.HelloService;
import com.myrpc.api.dto.RpcRequest;
import com.myrpc.api.dto.RpcResponse;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RpcRequestHandler 单元测试 —— 用 EmbeddedChannel 模拟完整调用链：
 * 入站 RpcRequest → handler 反射调用 → 出站 RpcResponse。
 *
 * <p>不依赖真实网络，专注验证 handler 的业务逻辑：
 * <ul>
 *   <li>正常调用：反射结果正确返回</li>
 *   <li>服务未注册：异常塞进 message 而不是抛出去</li>
 *   <li>目标方法抛异常：异常塞进 message 而不是被吞掉</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/06-服务端-Netty与反射调用} §1.3
 */
class RpcRequestHandlerTest {

    /** 构造一个 handler，注册 HelloService 的实现 */
    private RpcRequestHandler buildHandler() {
        ServiceRegistry registry = new ServiceRegistry();
        // lambda 实现 HelloService（HelloService 是函数式接口）
        registry.registerService(HelloService.class, (HelloService) name -> "Hi, " + name);
        return new RpcRequestHandler(registry);
    }

    /** 构造一个请求消息：调用 HelloService#sayHi(String) */
    private RpcMessage buildRequest(long requestId, String interfaceName, String name) {
        RpcRequest req = new RpcRequest();
        req.setRequestId(requestId);
        req.setInterfaceName(interfaceName);
        req.setMethodName("sayHi");
        req.setParamTypes(new Class<?>[]{String.class});
        req.setParameters(new Object[]{name});
        return new RpcMessage(MessageType.REQUEST.getCode(), (byte) 1, req); // 序列化类型 1 = Kryo
    }

    @Test
    void testInvokeSuccess() {
        EmbeddedChannel channel = new EmbeddedChannel(buildHandler());

        channel.writeInbound(buildRequest(1L, HelloService.class.getName(), "world"));

        // handler 通过 writeAndFlush 写出响应（outbound 方向）
        RpcMessage out = channel.readOutbound();
        assertNotNull(out, "应产出响应消息");
        assertEquals(MessageType.RESPONSE.getCode(), out.getMessageType());
        assertEquals((byte) 1, out.getSerializerType(), "序列化类型应沿用请求的");

        RpcResponse resp = (RpcResponse) out.getData();
        assertEquals(1L, resp.getRequestId());
        assertEquals("Hi, world", resp.getData(), "反射调用结果应正确");
        assertNull(resp.getMessage(), "正常调用不应有异常信息");

        channel.finish();
    }

    @Test
    void testServiceNotRegistered() {
        ServiceRegistry registry = new ServiceRegistry(); // 空注册表
        EmbeddedChannel channel = new EmbeddedChannel(new RpcRequestHandler(registry));

        channel.writeInbound(buildRequest(2L, "com.myrpc.api.NotExistService", "world"));

        RpcMessage out = channel.readOutbound();
        RpcResponse resp = (RpcResponse) out.getData();
        assertNull(resp.getData(), "未注册服务不应有返回值");
        assertNotNull(resp.getMessage(), "未注册服务应返回异常信息");
        assertTrue(resp.getMessage().contains("服务未注册"));

        channel.finish();
    }

    @Test
    void testTargetMethodThrows() {
        ServiceRegistry registry = new ServiceRegistry();
        // 实现故意抛异常
        registry.registerService(HelloService.class, (HelloService) name -> {
            throw new IllegalStateException("业务异常: " + name);
        });
        EmbeddedChannel channel = new EmbeddedChannel(new RpcRequestHandler(registry));

        channel.writeInbound(buildRequest(3L, HelloService.class.getName(), "boom"));

        RpcMessage out = channel.readOutbound();
        RpcResponse resp = (RpcResponse) out.getData();
        assertNull(resp.getData());
        assertNotNull(resp.getMessage(), "业务异常应通过 message 传回");
        assertTrue(resp.getMessage().contains("业务异常: boom"),
                "异常信息应包含业务异常内容，实际: " + resp.getMessage());

        channel.finish();
    }
}
