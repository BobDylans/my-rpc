/**
 * 编解码层：Netty 的 ChannelHandler，负责对象 ↔ 字节流互转。
 *
 * <p>本包实现：
 * <ul>
 *   <li>{@code RpcMessageEncoder} —— 出站：{@code RpcMessage → ByteBuf}，继承 {@code MessageToByteEncoder}</li>
 *   <li>{@code RpcMessageDecoder} —— 入站：{@code ByteBuf → RpcMessage}，继承 {@code ByteToMessageDecoder}</li>
 * </ul>
 *
 * <p>解码器核心：按协议头长度字段读取，解决 TCP 粘包/半包。
 * 对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器}
 */
package com.myrpc.core.codec;
