package com.clinic.notification.controller;

import com.clinic.notification.dto.NotificationResponse;
import com.clinic.notification.dto.PageResponse;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.clinic.notification.testutil.TestData.EVENT_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    NotificationService service;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service)).build();
    }

    private static PageResponse<NotificationResponse> pageOf(NotificationStatus st) {
        NotificationResponse r = new NotificationResponse(1L, EVENT_ID, 100L, 7L, 3L, "msg", st, 1, null, null, null);
        return new PageResponse<>(List.of(r), 0, 20, 1, 1);
    }

    @Test
    @DisplayName("Given no params, when GET /notifications, then defaults page=0 size=20 and no status are used")
    void list_defaults() throws Exception {
        // given
        when(service.list(null, 0, 20)).thenReturn(pageOf(NotificationStatus.SENT));

        // when / then
        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20));
        verify(service).list(null, 0, 20);
    }

    @Test
    @DisplayName("Given status, page and size params, when GET /notifications, then they are passed to the service")
    void list_withParams() throws Exception {
        // given
        when(service.list(NotificationStatus.PENDING, 2, 5)).thenReturn(pageOf(NotificationStatus.PENDING));

        // when / then
        mvc.perform(get("/notifications").param("status", "PENDING").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        verify(service).list(NotificationStatus.PENDING, 2, 5);
    }

    @Test
    @DisplayName("Given an unknown status value, when GET /notifications, then 400 is returned")
    void list_invalidStatus_badRequest() throws Exception {
        mvc.perform(get("/notifications").param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given a non-numeric page, when GET /notifications, then 400 is returned")
    void list_invalidPage_badRequest() throws Exception {
        mvc.perform(get("/notifications").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }
}
