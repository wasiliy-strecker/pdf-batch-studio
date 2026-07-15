package de.appfabrik.pdfbatch.core;

import java.util.EnumSet;
import java.util.Set;

public enum JobStatus {
    CREATED,
    VALIDATING,
    DRAFT,
    READY,
    QUEUED,
    PROCESSING,
    PACKAGING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    CANCELLING,
    CANCELLED,
    FAILED,
    EXPIRED;

    private static final Set<JobStatus> ACTIVE =
            EnumSet.of(QUEUED, PROCESSING, PACKAGING, CANCELLING);
    private static final Set<JobStatus> TERMINAL =
            EnumSet.of(COMPLETED, COMPLETED_WITH_ERRORS, CANCELLED, FAILED, EXPIRED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
