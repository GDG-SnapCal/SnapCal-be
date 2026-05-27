package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.CategoryUpdateRequest;
import com.snapcal.snapcalbackend.dto.request.DuplicateSelectRequest;
import com.snapcal.snapcalbackend.dto.response.PhotoDetailResponse;
import com.snapcal.snapcalbackend.dto.response.PhotoUploadResponse;
import com.snapcal.snapcalbackend.dto.response.UploadStatusResponse;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.service.PhotoUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    public PhotoUploadResponse upload(
            @RequestParam("photos") List<MultipartFile> photos,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        return photoUploadService.upload(photos, user);
    }

    @GetMapping("/upload/{uploadId}/status")
    public UploadStatusResponse getUploadStatus(
            @PathVariable String uploadId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveUser(userDetails);
        return photoUploadService.getUploadStatus(uploadId, user);
    }

    @PostMapping("/duplicates/select")
    public Map<String, String> selectDuplicates(
            @Valid @RequestBody DuplicateSelectRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        photoUploadService.selectFromDuplicates(request, user);
        return Map.of("message", "Selection completed.");
    }

    @PatchMapping("/{photoId}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateCategory(
            @PathVariable UUID photoId,
            @Valid @RequestBody CategoryUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        photoUploadService.updateCategory(photoId, request.getCategoryId(), user);
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        photoUploadService.delete(photoId, user);
    }

    @PostMapping("/{photoId}/edit")
    public Map<String, String> editPhoto(
            @PathVariable UUID photoId,
            @RequestBody(required = false) Map<String, Object> options,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveUser(userDetails);
        String url = photoUploadService.getEditablePhotoUrl(photoId, user);
        return Map.of(
                "photoId", photoId.toString(),
                "editedUrl", url
        );
    }

    @GetMapping("/{photoId}")
    public PhotoDetailResponse getPhoto(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);

        var photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."));

        var photoCategory = photoCategoryRepository.findByPhotoId(photoId).orElse(null);

        return PhotoDetailResponse.of(photo, photoCategory);
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("User not found."));
    }
}
