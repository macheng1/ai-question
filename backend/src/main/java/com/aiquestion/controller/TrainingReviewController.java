package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;

@RestController
@RequestMapping("/learner/trainings/{id}/review")
public class TrainingReviewController {
    private final AccountService accounts;
    private final TrainingReviewService review;
    private final TrainingVideoStorage videos;
    private final TrainingMaterialService materials;
    public TrainingReviewController(AccountService accounts, TrainingReviewService review,
            TrainingVideoStorage videos, TrainingMaterialService materials) {
        this.accounts = accounts; this.review = review; this.videos = videos; this.materials = materials;
    }
    @GetMapping
    public ApiResponse<?> list(@PathVariable long id, @RequestHeader(value="Authorization", required=false) String auth) {
        return ApiResponse.ok(review.list(accounts.requireEmployee(auth), id));
    }
    @GetMapping("/videos/{video}")
    public ResponseEntity<Resource> video(@PathVariable long id, @PathVariable long video,
            @RequestHeader(value="Authorization", required=false) String auth) {
        return videos.content(review.video(accounts.requireEmployee(auth), id, video));
    }
    @GetMapping(value="/materials/{material}/pages/{page}", produces="image/png")
    public ResponseEntity<byte[]> page(@PathVariable long id, @PathVariable long material, @PathVariable int page,
            @RequestHeader(value="Authorization", required=false) String auth) throws java.io.IOException {
        review.authorize(accounts.requireEmployee(auth), id);
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(materials.page(id, material, page));
    }
}
