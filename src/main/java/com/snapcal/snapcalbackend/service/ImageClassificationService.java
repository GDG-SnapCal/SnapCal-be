package com.snapcal.snapcalbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageClassificationService {

    private static final Set<String> VALID_CATEGORIES = Set.of("음식", "패션", "운동", "여행", "일상", "미분류");
    private static final String FALLBACK_CATEGORY = "미분류";
    private static final String PROMPT =
            "이 이미지를 다음 카테고리 중 하나로 분류하세요: 음식, 패션, 운동, 여행, 일상, 미분류\n" +
            "카테고리 이름만 반환하세요. 다른 텍스트는 포함하지 마세요.";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.base-url}")
    private String baseUrl;

    public String classify(byte[] imageBytes, String contentType) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = contentType != null ? contentType : "image/jpeg";
            String dataUrl = "data:" + mimeType + ";base64," + base64Image;

            String requestBody = objectMapper.writeValueAsString(new GPTRequest(
                    model,
                    List.of(new Message("user", List.of(
                            new ImageContent(new ImageUrl(dataUrl)),
                            new TextContent(PROMPT)
                    ))),
                    10
            ));

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("GPT 분류 실패: HTTP {}", response.code());
                    return FALLBACK_CATEGORY;
                }
                return parseCategory(response.body().string());
            }
        } catch (Exception e) {
            log.warn("GPT 분류 중 오류 발생: {}", e.getMessage());
            return FALLBACK_CATEGORY;
        }
    }

    private String parseCategory(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText().trim();
            return VALID_CATEGORIES.contains(content) ? content : FALLBACK_CATEGORY;
        } catch (Exception e) {
            return FALLBACK_CATEGORY;
        }
    }

    // GPT 요청 레코드
    record GPTRequest(String model, List<Message> messages, int max_tokens) {}
    record Message(String role, List<Object> content) {}
    record ImageContent(ImageUrl image_url) { String type() { return "image_url"; } }
    record TextContent(String text) { String type() { return "text"; } }
    record ImageUrl(String url) {}
}
