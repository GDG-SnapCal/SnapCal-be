package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.common.ApiResponse;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.CategoryUpdateRequest;
import com.snapcal.snapcalbackend.dto.request.DuplicateSelectRequest;
import com.snapcal.snapcalbackend.dto.request.SetRepresentativeRequest;
import com.snapcal.snapcalbackend.dto.response.PhotoDetailResponse;
import com.snapcal.snapcalbackend.dto.response.PhotoImageUpdateResponse;
import com.snapcal.snapcalbackend.dto.response.PhotoListResponse;
import com.snapcal.snapcalbackend.dto.response.UploadInitiatedResponse;
import com.snapcal.snapcalbackend.dto.response.UploadStatusResponse;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.service.PhotoUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoUploadService photoUploadService;
    private final PhotoRepository photoRepository;
    private final PhotoCategoryRepository photoCategoryRepository;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<UploadInitiatedResponse> upload(
            @RequestParam("photos") List<MultipartFile> photos,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        return ApiResponse.ok(photoUploadService.upload(photos, user));
    }

    @GetMapping("/upload/{uploadId}/status")
    public ResponseEntity<ApiResponse<UploadStatusResponse>> getUploadStatus(
            @PathVariable String uploadId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        UploadStatusResponse status = photoUploadService.getUploadStatus(uploadId, user);
        HttpStatus httpStatus = "processing".equals(status.getStatus())
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(ApiResponse.ok(status));
    }

    @GetMapping
    public ApiResponse<List<PhotoListResponse>> getPhotos(
            @RequestParam LocalDate date,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        List<PhotoListResponse> photos = photoUploadService.getPhotosByDate(date, category, user);
        return ApiResponse.ok(photos);
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

    @PatchMapping("/{photoId}/representative")
    public ApiResponse<Void> setRepresentative(
            @PathVariable UUID photoId,
            @RequestBody(required = false) SetRepresentativeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        String categoryName = (request != null) ? request.getCategory() : null;
        photoUploadService.setRepresentative(photoId, categoryName, user);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{photoId}/image")
    public ApiResponse<PhotoImageUpdateResponse> updateImage(
            @PathVariable UUID photoId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        return ApiResponse.ok(photoUploadService.updateImage(photoId, file, user));
    }

    @DeleteMapping("/{photoId}")
    public ApiResponse<Void> deletePhoto(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        photoUploadService.delete(photoId, user);
        return ApiResponse.ok(null);
    }

    @Transactional(readOnly = true)
    @GetMapping("/{photoId}")
    public ApiResponse<PhotoDetailResponse> getPhoto(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);

        var photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));

        var photoCategory = photoCategoryRepository.findByPhotoId(photoId).orElse(null);

        return ApiResponse.ok(PhotoDetailResponse.of(photo, photoCategory));
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
    }
}
