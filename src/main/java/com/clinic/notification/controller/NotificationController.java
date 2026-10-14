package com.clinic.notification.controller;

import com.clinic.notification.dto.NotificationResponse;
import com.clinic.notification.dto.PageResponse;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, for inspecting results during development. NO authentication: do not expose it. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(required = false) NotificationStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(status, page, size);
    }
}
