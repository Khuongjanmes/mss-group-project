package com.openevent.event.category;

import java.text.Normalizer;
import java.util.Locale;

/** Sinh slug từ tên tiếng Việt: "Hội thảo & Tọa đàm" → "hoi-thao-toa-dam". */
final class Slugs {

    private static final int MAX_LENGTH = 120;

    private Slugs() {
    }

    static String fromName(String name) {
        String ascii = Normalizer.normalize(name.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "danh-muc" : slug;
    }
}
