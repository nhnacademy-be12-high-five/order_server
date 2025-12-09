package com.nhnacademy.order_server.config;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookClientConfig {
    @Bean
    public Request.Options options() {
        return new Request.Options(3000, 5000); 
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 1000, 3);
    }
}
