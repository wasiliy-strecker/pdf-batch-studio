package de.appfabrik.pdfbatch.persistence;

import de.appfabrik.pdfbatch.core.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {
    @Bean
    JobRepository jobRepository(SpringDataBatchJobRepository repository) {
        return new JpaJobRepositoryAdapter(repository);
    }
}
