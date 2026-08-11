/**
 * 协议层：定义 RPC 通信协议格式与消息实体。
 *
 * <p>本包定义：
 * <ul>
 *   <li>{@link com.myrpc.core.protocol.RpcMessage} —— 协议消息包装（头部 + 数据体）</li>
 *   <li>{@link com.myrpc.core.protocol.MessageType} —— 消息类型枚举（请求/响应/心跳）</li>
 *   <li>协议常量（魔数、版本号、各字段长度）</li>
 * </ul>
 *
 * <p>对应学习文档：{@link /后端知识/中间件/04-自定义通信协议与编解码器}
 */
package com.myrpc.core.protocol;
