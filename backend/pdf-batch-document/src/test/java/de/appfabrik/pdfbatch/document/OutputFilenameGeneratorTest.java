package de.appfabrik.pdfbatch.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputFilenameGeneratorTest {
    @Test
    void sanitizesPathsAndKeepsTheResultInsideTheArchive() {
        OutputFilenameGenerator generator = new OutputFilenameGenerator();

        String filename = generator.generate("../{name}.pdf", Map.of("name", "A/B:C"), 1);

        assertThat(filename).isEqualTo("A-B-C.pdf");
        assertThat(filename).doesNotContain("..", "/", "\\");
    }

    @Test
    void resolvesCaseInsensitiveCollisionsDeterministically() {
        OutputFilenameGenerator generator = new OutputFilenameGenerator();

        assertThat(generator.generate("{name}", Map.of("name", "Invoice"), 1))
                .isEqualTo("Invoice.pdf");
        assertThat(generator.generate("{name}", Map.of("name", "invoice"), 2))
                .isEqualTo("invoice-2.pdf");
    }

    @Test
    void fallsBackForAnEmptyFilename() {
        assertThat(new OutputFilenameGenerator().generate("{name}", Map.of("name", "<>"), 7))
                .isEqualTo("document-7.pdf");
    }
}
