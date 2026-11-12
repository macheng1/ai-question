package com.aiquestion.service;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.aiquestion.controller.ManagementOverviewController.*;
import com.aiquestion.repository.ManagementOverviewRepository;

@org.springframework.stereotype.Service()
public class ManagementOverviewService {

    private final ManagementOverviewRepository persistence;

    public ManagementOverviewService(ManagementOverviewRepository persistence) {
        this.persistence = persistence;
    }

    public ApiResponse<?> overview() {
        long owner = ManagementAccess.userId();
        return ApiResponse.ok(Map.of("trainingCount", com.aiquestion.repository.RepositoryRows.one(persistence.countTrainings(owner)), "employeeCount", com.aiquestion.repository.RepositoryRows.one(persistence.countEmployees(owner)), "examCount", com.aiquestion.repository.RepositoryRows.one(persistence.countExams(owner))));
    }
}
