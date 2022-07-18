package com.google.jenkins.plugins.computeengine;

/** Instance did not launch before the specified timeout. */
public class LaunchTimeoutException extends Exception {
  public LaunchTimeoutException(String errorMessage) {
    super(errorMessage);
  }
}
