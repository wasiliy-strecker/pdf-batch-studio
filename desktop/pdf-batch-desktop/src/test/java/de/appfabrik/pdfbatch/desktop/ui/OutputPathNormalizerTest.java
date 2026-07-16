package de.appfabrik.pdfbatch.desktop.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutputPathNormalizerTest {
    @ParameterizedTest
    @CsvSource({
        "result,result.zip",
        "result.zip,result.zip",
        "result.ZIP,result.ZIP",
        "result.zip.zip,result.zip",
        "result.zip.zip.zip,result.zip"
    })
    void keepsExactlyOneZipExtension(String input, String expected) {
        assertThat(OutputPathNormalizer.ensureSingleZipExtension(Path.of("/tmp", input)))
                .isEqualTo(Path.of("/tmp", expected));
    }
}
