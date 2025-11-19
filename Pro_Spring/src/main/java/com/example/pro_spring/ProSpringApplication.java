package com.example.pro_spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProSpringApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProSpringApplication.class, args);
  }
}
