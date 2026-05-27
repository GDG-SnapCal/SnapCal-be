package com.snapcal.snapcalbackend.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 이미지 분석 유틸
 * - pHash  : Perceptual Hash — 시각적 유사도 비교용 64비트 해시
 * - 선명도  : 라플라시안 분산 — 버스트샷 중 대표 사진 선택용
 */
@Slf4j
public class ImageAnalysisUtils {

    /** 해밍 거리 임계값 — 10 이하면 중복으로 판단 */
    public static final int DUPLICATE_THRESHOLD = 10;

    private static final int DCT_SIZE  = 32;  // DCT 변환 크기
    private static final int HASH_SIZE = 8;   // 상위 주파수 추출 크기 (8×8 = 64비트)

    // ── pHash ────────────────────────────────────────────────────────

    /**
     * Perceptual Hash 계산
     * 이미지를 32×32로 축소 → 그레이스케일 → DCT → 상위 8×8 주파수 → 64비트 해시
     */
    public static long computePHash(byte[] imageBytes) throws IOException {
        BufferedImage img = readImage(imageBytes);

        // 1. 32×32 리사이즈
        BufferedImage resized = resize(img, DCT_SIZE, DCT_SIZE);

        // 2. 그레이스케일 픽셀 행렬 추출
        double[][] pixels = toGrayscaleMatrix(resized, DCT_SIZE);

        // 3. 2D DCT 변환
        double[][] dct = applyDCT(pixels);

        // 4. 상위 좌측 8×8 (저주파 성분 64개) 추출
        double[] dctFlat = new double[HASH_SIZE * HASH_SIZE];
        for (int y = 0; y < HASH_SIZE; y++) {
            for (int x = 0; x < HASH_SIZE; x++) {
                dctFlat[y * HASH_SIZE + x] = dct[y][x];
            }
        }

        // 5. 평균 계산 (DC 성분 [0][0] 제외 — 전체 밝기 영향 배제)
        double avg = 0;
        for (int i = 1; i < dctFlat.length; i++) avg += dctFlat[i];
        avg /= (dctFlat.length - 1);

        // 6. 평균보다 크면 1, 이하면 0 → 64비트 해시 생성
        long hash = 0;
        for (int i = 0; i < dctFlat.length; i++) {
            if (dctFlat[i] > avg) hash |= (1L << i);
        }
        return hash;
    }

    /** 두 pHash 간 해밍 거리 (다른 비트 수) */
    public static int hammingDistance(long h1, long h2) {
        return Long.bitCount(h1 ^ h2);
    }

    /** 해밍 거리가 임계값 이하면 중복으로 판단 */
    public static boolean isDuplicate(long h1, long h2) {
        return hammingDistance(h1, h2) <= DUPLICATE_THRESHOLD;
    }

    // ── 선명도 ───────────────────────────────────────────────────────

    /**
     * 라플라시안 분산으로 선명도 점수 계산
     * 값이 높을수록 선명한 이미지 → 버스트샷 중 대표 사진 선택에 활용
     */
    public static double computeSharpness(byte[] imageBytes) throws IOException {
        BufferedImage img = readImage(imageBytes);

        int size = 64;  // 성능을 위해 64×64로 축소
        BufferedImage resized = resize(img, size, size);
        double[][] gray = toGrayscaleMatrix(resized, size);

        // 라플라시안 커널 [0,1,0 / 1,-4,1 / 0,1,0] — 엣지(윤곽) 강조
        int[][] kernel = {{0, 1, 0}, {1, -4, 1}, {0, 1, 0}};

        double sum = 0, sumSq = 0;
        int count = 0;

        for (int y = 1; y < size - 1; y++) {
            for (int x = 1; x < size - 1; x++) {
                double val = 0;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        val += kernel[ky + 1][kx + 1] * gray[y + ky][x + kx];
                    }
                }
                sum += val;
                sumSq += val * val;
                count++;
            }
        }

        double mean = sum / count;
        return (sumSq / count) - (mean * mean);  // 분산(variance)
    }

    // ── private 유틸 ─────────────────────────────────────────────────

    private static BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IOException("이미지 파일을 읽을 수 없습니다.");
        return img;
    }

    private static BufferedImage resize(BufferedImage img, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /** BufferedImage → 그레이스케일 2D 행렬 (BT.601 가중치 적용) */
    private static double[][] toGrayscaleMatrix(BufferedImage img, int size) {
        double[][] matrix = new double[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8)  & 0xFF;
                int b =  rgb        & 0xFF;
                matrix[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
            }
        }
        return matrix;
    }

    /** 2D DCT-II 변환 */
    private static double[][] applyDCT(double[][] pixels) {
        int n = pixels.length;
        double[][] dct = new double[n][n];
        double factor = 2.0 / n;

        for (int u = 0; u < n; u++) {
            double cu = (u == 0) ? 1.0 / Math.sqrt(2) : 1.0;
            for (int v = 0; v < n; v++) {
                double cv = (v == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                double sum = 0;
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        sum += pixels[x][y]
                                * Math.cos((2 * x + 1) * u * Math.PI / (2 * n))
                                * Math.cos((2 * y + 1) * v * Math.PI / (2 * n));
                    }
                }
                dct[u][v] = factor * cu * cv * sum;
            }
        }
        return dct;
    }
}
