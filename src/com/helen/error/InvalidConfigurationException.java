package com.helen.error;

/** If Helen's configuration is wrong
 * —for example, if she is missing a required property in her configuration—
 *  she is fatally misconfigured and probably shouldn't run at all. */
public class InvalidConfigurationException extends RuntimeException {
  public InvalidConfigurationException(String msg) {
    super(msg);
  }
  public InvalidConfigurationException(String msg, Exception cause) {
    super(msg, cause);
  }
}
