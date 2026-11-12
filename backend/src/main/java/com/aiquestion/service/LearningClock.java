package com.aiquestion.service;

import com.aiquestion.repository.LearningClockRepository;

/**
 * 同一手机号跨页面、视频和学习会话共用计时上限；调用方必须位于事务内。
 */
@org.springframework.stereotype.Service()
public class LearningClock {

    public double credit(long session, double requested) {
        String phone = com.aiquestion.repository.RepositoryRows.one(persistence.findSessionPhone(session));
        persistence.initializeClock(phone);
        var row = com.aiquestion.repository.RepositoryRows.one(persistence.lockClock(phone));
        double elapsed = ((Number) row.get("elapsed")).doubleValue();
        persistence.touchClock(phone);
        return Math.min(Math.max(0, requested), Math.min(15, elapsed));
    }

    private final LearningClockRepository persistence;

    public LearningClock(LearningClockRepository persistence) {
        this.persistence = persistence;
    }
}
