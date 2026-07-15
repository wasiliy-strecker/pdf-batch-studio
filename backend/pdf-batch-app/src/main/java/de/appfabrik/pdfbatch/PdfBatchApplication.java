package de.appfabrik.pdfbatch;

import de.appfabrik.pdfbatch.config.PdfBatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(PdfBatchProperties.class)
@SpringBootApplication
public class PdfBatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(PdfBatchApplication.class, args);
    }
}
