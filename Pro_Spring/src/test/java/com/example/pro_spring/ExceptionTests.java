package com.example.pro_spring;

import com.example.pro_spring.exception.GlobalExceptionHandler;
import com.example.pro_spring.exception.HttpUtilException;
import com.example.pro_spring.exception.ServerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExceptionTests {

  @Nested
  @DisplayName("HttpUtilException – testy konstruktora")
  class HttpUtilExceptionTests {

    @Test
    @DisplayName("Tworzy wyjatek tylko z wiadomoscia")
    void shouldCreateWithMessageOnly() {
      HttpUtilException ex = new HttpUtilException("Blad klienta");

      assertEquals("Blad klienta", ex.getMessage());
      assertNull(ex.getCause());
    }

    @Test
    @DisplayName("Tworzy wyjatek z wiadomoscia i przyczyna")
    void shouldCreateWithMessageAndCause() {
      Throwable cause = new IllegalArgumentException("Przyczyna");
      HttpUtilException ex = new HttpUtilException("Blad klienta", cause);

      assertEquals("Blad klienta", ex.getMessage());
      assertEquals(cause, ex.getCause());
    }
  }


  @Nested
  @DisplayName("ServerException – testy konstruktora")
  class ServerExceptionTests {

    @Test
    @DisplayName("Tworzy wyjatek tylko z wiadomoscia")
    void shouldCreateWithMessageOnly() {
      ServerException ex = new ServerException("Blad serwera");

      assertEquals("Blad serwera", ex.getMessage());
      assertNull(ex.getCause());
    }

    @Test
    @DisplayName("Tworzy wyjatek z wiadomoscia i przyczyna")
    void shouldCreateWithMessageAndCause() {
      Throwable cause = new RuntimeException("Przyczyna");
      ServerException ex = new ServerException("Blad serwera", cause);

      assertEquals("Blad serwera", ex.getMessage());
      assertEquals(cause, ex.getCause());
    }
  }


  @Nested
  @DisplayName("GlobalExceptionHandler – obsluga wyjatkow MVC")
  @WebMvcTest(TestExceptionController.class)
  @Import(GlobalExceptionHandler.class)
  class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Obsluguje HttpUtilException i zwraca 400")
    void shouldHandleHttpUtilException() throws Exception {
      mockMvc.perform(get("/http-util-error"))
          .andExpect(status().isBadRequest())
          .andExpect(view().name("error"))
          .andExpect(model().attribute("errorTitle", "Client Error"))
          .andExpect(model().attribute("errorMessage", "Blad klienta"));
    }

    @Test
    @DisplayName("Obsluguje ServerException i zwraca 500")
    void shouldHandleServerException() throws Exception {
      mockMvc.perform(get("/server-error"))
          .andExpect(status().isInternalServerError())
          .andExpect(view().name("error"))
          .andExpect(model().attribute("errorTitle", "Server Error"))
          .andExpect(model().attribute("errorMessage", "Blad serwera"));
    }

    @Test
    @DisplayName("Obsluguje nieoczekiwany wyjatek i zwraca 500")
    void shouldHandleUnexpectedException() throws Exception {
      mockMvc.perform(get("/unexpected-error"))
          .andExpect(status().isInternalServerError())
          .andExpect(view().name("error"))
          .andExpect(model().attribute("errorTitle", "Unexpected Error"))
          .andExpect(model().attribute("errorMessage", "Nieoczekiwany blad"));
    }
  }

  @Controller
  static class TestExceptionController {

    @GetMapping("/http-util-error")
    public String throwHttpUtilException() {
      throw new HttpUtilException("Blad klienta");
    }

    @GetMapping("/server-error")
    public String throwServerException() {
      throw new ServerException("Blad serwera");
    }

    @GetMapping("/unexpected-error")
    public String throwUnexpectedException() {
      throw new RuntimeException("Nieoczekiwany blad");
    }
  }
}
