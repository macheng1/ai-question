package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.LearnerAccess;
import com.aiquestion.security.VideoPlaybackTickets;
import com.aiquestion.service.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class VideoPlaybackController {
    private final AccountService accounts;
    private final TrainingReviewService review;
    private final TrainingService trainings;
    private final CandidateLearningService learning;
    private final VideoPlaybackTickets tickets;
    private final TrainingVideoStorage storage;

    public VideoPlaybackController(AccountService accounts, TrainingReviewService review, TrainingService trainings,
            CandidateLearningService learning, VideoPlaybackTickets tickets, TrainingVideoStorage storage) {
        this.accounts = accounts;
        this.review = review;
        this.trainings = trainings;
        this.learning = learning;
        this.tickets = tickets;
        this.storage = storage;
    }

    @PostMapping("/trainings/{id}/videos/{video}/playback")
    public ApiResponse<String> preview(@PathVariable long id, @PathVariable long video,
            @RequestHeader("Authorization") String auth) {
        trainings.get(id, accounts.requireAdministrator(auth).accountId());
        trainings.video(id, video);
        return ApiResponse.ok(tickets.issue(id, video));
    }

    @PostMapping("/learner/trainings/{id}/review/videos/{video}/playback")
    public ApiResponse<String> review(@PathVariable long id, @PathVariable long video,
            @RequestHeader("Authorization") String auth) {
        review.video(accounts.requireEmployee(auth), id, video);
        return ApiResponse.ok(tickets.issue(id, video));
    }

    public record Session(String token) {}

    @PostMapping("/public/{kind:courses|exams}/{id}/learning/videos/{video}/playback")
    public ApiResponse<String> learning(@PathVariable String kind, @PathVariable long id, @PathVariable long video,
            @RequestBody Session session, @RequestAttribute(LearnerAccess.PROFILE) AccountService.Profile employee) {
        if (id <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST);
        var row = learning.video(kind.equals("courses") ? -id : id, video, session.token());
        if (((Number) row.get("employee_id")).longValue() != employee.userId())
            throw new BusinessException(ErrorCode.FORBIDDEN);
        return ApiResponse.ok(tickets.issue(((Number) row.get("training_id")).longValue(), video));
    }

    @GetMapping("/video-playback/{video}")
    public ResponseEntity<Resource> content(@PathVariable long video, @RequestParam String ticket) {
        long training = tickets.verify(video, ticket);
        return storage.content(trainings.video(training, video));
    }
}
