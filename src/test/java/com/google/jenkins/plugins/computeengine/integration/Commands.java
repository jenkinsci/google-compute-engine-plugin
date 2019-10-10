package com.google.jenkins.plugins.computeengine.integration;

import lombok.Getter;

@Getter
public enum Commands {
  ECHO("echo %s", "echo %s"),
  EXIT("exit /b %s", "exit %s"),
  SLEEP("timeout /t %s", "sleep %s");

  private String windows;
  private String linux;

  Commands(String windows, String linux) {
    this.windows = windows;
    this.linux = linux;
  }
}
