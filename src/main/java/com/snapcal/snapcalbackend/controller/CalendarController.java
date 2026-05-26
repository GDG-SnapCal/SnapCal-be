package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.common.ApiResponse;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.response.CalendarResponse;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<CalendarResponse> getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (month < 1 || month > 12) {
            return ApiResponse.error("VALIDATION_ERROR", "month는 1~12 사이 값이어야 합니다.", "month");
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        CalendarResponse response = calendarService.getMonthlyCalendar(user.getId(), year, month);
        return ApiResponse.ok(response);
    }
}
