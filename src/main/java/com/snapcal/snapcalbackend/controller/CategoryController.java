package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.common.ApiResponse;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.response.CategoryResponse;
import com.snapcal.snapcalbackend.repository.CategoryRepository;
import com.snapcal.snapcalbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        List<CategoryResponse> categories = categoryRepository
                .findByUserIdOrIsDefaultTrue(user.getId())
                .stream()
                .map(CategoryResponse::from)
                .toList();

        return ApiResponse.ok(categories);
    }
}
