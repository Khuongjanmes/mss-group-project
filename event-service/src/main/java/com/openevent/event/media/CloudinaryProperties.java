package com.openevent.event.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tài khoản Cloudinary lưu ảnh bìa sự kiện; để trống thì API tải ảnh trả 503. */
@ConfigurationProperties(prefix = "openevent.media.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {

    public boolean isConfigured() {
        return hasText(cloudName) && hasText(apiKey) && hasText(apiSecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
