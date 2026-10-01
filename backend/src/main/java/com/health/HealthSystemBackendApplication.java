package com.health;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HealthSystemBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthSystemBackendApplication.class, args);
    }

}
