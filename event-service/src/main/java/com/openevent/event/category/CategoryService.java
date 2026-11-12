package com.openevent.event.category;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.web.ApiException;
import com.openevent.event.category.dto.CategoryRequest;
import com.openevent.event.category.dto.CategoryResponse;
import com.openevent.event.events.EventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categories;
    private final EventRepository events;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        String slug = Slugs.fromName(name);
        if (categories.findBySlug(slug).isPresent()) {
            throw exists();
        }
        return CategoryResponse.from(categories.save(Category.create(name, slug, request.description())));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = require(id);
        String name = request.name().trim();
        String slug = Slugs.fromName(name);
        if (categories.findBySlug(slug).filter(other -> !other.getId().equals(id)).isPresent()) {
            throw exists();
        }
        category.update(name, slug, request.description());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID id) {
        Category category = require(id);
        if (events.existsByCategoryId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "Danh mục đang được sự kiện sử dụng");
        }
        categories.delete(category);
    }

    public Category require(UUID id) {
        return categories.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy danh mục"));
    }

    private static ApiException exists() {
        return new ApiException(HttpStatus.CONFLICT, "CATEGORY_EXISTS", "Đã có danh mục cùng tên");
    }
}
