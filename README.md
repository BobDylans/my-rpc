# my-rpc

手写 RPC 框架 · 学习项目（Netty + Kryo + Zookeeper）

对标 [JavaGuide guide-rpc-framework](https://github.com/Snailclimb/guide-rpc-framework)，作为学习中间件的起点。

## 模块结构

| 模块 | 职责 |
|------|------|
| `rpc-api` | 对外接口定义 + 通用 DTO（`RpcRequest`/`RpcResponse`），零业务依赖 |
| `rpc-core` | 框架核心：协议、序列化、编解码、代理、注册中心抽象、负载均衡、服务端/客户端启动 |
| `rpc-provider` | 服务端：启动 ServerBootstrap + 服务注册 + 注解驱动暴露 |
| `rpc-consumer` | 客户端：动态代理 + 服务发现 + 负载均衡 + 注解驱动注入 |
| `rpc-test` | 集成测试：定义 `HelloService`，跑端到端 RPC 调用闭环 |

## 环境要求

- Java 21
- Maven 3.9+
- Zookeeper 3.9+（阶段 9 起需要，本地 Docker 启动即可）

## 构建

```bash
mvn clean compile
```

## 学习进度

详见 `/home/ivan/ProjectS/obsidian-data/后端知识/中间件/00-RPC框架学习路线总览.md`

- [x] 阶段 3 · 项目骨架与 Maven 多模块
- [x] 阶段 4 · 自定义通信协议与编解码器
- [x] 阶段 5 · 序列化层
- [x] 阶段 6 · 服务端（Netty + 反射调用）
- [x] 阶段 7 · 客户端（动态代理 + Future 异步）
- [x] 阶段 8 · 端到端打通（客户端集成测试验证）
- [x] 阶段 9 · 注册中心与服务治理（部分）
  - [x] 服务注册（Provider 侧）
  - [x] 服务发现 + 缓存 + Watcher（Consumer 侧）
  - [x] 随机负载均衡
  - [ ] 轮询 / 一致性哈希负载均衡（阶段 11 剩余）
- [ ] 阶段 12-14 · 健壮性增强
