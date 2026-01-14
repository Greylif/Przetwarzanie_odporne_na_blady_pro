package com.example.pro_spring.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Klasa wykorzystywana do komunikacji HTTP pomiedzy serwerami Paxosa.
 */
public class HttpUtil {

  private static final RestTemplate rest = new RestTemplate();

  /**
   * Prywatny konstruktor zapobiegajacy tworzeniu instancji klasy.
   *
   * @throws UnsupportedOperationException zawsze
   */
  private HttpUtil() {
    throw new UnsupportedOperationException("");
  }


  /**
   * Wysyla zadanie POST bez tresci do podanego adresu URL.
   *
   * @param url adres endpointu HTTP
   * @return tresc odpowiedzi lub null
   */
  public static String postParams(String url) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.TEXT_PLAIN);
      HttpEntity<String> entity = new HttpEntity<>("", headers);

      ResponseEntity<String> resp =
          rest.exchange(url, HttpMethod.POST, entity, String.class);
      return resp.getBody();

    } catch (RestClientException e) {
      return null;
    }
  }

}
