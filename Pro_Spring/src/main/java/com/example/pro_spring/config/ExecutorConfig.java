package com.example.pro_spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


/**
 * Konfiguracja puli watkow uzywanej w aplikacji.
 *
 * Klasa definiuje bean ThreadPoolTaskExecutor, ktory jest wykorzystywany do asynchronicznego uruchamiania rund protokolu Paxos.
 *
 */
@Configuration
public class ExecutorConfig {

  /**
   * Tworzy i konfiguruje pule watkow dla zadan asynchronicznych.
   *
   * @return skonfigurowany ThreadPoolTaskExecutor
   */
  @Bean
  public ThreadPoolTaskExecutor executor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(16);
    exec.setMaxPoolSize(32);
    exec.initialize();
    return exec;
  }
}
