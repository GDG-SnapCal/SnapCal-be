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

    private static final Set<String> VALID_CATEGORIES = Set.of("음식", "패션", "운동", "풍경", "일상", "미분류");
    private static final String FALLBACK_CATEGORY = "미분류";
    private static final String PROMPT =
            "이 이미지를 다음 카테고리 중 하나로 분류하고 JSON으로만 응답하세요.\n" +
            "- 음식: 음식, 요리, 식당, 카페 등\n" +
            "- 패션: 옷, 신발, 액세서리, 패션 아이템 등\n" +
            "- 운동: 스포츠, 헬스, 야외 운동 등\n" +
            "- 풍경: 자연, 도시, 여행지 풍경 등\n" +
            "- 일상: 사람의 일상적인 활동, 모임, 셀카 등\n" +
            "- 미분류: 동물, 사물, 문서, 스크린샷 등 위 카테고리에 해당하지 않는 경우\n" +
            "응답 형식 (다른 텍스트 없이 JSON만): {\"category\": \"카테고리명\", \"confidence\": 0.0~1.0}";

    public record ClassificationResult(String category, double confidence) {
        static ClassificationResult fallback() { return new ClassificationResult(FALLBACK_CATEGORY, 0.0); }
    }

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.base-url}")
    private String baseUrl;

    public ClassificationResult classify(byte[] imageBytes, String contentType) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = contentType != null ? contentType : "image/jpeg";
            String dataUrl = "data:" + mimeType + ";base64," + base64Image;

            String requestBody = objectMapper.writeValueAsString(new GPTRequest(
                    model,
                    List.of(new Message("user", List.of(
                            ImageContent.of(new ImageUrl(dataUrl)),
                            TextContent.of(PROMPT)
                    ))),
                    50
            ));

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("GPT 분류 실패: HTTP {}", response.code());
                    return ClassificationResult.fallback();
                }
                return parseResult(response.body().string());
            }
        } catch (Exception e) {
            log.warn("GPT 분류 중 오류 발생: {}", e.getMessage());
            return ClassificationResult.fallback();
        }
    }

    private ClassificationResult parseResult(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText().trim();
            log.debug("GPT 분류 응답: '{}'", content);

            JsonNode json = objectMapper.readTree(content);
            String category = json.path("category").asText("미분류").trim();
            double confidence = json.path("confidence").asDouble(0.0);

            if (!VALID_CATEGORIES.contains(category)) {
                log.warn("GPT가 알 수 없는 카테고리 반환: '{}' → 미분류 처리", category);
                return ClassificationResult.fallback();
            }
            return new ClassificationResult(category, confidence);
        } catch (Exception e) {
            log.warn("GPT 응답 파싱 실패: {}", e.getMessage());
            return ClassificationResult.fallback();
        }
    }

    // GPT 요청 레코드
    record GPTRequest(String model, List<Message> messages, int max_tokens) {}
    record Message(String role, List<Object> content) {}
    record ImageContent(String type, ImageUrl image_url) {
        static ImageContent of(ImageUrl url) { return new ImageContent("image_url", url); }
    }
    record TextContent(String type, String text) {
        static TextContent of(String text) { return new TextContent("text", text); }
    }
    record ImageUrl(String url) {}
}
