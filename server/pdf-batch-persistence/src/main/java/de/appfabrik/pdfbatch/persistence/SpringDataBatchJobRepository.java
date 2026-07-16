package de.appfabrik.pdfbatch.persistence;

import de.appfabrik.pdfbatch.core.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataBatchJobRepository extends JpaRepository<BatchJobEntity, UUID> {
    long countByStatusIn(Collection<JobStatus> statuses);

    boolean existsByIdAndStatus(UUID id, JobStatus status);

    List<BatchJobEntity> findByExpiresAtLessThanEqualOrderByExpiresAtAsc(Instant cutoff);
}
