package com.myrpc.test;

import com.myrpc.api.HelloService;

/**
 * HelloService 实现 —— 阶段 06 端到端测试用。
 *
 * <p>放在 {@code rpc-test} 模块：这里是"业务方"，框架代码在 rpc-core。
 * 实现类由服务端注册进 {@code ServiceRegistry}，消费者通过 RPC 调用它。
 */
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHi(String name) {
        return "Hi, " + name + " (from RPC server)";
    }
}
