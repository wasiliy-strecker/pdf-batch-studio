package de.appfabrik.pdfbatch;

import de.appfabrik.pdfbatch.config.PdfBatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(PdfBatchProperties.class)
@SpringBootApplication
public class PdfBatchStudioApplication {
    public static void main(String[] args) {
        SpringApplication.run(PdfBatchStudioApplication.class, args);
    }
}
