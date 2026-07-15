package de.appfabrik.pdfbatch.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.CsvInspection;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import de.appfabrik.pdfbatch.core.PdfInspection;
import de.appfabrik.pdfbatch.core.UploadInspection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PostgreSqlPersistenceTest {
    @Test
    void flywaySchemaAndJpaMappingRoundTripAgainstPostgreSql() {
        boolean dockerSocketIsUsable = System.getenv("DOCKER_HOST") != null
                || (Files.isReadable(Path.of("/var/run/docker.sock"))
                        && Files.isWritable(Path.of("/var/run/docker.sock")));
        Assumptions.assumeTrue(
                dockerSocketIsUsable, "Docker socket is not accessible to the current user");
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the PostgreSQL integration test");

        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.10-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).load().migrate();

            try (EntityManagerFactory entityManagerFactory = entityManagerFactory(dataSource)) {
                UUID id = UUID.randomUUID();
                BatchJob original = draft(id);

                try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                    entityManager.getTransaction().begin();
                    entityManager.persist(BatchJobEntity.fromDomain(original));
                    entityManager.getTransaction().commit();
                }

                original.configure(
                        new JobConfiguration(
                                List.of(new FieldMapping("fullName", "name")), "{customerId}.pdf"),
                        now().plusSeconds(1));
                try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                    entityManager.getTransaction().begin();
                    entityManager.find(BatchJobEntity.class, id).updateFrom(original);
                    entityManager.getTransaction().commit();
                }

                BatchJob restored;
                try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                    entityManager.getTransaction().begin();
                    restored = entityManager.find(BatchJobEntity.class, id).toDomain();
                    entityManager.getTransaction().commit();
                }

                assertThat(restored).isNotNull();
                assertThat(restored.id()).isEqualTo(id);
                assertThat(restored.pdfFields())
                        .extracting(PdfFieldInfo::name)
                        .containsExactly("fullName");
                assertThat(restored.csvHeaders()).containsExactly("name", "customerId");
                assertThat(restored.totalRows()).isEqualTo(2);
                assertThat(restored.status()).isEqualTo(JobStatus.READY);
                assertThat(restored.mappings()).containsExactly(new FieldMapping("fullName", "name"));
            }
        }
    }

    private static EntityManagerFactory entityManagerFactory(DriverManagerDataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory =
                new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(BatchJobEntity.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.jdbc.time_zone", "UTC"));
        factory.afterPropertiesSet();
        return java.util.Objects.requireNonNull(factory.getObject());
    }

    private static BatchJob draft(UUID id) {
        Instant now = now();
        return BatchJob.draft(
                id,
                new UploadInspection(
                        new PdfInspection(
                                1, List.of(new PdfFieldInfo("fullName", "PDTextField", true))),
                        new CsvInspection(',', List.of("name", "customerId"), 2)),
                now,
                now.plusSeconds(3600));
    }

    private static Instant now() {
        return Instant.parse("2026-01-01T10:00:00Z");
    }
}
