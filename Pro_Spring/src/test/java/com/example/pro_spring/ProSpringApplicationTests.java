package com.example.pro_spring;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProSpringApplicationTests {

  @Test
  void contextLoads() {
    //contextLoads
  }

  @Test
  @DisplayName("main app")
  void mainStartApplication() {
    assertThatCode(() ->
        ProSpringApplication.main(new String[] {})
    ).doesNotThrowAnyException();
  }

}
