package com.example.pro_spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pro_spring.exception.HttpUtilException;
import com.example.pro_spring.util.HttpUtil;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("Testy HttpUtil")
class HttpUtilTest {

  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() throws Exception {
    RestTemplate restTemplate = getRestTemplate();
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  @DisplayName("postParams OK")
  void postParamsSuccess() {
    mockServer.expect(requestTo("http://test/url"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("RESPONSE_OK", MediaType.TEXT_PLAIN));

    String result = HttpUtil.postParams("http://test/url");

    mockServer.verify();
    assertThat(result).isEqualTo("RESPONSE_OK");
  }

  @Test
  @DisplayName("postParams – zwraca null przy bledzie HTTP")
  void postParamsReturnsNullOnError() {

    mockServer.expect(requestTo("http://fail/url"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    String result = HttpUtil.postParams("http://fail/url");

    assertThat(result).isNull();

    mockServer.verify();
  }


  @Test
  @DisplayName("Konstruktor rzuca wyjatek")
  void constructorException() throws Exception {
    var constructor = HttpUtil.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    assertThrows(
        InvocationTargetException.class,
        constructor::newInstance
    );
  }



  private RestTemplate getRestTemplate() throws Exception {
    Field field = HttpUtil.class.getDeclaredField("rest");
    field.setAccessible(true);
    return (RestTemplate) field.get(null);
  }
}
