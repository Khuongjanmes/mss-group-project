package com.openevent.identity.user;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

final class UserSpecifications {

    private static final char ESCAPE = '\\';

    private UserSpecifications() {
    }

    /** Tìm theo một phần email hoặc họ tên, không phân biệt hoa thường; % và _ được hiểu theo nghĩa đen. */
    static Specification<User> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("fullName")), pattern, ESCAPE));
    }

    static Specification<User> status(UserStatus status) {
        if (status == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
