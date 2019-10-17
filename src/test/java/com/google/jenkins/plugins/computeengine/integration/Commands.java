package com.google.jenkins.plugins.computeengine.integration;

import lombok.Getter;

/**
 * An enum defining commands that can be run on both linux and windows agents, with corresponding
 * String format values for each platform.
 */
@Getter
public enum Commands {
  ECHO("echo %s", "echo %s"),
  EXIT("exit %s", "exit %s"),
  SLEEP("sleep -s %s", "sleep %s");

  private String windows;
  private String linux;

  Commands(String windows, String linux) {
    this.windows = windows;
    this.linux = linux;
  }
}
