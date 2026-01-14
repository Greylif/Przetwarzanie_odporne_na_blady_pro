package com.example.pro_spring.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Globalny handler wyjatkow.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  private static final String ERRORTITLE = "errorTitle";

  private static final String ERRORMESSAGE = "errorMessage";

  private static final String ERROR = "error";

  /**
   * Obsluguje wyjatki HttpUtilException.
   *
   * @param ex wyjatek klienta
   * @param model model widoku
   * @return nazwa widoku bledu
   */
  @ExceptionHandler(HttpUtilException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleHttpUtilException(HttpUtilException ex, Model model) {
    model.addAttribute(ERRORTITLE, "Client Error");
    model.addAttribute(ERRORMESSAGE, ex.getMessage());
    return ERROR;
  }

  /**
   * Obsluguje wyjatki typu ServerException.
   *
   * @param ex wyjatek serwera
   * @param model model widoku
   * @return nazwa widoku bledu
   */
  @ExceptionHandler(ServerException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleServerException(ServerException ex, Model model) {
    model.addAttribute(ERRORTITLE, "Server Error");
    model.addAttribute(ERRORMESSAGE, ex.getMessage());
    return ERROR;
  }

  /**
   * Obsluguje wszystkie nieprzewidziane wyjatki.
   *
   * @param ex dowolny wyjatek
   * @param model model widoku
   * @return nazwa widoku bledu
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleUnexpected(Exception ex, Model model) {
    model.addAttribute(ERRORTITLE, "Unexpected Error");
    model.addAttribute(ERRORMESSAGE, ex.getMessage());
    return ERROR;
  }
}
