package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.Category;
import com.snapcal.snapcalbackend.domain.PhotoCategory;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.CategoryCreateRequest;
import com.snapcal.snapcalbackend.dto.request.CategoryPatchRequest;
import com.snapcal.snapcalbackend.dto.response.CategoryResponse;
import com.snapcal.snapcalbackend.repository.CategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final PhotoCategoryRepository photoCategoryRepository;

    /** 커스텀 카테고리 생성 */
    public CategoryResponse create(User user, CategoryCreateRequest request) {
        Category category = Category.builder()
                .user(user)
                .name(request.getName())
                .colorHex(request.getColorHex())
                .isDefault(false)
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    /** 커스텀 카테고리 이름/색상 수정 (기본 카테고리 불가) */
    public CategoryResponse update(User user, Integer categoryId, CategoryPatchRequest request) {
        Category category = findOwnedCustomCategory(user, categoryId);
        category.update(request.getName(), request.getColorHex());
        return CategoryResponse.from(category);
    }

    /** 커스텀 카테고리 삭제 — 해당 카테고리의 사진은 미분류로 이동 */
    public void delete(User user, Integer categoryId) {
        Category category = findOwnedCustomCategory(user, categoryId);

        Category unclassified = categoryRepository.findFirstByNameAndIsDefaultTrue("미분류")
                .orElseThrow(() -> new IllegalStateException("미분류 카테고리를 찾을 수 없습니다."));

        List<PhotoCategory> photoCategories = photoCategoryRepository.findAllByCategoryId(categoryId);
        photoCategories.forEach(pc -> pc.migrateToDefault(unclassified));

        categoryRepository.delete(category);
    }

    // ── private ──────────────────────────────────────────────────

    private Category findOwnedCustomCategory(User user, Integer categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."));

        if (category.isDefault()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "기본 카테고리는 수정/삭제할 수 없습니다.");
        }
        if (category.getUser() == null || !category.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 카테고리만 수정/삭제할 수 있습니다.");
        }

        return category;
    }
}
