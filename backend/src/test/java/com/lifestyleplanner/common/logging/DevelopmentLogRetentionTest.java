package com.lifestyleplanner.common.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;

class DevelopmentLogRetentionTest {
    @TempDir
    Path logDirectory;

    @Test
    void localLogbackConfigurationUsesNinetyDayHistoryAndCorrelationIds() throws Exception {
        try (var input = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertTrue(input != null, "Logback configuration should be present on the classpath");
            String configuration = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            assertTrue(configuration.contains("<springProfile name=\"local\">"));
            assertTrue(configuration.contains("<maxHistory>90</maxHistory>"));
            assertTrue(configuration.contains("<cleanHistoryOnStart>true</cleanHistoryOnStart>"));
            assertTrue(configuration.contains("[cid=%X{correlationId:-}]"));
        }
    }

    @Test
    void developmentRollingPolicyDeletesArchivesOlderThanNinetyDays() throws Exception {
        String oldDate = LocalDate.now().minusDays(100).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path expiredArchive = logDirectory.resolve("application." + oldDate + ".0.log.gz");
        Files.writeString(expiredArchive, "synthetic log archive");

        LoggerContext context = new LoggerContext();
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setFile(logDirectory.resolve("application.log").toString());

        SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
        policy.setContext(context);
        policy.setParent(appender);
        policy.setFileNamePattern(logDirectory.resolve("application.%d{yyyy-MM-dd}.%i.log.gz").toString());
        policy.setMaxFileSize(FileSize.valueOf("1MB"));
        policy.setMaxHistory(90);
        policy.setTotalSizeCap(FileSize.valueOf("2GB"));
        policy.setCleanHistoryOnStart(true);
        policy.start();
        appender.setRollingPolicy(policy);
        appender.setTriggeringPolicy(policy);
        appender.start();

        try {
            for (int attempt = 0; attempt < 50 && Files.exists(expiredArchive); attempt++) {
                Thread.sleep(20);
            }

            assertFalse(Files.exists(expiredArchive), "archives older than 90 days should be deleted on startup");
            assertTrue(Files.isDirectory(logDirectory));
        } finally {
            appender.stop();
            context.stop();
        }
    }
}
