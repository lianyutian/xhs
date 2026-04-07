package com.lianyutian.xhs.user;

import com.lianyutian.xhs.user.config.SecurityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SecurityProperties.class)
@MapperScan(basePackages = "com.lianyutian.xhs.user.repository.mybatis")
public class UserServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
