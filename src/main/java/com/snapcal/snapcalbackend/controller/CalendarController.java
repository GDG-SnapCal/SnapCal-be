package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.common.ApiResponse;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.CalendarSaveRequest;
import com.snapcal.snapcalbackend.dto.response.CalendarResponse;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final UserRepository userRepository;

    /**
     * 업로드 + 중복 검토가 끝난 뒤 최종적으로 캘린더에 사진을 저장.
     * PENDING → CONFIRMED 상태 전환.
     *
     * 중복 그룹이 없었던 경우에도 업로드 후 반드시 이 API를 호출해야 캘린더에 반영됨.
     */
    @PostMapping("/save")
    public ApiResponse<Map<String, String>> saveToCalendar(
            @Valid @RequestBody CalendarSaveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        calendarService.saveToCalendar(user.getId(), request);
        return ApiResponse.ok(Map.of("message", "캘린더에 저장되었습니다."));
    }

    @GetMapping
    public ApiResponse<CalendarResponse> getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (month < 1 || month > 12) {
            return ApiResponse.error("VALIDATION_ERROR", "month는 1~12 사이 값이어야 합니다.", "month");
        }

        User user = resolveUser(userDetails);
        CalendarResponse response = calendarService.getMonthlyCalendar(user.getId(), year, month);
        return ApiResponse.ok(response);
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
    }
}
