package com.google.jenkins.plugins.computeengine.integration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Utility class for asserting log messages in Jenkins when using {@code RealJenkinsRule}.
 * If the test is only using {@code JenkinsRule}, then use the {@code LoggerRule} instead.
 * Note: Probably it is better to have these kind of utilities in the jenkins-test-harness itself instead, but at least as of now, such utilities are not available.
 */
public class RealJenkinsLogUtil {

    private static final Map<String, List<LogRecord>> logRecordsByClass = new HashMap<>();

    public static void setupLogRecorder(String className) {
        Logger logger = Logger.getLogger(className);
        logger.setLevel(Level.FINEST);
        logRecordsByClass.put(className, new ArrayList<>());
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (logRecordsByClass) {
                    logRecordsByClass.get(className).add(record);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        });
    }

    public static void assertLogContains(String className, String... texts) {
        var logRecords = logRecordsByClass.get(className);
        for (var text : texts) {
            assertThat(logRecords, hasItem(hasProperty("message", containsString(text))));
        }
    }

    public static void assertLogDoesNotContain(String className, String... texts) {
        var logRecords = logRecordsByClass.get(className);
        for (var text : texts) {
            assertThat(logRecords, not(hasItem(hasProperty("message", containsString(text)))));
        }
    }
}
