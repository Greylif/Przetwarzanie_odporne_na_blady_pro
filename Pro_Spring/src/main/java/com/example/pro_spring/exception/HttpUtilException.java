package com.example.pro_spring.exception;

/**
 * Wyjatek reprezentujacy blad w HttpUtil.
 */
public class HttpUtilException extends RuntimeException {

  /**
   * Tworzy nowy wyjatek z podana wiadomoscia.
   *
   * @param message opis bledu
   */
  public HttpUtilException(String message) {
    super(message);
  }

  /**
   * Tworzy nowy wyjatek z wiadomoscia oraz pierwotna przyczyna.
   *
   * @param message opis bledua
   * @param cause oryginalny wyjatek
   */
  public HttpUtilException(String message, Throwable cause) {
    super(message, cause);
  }
}
