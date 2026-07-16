package de.appfabrik.pdfbatch.config;

import de.appfabrik.pdfbatch.core.DocumentEngine;
import de.appfabrik.pdfbatch.core.DocumentLimits;
import de.appfabrik.pdfbatch.core.JobApplicationService;
import de.appfabrik.pdfbatch.core.JobDispatchPort;
import de.appfabrik.pdfbatch.core.JobRepository;
import de.appfabrik.pdfbatch.core.JobWorker;
import de.appfabrik.pdfbatch.core.JobWorkspace;
import de.appfabrik.pdfbatch.document.LocalJobWorkspace;
import de.appfabrik.pdfbatch.document.PdfCsvDocumentEngine;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DocumentLimits documentLimits(PdfBatchProperties properties) {
        PdfBatchProperties.Limits limits = properties.limits();
        return new DocumentLimits(
                limits.maxPdfBytes(),
                limits.maxCsvBytes(),
                limits.maxRows(),
                limits.maxPdfPages(),
                limits.maxPdfFields(),
                limits.maxFieldValueLength());
    }

    @Bean
    DocumentEngine documentEngine() {
        return new PdfCsvDocumentEngine();
    }

    @Bean
    JobWorkspace jobWorkspace(PdfBatchProperties properties) {
        return new LocalJobWorkspace(properties.storageRoot());
    }

    @Bean
    JobWorker jobWorker(
            JobRepository repository,
            JobWorkspace workspace,
            DocumentEngine documentEngine,
            DocumentLimits limits,
            Clock clock) {
        return new JobWorker(repository, workspace, documentEngine, limits, clock);
    }

    @Bean
    JobDispatchPort jobDispatcher(JobWorker worker, PdfBatchProperties properties) {
        PdfBatchProperties.Processing processing = properties.processing();
        return new BoundedJobDispatcher(
                worker, processing.workers(), processing.queueCapacity());
    }

    @Bean
    JobApplicationService jobApplicationService(
            JobRepository repository,
            JobWorkspace workspace,
            DocumentEngine documentEngine,
            JobDispatchPort dispatcher,
            DocumentLimits limits,
            Clock clock,
            PdfBatchProperties properties) {
        return new JobApplicationService(
                repository,
                workspace,
                documentEngine,
                dispatcher,
                limits,
                clock,
                properties.retention(),
                properties.processing().maxActiveJobs());
    }
}
