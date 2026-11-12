package com.openevent.event.category.dto;

import java.util.UUID;

import com.openevent.event.category.Category;

public record CategoryResponse(UUID id, String name, String slug, String description) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription());
    }
}
