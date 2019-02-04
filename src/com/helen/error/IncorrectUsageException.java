package com.helen.error;

public class IncorrectUsageException extends Exception {
  public IncorrectUsageException(String msg) {
    super(msg);
  }
  public IncorrectUsageException(String msg, Exception cause) {
    super(msg, cause);
  }
}
