package com.openevent.event.media;

import org.springframework.web.multipart.MultipartFile;

/** Nơi lưu ảnh; tách interface để test thay bằng bản giả, không gọi Cloudinary thật. */
public interface ImageStorage {

    /** Tệp đã được {@link ImageFiles#validate} kiểm tra. */
    UploadedImage upload(MultipartFile file, String folder);

    record UploadedImage(String url, String publicId) {
    }
}
