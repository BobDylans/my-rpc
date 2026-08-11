package com.myrpc.core.client;

import com.myrpc.api.dto.RpcResponse;
import com.myrpc.core.protocol.MessageType;
import com.myrpc.core.protocol.RpcMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端响应处理器 —— 收到 RpcResponse，按 requestId 配对到等待中的 Future。
 *
 * <p>与服务端的 {@code RpcRequestHandler} 对称：
 * <pre>
 * 服务端：Decoder → RpcRequestHandler（收请求，反射调用）
 * 客户端：Decoder → RpcResponseHandler（收响应，配对 Future）
 * </pre>
 *
 * <p>核心就一行逻辑：{@link UnprocessedRequests#complete}。
 * 调用线程此刻正阻塞在 {@code future.get()} 上，这里一 complete，它立刻醒过来拿到结果。
 *
 * <p>对应学习文档：{@link /后端知识/中间件/07-客户端-动态代理与Future异步} §1.4
 */
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(RpcResponseHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        if (msg.getMessageType() != MessageType.RESPONSE.getCode()) {
            log.debug("忽略非响应消息: {}", MessageType.of(msg.getMessageType()));
            return;
        }
        // 相当于从然后来的resp中读取对应的data
        RpcResponse response = (RpcResponse) msg.getData();
        // 配对：complete 内部会 remove，配对即清理
        // 当client获取到对应的数据后就会调用complete提取出信息并且提交
        // 这个时候proxy也能通过future获取到对应的信息
        UnprocessedRequests.complete(response.getRequestId(), response);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 连接断开：所有在途请求不可能再有响应了
        // 全部标记失败，让等待的调用线程立刻抛异常，而不是永久阻塞
        log.warn("连接断开，未完成请求 {} 个全部标记失败", UnprocessedRequests.pendingCount());
        UnprocessedRequests.failAll(new IllegalStateException("RPC 连接已断开"));
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端连接异常", cause);
        ctx.close();
    }
}
