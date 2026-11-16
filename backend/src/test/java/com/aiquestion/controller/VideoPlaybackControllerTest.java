package com.aiquestion.controller;

import com.aiquestion.common.BusinessException;
import com.aiquestion.security.*;
import com.aiquestion.service.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoPlaybackControllerTest {
    private final CandidateLearningService learning = mock(CandidateLearningService.class);
    private final VideoPlaybackTickets tickets = mock(VideoPlaybackTickets.class);
    private final TrainingService trainings = mock(TrainingService.class);
    private final AccountService accounts = mock(AccountService.class);
    private final TrainingReviewService review = mock(TrainingReviewService.class);
    private final TrainingVideoStorage storage = mock(TrainingVideoStorage.class);
    private final VideoPlaybackController controller = new VideoPlaybackController(accounts, review, trainings, learning, tickets, storage);
    private final AccountService.Profile employee = new AccountService.Profile(42, "A", "", "", "", "", AccountType.EMPLOYEE, 9);

    @Test void activePlaybackChecksSessionOwnershipAndTask() {
        var session = new VideoPlaybackController.Session("session");
        when(learning.video(-4, 7, "session")).thenReturn(Map.of("employee_id", 99, "training_id", 4));
        assertThrows(BusinessException.class, () -> controller.learning("courses", 4, 7, session, employee));
        verifyNoInteractions(tickets);
        when(learning.video(5, 7, "session")).thenReturn(Map.of("employee_id", 42, "training_id", 4));
        when(tickets.issue(4, 7, VideoPlaybackTickets.Source.EXAM_LEARNING)).thenReturn("ticket");
        assertEquals("ticket", controller.learning("exams", 5, 7, session, employee).data());
    }

    @Test void browserCanReadOnlyTheRequestedRangeWithATicket() throws Exception {
        var row = Map.<String, Object>of("storage_path", "example.mp4");
        when(tickets.verify(7, "ticket")).thenReturn(new VideoPlaybackTickets.Grant(4, VideoPlaybackTickets.Source.COURSE_LEARNING));
        when(trainings.video(4, 7)).thenReturn(row);
        var resource = new ByteArrayResource(new byte[]{0, 1, 2, 3, 4});
        when(storage.content(row)).thenReturn(ResponseEntity.ok().contentType(MediaType.parseMediaType("video/mp4")).body(resource));
        MockMvcBuilders.standaloneSetup(controller).build()
            .perform(get("/video-playback/7").param("ticket", "ticket").header("Range", "bytes=1-3"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string("Content-Range", "bytes 1-3/5"))
            .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test void previewAndReviewCheckExistingResourcePermissions() {
        var admin = new AccountIdentity(9, AccountType.ADMIN, 9);
        var learner = new AccountIdentity(42, AccountType.EMPLOYEE, 9);
        when(accounts.requireAdministrator("admin")).thenReturn(admin);
        when(accounts.requireEmployee("learner")).thenReturn(learner);
        when(trainings.get(1, 9)).thenThrow(new IllegalStateException("denied"));
        when(review.video(learner, 1, 2)).thenThrow(new IllegalStateException("denied"));
        assertThrows(IllegalStateException.class, () -> controller.preview(1, 2, "admin"));
        assertThrows(IllegalStateException.class, () -> controller.review(1, 2, "learner"));
        verifyNoInteractions(tickets, storage);
    }
}
