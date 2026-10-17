package com.w6w.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.w6w")
public class W6wApplication {
    public static void main(String[] args) {
        SpringApplication.run(W6wApplication.class, args);
    }
}
