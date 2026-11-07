package com.openevent.event.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.openevent.common.web.ApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Tải ảnh lên Cloudinary bằng signed upload (theo openevent): ký {@code folder} và {@code timestamp} bằng API secret,
 * secret không rời server.
 */
@Slf4j
@Component
@EnableConfigurationProperties(CloudinaryProperties.class)
class CloudinaryImageStorage implements ImageStorage {

    private final CloudinaryProperties properties;
    private final RestClient restClient = RestClient.create();

    CloudinaryImageStorage(CloudinaryProperties properties) {
        this.properties = properties;
    }

    @Override
    public UploadedImage upload(MultipartFile file, String folder) {
        if (!properties.isConfigured()) {
            throw unavailable("IMAGE_STORAGE_NOT_CONFIGURED", "Kho lưu trữ ảnh chưa được cấu hình");
        }
        long timestamp = Instant.now().getEpochSecond();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("api_key", properties.apiKey());
        body.add("timestamp", Long.toString(timestamp));
        body.add("folder", folder);
        body.add("signature", sign("folder=" + folder + "&timestamp=" + timestamp + properties.apiSecret()));
        String filename = safeFilename(file.getOriginalFilename());
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
        fileHeaders.setContentDispositionFormData("file", filename);
        try {
            body.add("file", new HttpEntity<>(new NamedBytes(file.getBytes(), filename), fileHeaders));
            Map<String, Object> response = restClient.post()
                    .uri("https://api.cloudinary.com/v1_1/{cloudName}/image/upload", properties.cloudName())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.get("secure_url") == null || response.get("public_id") == null) {
                throw unavailable("IMAGE_UPLOAD_FAILED", "Kho lưu trữ ảnh trả về dữ liệu không hợp lệ");
            }
            return new UploadedImage(response.get("secure_url").toString(), response.get("public_id").toString());
        } catch (IOException | RestClientException ex) {
            log.warn("Tải ảnh lên Cloudinary lỗi: {}", ex.getMessage());
            throw unavailable("IMAGE_UPLOAD_FAILED", "Không thể tải ảnh lên kho lưu trữ");
        }
    }

    static String sign(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 không có sẵn", ex);
        }
    }

    private static String safeFilename(String original) {
        return original == null || original.isBlank() ? "event-image" : original.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    /** Cloudinary cần tên tệp trong phần multipart. */
    private static final class NamedBytes extends ByteArrayResource {

        private final String filename;

        private NamedBytes(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
