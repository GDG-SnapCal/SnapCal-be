package com.snapcal.snapcalbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.public-url}")
    private String publicUrl;

    public String upload(byte[] content, String userId, String originalFilename, String contentType) {
        String key = buildKey(userId, originalFilename);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content)
        );

        return publicUrl + "/" + bucket + "/" + key;
    }

    public void delete(String fileUrl) {
        String key = fileUrl.substring(fileUrl.indexOf(bucket) + bucket.length() + 1);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    private String buildKey(String userId, String originalFilename) {
        String ext = extractExtension(originalFilename);
        return userId + "/" + UUID.randomUUID() + ext;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
