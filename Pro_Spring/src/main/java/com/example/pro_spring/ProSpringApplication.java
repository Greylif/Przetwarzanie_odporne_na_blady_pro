package com.example.pro_spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Glowna klasa startowa aplikacji Spring Boot.
 *
 */
@SpringBootApplication
@EnableScheduling
public class ProSpringApplication {

  /**
   * Start aplikacji
   *
   * @param args argumenty linii polecen
   */
  public static void main(String[] args) {
    SpringApplication.run(ProSpringApplication.class, args);
  }
}
