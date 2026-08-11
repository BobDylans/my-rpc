package com.myrpc.api;

/**
 * 示例服务接口 —— 用于端到端测试（阶段 08）。
 *
 * <p>这个接口放在 {@code rpc-api} 模块，因为它要同时被
 * {@code rpc-provider}（实现方）和 {@code rpc-consumer}（调用方）依赖。
 * 这也是 {@code rpc-api} 单独成模块的根本原因：避免 consumer/provider 互相依赖。
 */
public interface HelloService {

    /**
     * @param name 调用者名字
     * @return 问候语
     */
    String sayHi(String name);
}
