package com.lifestyleplanner.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtLocalConfigurationTest {

    @Test
    void localDefaultsResolveAndLoadTheExistingRsaKeyPairWithoutStartingDatabase() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(EmptyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("local");
                    assertThat(environment.getPropertySources().stream()
                            .anyMatch(source -> source.getName().contains("application-local-secrets.properties")))
                            .isTrue();

                    JwtProperties properties = new JwtProperties(
                            environment.getProperty("app.security.jwt.issuer"),
                            Duration.ofMinutes(15),
                            Duration.ofDays(7),
                            environment.getProperty("app.security.jwt.private-key-path"),
                            environment.getProperty("app.security.jwt.public-key-path"));
                    JwtSecurityConfiguration.JwtKeyPair keys = new JwtSecurityConfiguration().jwtKeyPair(properties);

                    assertThat(keys.privateKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
                    assertThat(keys.publicKey().getModulus()).isEqualTo(keys.privateKey().getModulus());
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }
}
