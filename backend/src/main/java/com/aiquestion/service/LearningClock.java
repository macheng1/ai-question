package com.aiquestion.service;

import com.aiquestion.repository.LearningClockRepository;

/**
 * 同一员工跨页面、视频和学习会话共用计时上限；调用方必须位于事务内。
 */
@org.springframework.stereotype.Service()
public class LearningClock {

    public double credit(long session, double requested) {
        long employeeId = com.aiquestion.repository.RepositoryRows.one(persistence.findSessionEmployeeId(session));
        persistence.initializeClock(employeeId);
        var row = com.aiquestion.repository.RepositoryRows.one(persistence.lockClock(employeeId));
        double elapsed = ((Number) row.get("elapsed")).doubleValue();
        persistence.touchClock(employeeId);
        return Math.min(Math.max(0, requested), Math.min(15, elapsed));
    }

    private final LearningClockRepository persistence;

    public LearningClock(LearningClockRepository persistence) {
        this.persistence = persistence;
    }
}
