package com.loopia.gameanimationshow;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@Slf4j
@MapperScan("com.loopia.gameanimationshow.mapper")
@EnableTransactionManagement
@EnableCaching
@EnableScheduling
public class GameAnimationShowApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameAnimationShowApplication.class, args);
        log.info("server start!");
    }
}
