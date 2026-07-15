package de.appfabrik.pdfbatch;

import static org.assertj.core.api.Assertions.assertThat;

import de.appfabrik.pdfbatch.document.SampleTemplateGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=local",
            "spring.datasource.url=jdbc:h2:mem:pdfbatch-smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "pdf-batch.storage-root=target/smoke-test-jobs"
        })
class ApplicationSmokeTest {
    private static final Pattern JOB_ID = Pattern.compile("\\\"id\\\":\\\"([0-9a-f-]+)\\\"");
    @LocalServerPort private int port;

    @Test
    void servesTheWebApplicationAndHealthEndpoint() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> home = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(
                                    URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> readiness = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + port + "/actuator/health/readiness"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(home.statusCode()).isEqualTo(200);
            assertThat(home.body()).contains("Turn CSV rows into ready-to-send PDFs");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"");
            assertThat(readiness.statusCode()).isEqualTo(200);
            assertThat(readiness.body()).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void completesTheRealUploadPreviewProcessAndZipWorkflow() throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            String boundary = "pdf-batch-" + UUID.randomUUID();
            byte[] multipart = multipart(
                    boundary,
                    SampleTemplateGenerator.createTemplate(),
                    "name,customerId\nAda Lovelace,C-001\nGrace Hopper,C-002\n"
                            .getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> created = client.send(
                    request("/api/v1/jobs")
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(created.statusCode()).isEqualTo(201);
            String id = jobId(created.body());

            String configuration = """
                    {"mappings":[
                      {"pdfField":"fullName","csvColumn":"name"},
                      {"pdfField":"customerNumber","csvColumn":"customerId"}
                    ],"filenamePattern":"{customerId}-{name}.pdf"}
                    """;
            HttpResponse<String> configured = client.send(
                    request("/api/v1/jobs/" + id + "/configuration")
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(configuration))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(configured.statusCode()).isEqualTo(200);
            assertThat(configured.body()).contains("\"status\":\"READY\"");

            HttpResponse<byte[]> preview = client.send(
                    request("/api/v1/jobs/" + id + "/preview")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(preview.statusCode()).isEqualTo(200);
            try (var document = Loader.loadPDF(preview.body())) {
                assertThat(document.getDocumentCatalog()
                                .getAcroForm()
                                .getField("fullName")
                                .getValueAsString())
                        .isEqualTo("Ada Lovelace");
            }

            HttpResponse<String> queued = client.send(
                    request("/api/v1/jobs/" + id + "/process")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(queued.statusCode()).isEqualTo(202);

            String status = waitForTerminalStatus(client, id);
            assertThat(status).contains("\"status\":\"COMPLETED\"")
                    .contains("\"successfulRows\":2")
                    .contains("\"failedRows\":0");

            HttpResponse<byte[]> result = client.send(
                    request("/api/v1/jobs/" + id + "/result").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(result.statusCode()).isEqualTo(200);
            Map<String, byte[]> entries = unzip(result.body());
            assertThat(entries.keySet())
                    .containsExactlyInAnyOrder(
                            "documents/C-001-Ada-Lovelace.pdf",
                            "documents/C-002-Grace-Hopper.pdf",
                            "manifest.json",
                            "errors.csv");
            assertThat(entries.keySet()).allMatch(name -> !name.contains("..") && !name.startsWith("/"));
        }
    }

    private String waitForTerminalStatus(HttpClient client, String id) throws Exception {
        String body = "";
        for (int attempt = 0; attempt < 100; attempt++) {
            HttpResponse<String> response = client.send(
                    request("/api/v1/jobs/" + id).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            body = response.body();
            if (body.contains("\"status\":\"COMPLETED\"")
                    || body.contains("\"status\":\"COMPLETED_WITH_ERRORS\"")
                    || body.contains("\"status\":\"FAILED\"")) {
                return body;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Job did not reach a terminal state: " + body);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    private static String jobId(String json) {
        var matcher = JOB_ID.matcher(json);
        assertThat(matcher.find()).as("job id in response: %s", json).isTrue();
        return matcher.group(1);
    }

    private static byte[] multipart(String boundary, byte[] pdf, byte[] csv) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            part(output, boundary, "template", "template.pdf", "application/pdf", pdf);
            part(output, boundary, "data", "data.csv", "text/csv", csv);
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        }
    }

    private static void part(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] content)
            throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                        + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(content);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new java.io.ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
