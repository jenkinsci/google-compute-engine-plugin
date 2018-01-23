package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.trilead.ssh2.crypto.Base64;
import hudson.model.TaskListener;

import java.io.IOException;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;

import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.NoSuchAlgorithmException;

public class ComputeEngineLinuxLauncher extends ComputeEngineComputerLauncher {
    public ComputeEngineLinuxLauncher(String cloudName, Operation insertOperation) {
        super(cloudName, insertOperation);
    }

    protected void launch(ComputeEngineComputer computer, TaskListener listener, Instance inst)
            throws IOException, InterruptedException {
        //TODO: SSH stuff
    }

    public KeyPair generateKeys() throws NoSuchAlgorithmException {
        // Get the public/private key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        return kpg.genKeyPair();
    }
}
