package com.google.jenkins.plugins.computeengine;

import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;

public class GoogleKeyPairTest {
    private static final Logger LOGGER = Logger.getLogger(GoogleKeyPairTest.class.getName());

    @Test
    public void KeyPairGeneration() throws Exception {
        GoogleKeyPair gkp = GoogleKeyPair.generate();
        assertNotNull(gkp.toString());
    }
}

