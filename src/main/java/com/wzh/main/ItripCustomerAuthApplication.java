package com.wzh.main;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.wzh.controller")
@EnableDubboConfiguration
public class ItripCustomerAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItripCustomerAuthApplication.class, args);
    }

}
