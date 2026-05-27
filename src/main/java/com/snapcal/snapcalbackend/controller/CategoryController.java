package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.common.ApiResponse;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.CategoryCreateRequest;
import com.snapcal.snapcalbackend.dto.request.CategoryPatchRequest;
import com.snapcal.snapcalbackend.dto.response.CategoryResponse;
import com.snapcal.snapcalbackend.repository.CategoryRepository;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;

    /** 카테고리 목록 조회 — 시스템 기본 + 내 커스텀 */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        List<CategoryResponse> categories = categoryRepository
                .findByUserIdOrIsDefaultTrue(user.getId())
                .stream()
                .map(CategoryResponse::from)
                .toList();

        return ApiResponse.ok(categories);
    }

    /** 커스텀 카테고리 생성 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        return ApiResponse.ok(categoryService.create(user, request));
    }

    /** 커스텀 카테고리 이름/색상 수정 */
    @PatchMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Integer categoryId,
            @Valid @RequestBody CategoryPatchRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        return ApiResponse.ok(categoryService.update(user, categoryId, request));
    }

    /** 커스텀 카테고리 삭제 — 속한 사진은 미분류로 이동 */
    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> deleteCategory(
            @PathVariable Integer categoryId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        categoryService.delete(user, categoryId);
        return ApiResponse.ok(null);
    }

    // ── private ──────────────────────────────────────────────────

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
    }
}
