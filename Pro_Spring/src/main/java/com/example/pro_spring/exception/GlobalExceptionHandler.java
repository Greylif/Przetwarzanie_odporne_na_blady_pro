package com.example.pro_spring.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Globalny handler wyjątków dla aplikacji Spring MVC.
 * Przechwytuje wyjątki rzucane w warstwie kontrolerów
 * i mapuje je na odpowiednie widoki błędów oraz kody HTTP.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  private static final String ERRORTITLE = "errorTitle";

  private static final String ERRORMESSAGE = "errorMessage";

  private static final String ERROR = "error";

  /**
   * Obsługuje wyjątki HttpUtilException.
   *
   * @param ex wyjątek klienta
   * @param model model widoku
   * @return nazwa widoku błędu
   */
  @ExceptionHandler(HttpUtilException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleHttpUtilException(HttpUtilException ex, Model model) {
    model.addAttribute(ERRORTITLE, "Client Error");
    model.addAttribute(ERRORMESSAGE, ex.getMessage());
    return ERROR;
  }

  /**
   * Obsługuje wyjątki typu ServerException.
   *
   * @param ex wyjątek serwera
   * @param model model widoku
   * @return nazwa widoku błędu
   */
  @ExceptionHandler(ServerException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleServerException(ServerException ex, Model model) {
    model.addAttribute(ERRORTITLE, "Server Error");
    model.addAttribute(ERRORMESSAGE, ex.getMessage());
    return ERROR;
  }

  /**
   * Obsługuje wszystkie nieprzewidziane wyjątki.
   *
   * @param ex dowolny wyjątek
   * @param model model widoku
   * @return nazwa widoku błędu
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleUnexpected(Exception ex, Model model) {
    model.addAttribute(ERRORTITLE, "Unexpected Error");
    model.addAttribute(ERRORMESSAGE, ex.getMessage());
    return ERROR;
  }
}
