package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.common.ApiResponse;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.CategoryUpdateRequest;
import com.snapcal.snapcalbackend.dto.request.DuplicateSelectRequest;
import com.snapcal.snapcalbackend.dto.response.PhotoUploadResponse;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.service.PhotoUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoUploadService photoUploadService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ApiResponse<PhotoUploadResponse> upload(
            @RequestParam("photos") List<MultipartFile> photos,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        PhotoUploadResponse response = photoUploadService.upload(photos, user);
        return ApiResponse.ok(response);
    }

    @PostMapping("/duplicates/select")
    public ApiResponse<Map<String, String>> selectDuplicates(
            @Valid @RequestBody DuplicateSelectRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        photoUploadService.selectFromDuplicates(request, user);
        return ApiResponse.ok(Map.of("message", "선택이 완료되었습니다."));
    }

    @PatchMapping("/{photoId}/category")
    public ApiResponse<Void> updateCategory(
            @PathVariable UUID photoId,
            @Valid @RequestBody CategoryUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        photoUploadService.updateCategory(photoId, request.getCategoryId(), user);
        return ApiResponse.ok(null);
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
    }
}
