package com.feldsher;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.feldsher.user.mapper")
public class FeldsherApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeldsherApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    Feldsher 医疗AI问答系统启动成功!");
        System.out.println("    访问地址: http://localhost:8080");
        System.out.println("==========================================");
    }
}
