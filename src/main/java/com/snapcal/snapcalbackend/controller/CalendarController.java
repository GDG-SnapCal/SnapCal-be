package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.CalendarSaveRequest;
import com.snapcal.snapcalbackend.dto.response.CalendarResponse;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final UserRepository userRepository;

    @PostMapping("/save")
    public Map<String, String> saveToCalendar(
            @Valid @RequestBody CalendarSaveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = resolveUser(userDetails);
        calendarService.saveToCalendar(user.getId(), request);
        return Map.of("message", "Saved to calendar.");
    }

    @GetMapping
    public CalendarResponse getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be between 1 and 12.");
        }

        User user = resolveUser(userDetails);
        return calendarService.getMonthlyCalendar(user.getId(), year, month);
    }

    @PostMapping("/export")
    public Map<String, String> exportCalendar(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        resolveUser(userDetails);
        return Map.of(
                "imageUrl", "https://example.com/snapcal/export-placeholder.png",
                "expiresAt", OffsetDateTime.now().plusHours(1).toString()
        );
    }

    @PostMapping("/share/link")
    public Map<String, String> createShareLink(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        resolveUser(userDetails);
        return Map.of(
                "shareUrl", "https://snapcal.app/share/placeholder",
                "expiresAt", OffsetDateTime.now().plusDays(7).toString()
        );
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("User not found."));
    }
}
