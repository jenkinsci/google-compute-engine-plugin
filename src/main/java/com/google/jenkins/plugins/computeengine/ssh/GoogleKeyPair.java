package com.google.jenkins.plugins.computeengine.ssh;

import org.jclouds.ssh.SshKeys;

import java.util.Map;

public class GoogleKeyPair {

    private final String privateKey;
    private final String publicKey;
    private final String user;

    private GoogleKeyPair(String publicKey, String privateKey, String user) {
        this.publicKey = user + ":" + publicKey + " " + user;
        this.privateKey = privateKey;
        this.user = user;
    }

    public static GoogleKeyPair generate(String user) {
        Map<String, String> keys = SshKeys.generate();
        return new GoogleKeyPair(keys.get("public"), keys.get("private"), user);
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
}
