package com.google.jenkins.plugins.computeengine.ssh;

import org.jclouds.ssh.SshKeys;

import java.util.Map;

public class GoogleKeyPair {
    private static final String user = "jenkins";

    private final String privateKey;
    private final String publicKey;

    private GoogleKeyPair(String publicKey, String privateKey) {
        this.publicKey = user + ":" + publicKey + " " + user;
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    @Override
    public String toString() {
        return "Public key:\n" + publicKey + "\n\nPrivate key:\n" + privateKey;
    }

    public static GoogleKeyPair generate() throws Exception {
        Map<String, String> keys = SshKeys.generate();
        return new GoogleKeyPair(keys.get("public"), keys.get("private"));
    }
}
