package com.openevent.event.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import com.openevent.common.web.ApiException;

/**
 * Kiểm tra ảnh tải lên (theo openevent): tối đa 5 MB, JPG/PNG/WEBP, và vài byte đầu phải khớp định dạng khai báo
 * để không nhận tệp khác đổi đuôi.
 */
public final class ImageFiles {

    static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private ImageFiles() {
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("IMAGE_EMPTY", "Vui lòng chọn ảnh cần tải lên");
        }
        if (file.getSize() > MAX_SIZE) {
            throw badRequest("IMAGE_TOO_LARGE", "Dung lượng ảnh không được vượt quá 5 MB");
        }
        if (!TYPES.contains(file.getContentType())) {
            throw badRequest("IMAGE_TYPE_NOT_SUPPORTED", "Chỉ hỗ trợ ảnh JPG, PNG hoặc WEBP");
        }
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException ex) {
            throw badRequest("IMAGE_CONTENT_INVALID", "Không đọc được tệp ảnh");
        }
        if (!matches(header, file.getContentType())) {
            throw badRequest("IMAGE_CONTENT_INVALID", "Nội dung tệp không đúng định dạng ảnh đã khai báo");
        }
    }

    private static boolean matches(byte[] h, String type) {
        return switch (type) {
            case "image/jpeg" -> h.length >= 3 && u(h[0]) == 0xFF && u(h[1]) == 0xD8 && u(h[2]) == 0xFF;
            case "image/png" -> h.length >= 8 && u(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && u(h[4]) == 0x0D && u(h[5]) == 0x0A && u(h[6]) == 0x1A && u(h[7]) == 0x0A;
            case "image/webp" -> h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
    }

    private static int u(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
