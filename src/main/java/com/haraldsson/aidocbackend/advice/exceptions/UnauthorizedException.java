package com.haraldsson.aidocbackend.advice.exceptions;

public class UnauthorizedException extends BusinessException {
  public UnauthorizedException(String message) {
    super(message, "UNAUTHORIZED");
  }
}