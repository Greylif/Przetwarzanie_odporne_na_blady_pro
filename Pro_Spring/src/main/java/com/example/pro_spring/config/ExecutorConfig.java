package com.example.pro_spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

  @Bean
  public ThreadPoolTaskExecutor executor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(16);
    exec.setMaxPoolSize(32);
    exec.initialize();
    return exec;
  }
}
