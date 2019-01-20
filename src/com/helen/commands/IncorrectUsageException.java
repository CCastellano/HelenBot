package com.helen.commands;

public class IncorrectUsageException extends Exception {
  public IncorrectUsageException(String msg) {
    super(msg);
  }
}
