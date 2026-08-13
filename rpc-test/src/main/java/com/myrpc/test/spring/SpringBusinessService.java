package com.myrpc.test.spring;

import com.myrpc.api.HelloService;
import com.myrpc.core.annotation.RpcReference;
import org.springframework.stereotype.Service;

/**
 * 阶段 12 Spring 注解驱动 demo —— Consumer 侧业务类。
 *
 * <p>{@code @RpcReference} 标在字段上，{@code RpcReferenceBeanPostProcessor}
 * 会自动生成代理对象注入进来。调 {@code helloService.sayHi()} 实际走网络。
 *
 * <p>对比阶段 8 直连 demo 的写法：
 * <pre>
 *   // 阶段 8 手动拿代理：
 *   HelloService service = new RpcClientProxy(client, HelloService.class).getProxy();
 *   service.sayHi("world");
 *
 *   // 阶段 12 注解驱动：
 *   @RpcReference
 *   private HelloService helloService;  // 自动注入代理
 * </pre>
 *
 * <p>使用方完全感知不到"这是个 RPC 代理"，就像用本地 {@code @Autowired} 一样。
 */
@Service
public class SpringBusinessService {

    @RpcReference
    private HelloService helloService;

    public String greet(String name) {
        // 这里调的看起来是本地方法，实际走网络到 Provider
        return helloService.sayHi(name);
    }


}
