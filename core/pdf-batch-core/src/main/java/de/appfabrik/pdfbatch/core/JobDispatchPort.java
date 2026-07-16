package de.appfabrik.pdfbatch.core;

import java.util.UUID;

public interface JobDispatchPort {
    void dispatch(UUID jobId);
}
