package com.example.pro_spring.exception;

/**
 * Wyjątek reprezentujący błąd w HttpUtil.
 */
public class HttpUtilException extends RuntimeException {

  /**
   * Tworzy nowy wyjątek z podaną wiadomością.
   *
   * @param message opis błędu
   */
  public HttpUtilException(String message) {
    super(message);
  }

  /**
   * Tworzy nowy wyjątek z wiadomością oraz pierwotną przyczyną.
   *
   * @param message opis błędua
   * @param cause oryginalny wyjątek
   */
  public HttpUtilException(String message, Throwable cause) {
    super(message, cause);
  }
}
