package com.example.pro_spring.util;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class HttpUtil {

  private static final RestTemplate rest = new RestTemplate();

  public static String post(String url, String body, int timeoutMs) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.TEXT_PLAIN);
      HttpEntity<String> e = new HttpEntity<>(body, headers);

      ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, e, String.class);
      return resp.getBody();
    } catch (Exception e) {
      return null;
    }
  }
}
