package com.example.pro_spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pro_spring.model.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Test Promise")
class PromiseTests {

  @Test
  void createPromise() {
    Promise p = new Promise(true, 10, 42);

    assertThat(p.promised()).isTrue();
    assertThat(p.acceptedProposal()).isEqualTo(10);
    assertThat(p.acceptedValue()).isEqualTo(42);
  }
}
