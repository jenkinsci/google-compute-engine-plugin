package com.google.jenkins.plugins.computeengine.client;

import com.google.jenkins.plugins.computeengine.ComputeEngineLinuxLauncher;
import com.trilead.ssh2.crypto.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertEquals;

//@RunWith(MockitoJUnitRunner.class)
public class ComputeEngineLinuxLauncherTest {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineLinuxLauncherTest.class.getName());
}
