package com.example.pro_spring.exception;

/**
 * Wyjatek reprezentujacy blad po stronie serwera.
 */
public class ServerException extends RuntimeException {

  /**
   * Tworzy nowy wyjatek serwera z podana wiadomoscia.
   *
   * @param message opis bledu
   */
  public ServerException(String message) {
    super(message);
  }

  /**
   * Tworzy nowy wyjatek serwera z wiadomoscia oraz pierwotna przyczyna.
   *
   * @param message opis bledu
   * @param cause oryginalny wyjatek
   */
  public ServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
