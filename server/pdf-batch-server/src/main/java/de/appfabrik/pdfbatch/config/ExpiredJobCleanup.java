package de.appfabrik.pdfbatch.config;

import de.appfabrik.pdfbatch.core.JobApplicationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredJobCleanup {
    private final JobApplicationService jobs;

    public ExpiredJobCleanup(JobApplicationService jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${pdf-batch.cleanup-interval}")
    public void deleteExpiredJobs() {
        jobs.cleanupExpired();
    }
}
