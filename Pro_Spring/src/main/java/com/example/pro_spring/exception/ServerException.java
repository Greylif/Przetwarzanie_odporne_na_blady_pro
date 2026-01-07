package com.example.pro_spring.exception;

/**
 * Wyjątek reprezentujący błąd po stronie serwera.
 */
public class ServerException extends RuntimeException {

  /**
   * Tworzy nowy wyjątek serwera z podaną wiadomością.
   *
   * @param message opis błędu
   */
  public ServerException(String message) {
    super(message);
  }

  /**
   * Tworzy nowy wyjątek serwera z wiadomością oraz pierwotną przyczyną.
   *
   * @param message opis błędu
   * @param cause oryginalny wyjątek
   */
  public ServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
