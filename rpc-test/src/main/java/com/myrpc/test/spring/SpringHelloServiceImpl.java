package com.myrpc.test.spring;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.myrpc.api.HelloService;
import com.myrpc.core.annotation.RpcService;

/**
 * 阶段 12 Spring 注解驱动 demo —— Provider 侧。
 *
 * <p>标 {@code @RpcService} 后，{@code RpcServiceBeanPostProcessor}
 * 会自动把这个 bean 注册到 {@code RpcServer}，暴露为 RPC 服务。
 *
 * <p>对比阶段 8 直连 demo 的写法：
 * <pre>
 *   // 阶段 8 手动注册：
 *   server.registerService(HelloService.class, new HelloServiceImpl());
 *
 *   // 阶段 12 注解驱动：
 *   @RpcService
 *   public class HelloServiceImpl implements HelloService { ... }
 *   // BeanPostProcessor 自动扫描注册，不用写注册代码
 * </pre>
 */
@RpcService
public class SpringHelloServiceImpl implements HelloService {

    @Override
    public String sayHi(String name) {
        // lambda语法学习
        List<Student> students = Arrays.asList(
    new Student("Alice", 22, 85.5),
          new Student("Bob", 19, 72.0),
          new Student("Charlie", 25, 91.0),
          new Student("Diana", 20, 68.5),
          new Student("Ethan", 23, 79.0)
        );

        Collections.sort(students, new Comparator<Student>() {
            @Override
            public int compare(Student s1, Student s2) {
            return Integer.compare(s1.getAge(), s2.getAge());
            }
        });
        return "Hi, " + name + " (from Spring RPC server)";
    }

}
