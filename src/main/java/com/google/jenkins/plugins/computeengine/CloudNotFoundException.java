package com.google.jenkins.plugins.computeengine;

import hudson.slaves.Cloud;

public class CloudNotFoundException  extends Exception {
    public CloudNotFoundException(String message) {super(message);}
}
