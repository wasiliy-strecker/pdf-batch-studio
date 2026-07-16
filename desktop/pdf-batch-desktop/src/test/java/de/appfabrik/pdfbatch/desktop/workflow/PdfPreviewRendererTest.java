package de.appfabrik.pdfbatch.desktop.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import de.appfabrik.pdfbatch.document.SampleTemplateGenerator;
import org.junit.jupiter.api.Test;

class PdfPreviewRendererTest {
    @Test
    void rendersTheFirstPdfPageAsPng() throws Exception {
        byte[] png = new PdfPreviewRenderer().renderFirstPage(
                SampleTemplateGenerator.createTemplate());

        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(png.length).isGreaterThan(1_000);
    }
}
