package com.openevent.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openevent.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword) {
}
