package com.openevent.event.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

/**
 * Bộ lọc danh sách sự kiện; điều kiện {@code null} hoặc rỗng bị bỏ qua.
 *
 * @param category slug của danh mục
 * @param from giữ sự kiện kết thúc sau mốc này
 * @param to giữ sự kiện bắt đầu trước mốc này
 */
public record EventSearch(String keyword, String category, String city, Instant from, Instant to,
        EventStatus status, UUID organizerId, UUID organizationId) {

    private static final char ESCAPE = '\\';

    Specification<Event> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (organizerId != null) {
                predicates.add(cb.equal(root.get("organizerId"), organizerId));
            }
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organization").get("id"), organizationId));
            }
            if (hasText(keyword)) {
                String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern, ESCAPE));
            }
            if (hasText(category)) {
                predicates.add(cb.equal(root.get("category").get("slug"), category.trim()));
            }
            if (hasText(city)) {
                predicates.add(cb.equal(cb.lower(root.get("venue").get("city")),
                        city.trim().toLowerCase(Locale.ROOT)));
            }
            if (from != null) {
                predicates.add(cb.greaterThan(root.get("endTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startTime"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** % và _ trong từ khóa được hiểu theo nghĩa đen. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
