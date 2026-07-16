package de.appfabrik.pdfbatch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PdfBatchPropertiesTest {
    @Test
    void acceptsCapacityCoveredByWorkersAndQueue() {
        var processing = new PdfBatchProperties.Processing(2, 8, 10);

        assertThat(processing.maxActiveJobs()).isEqualTo(10);
    }

    @Test
    void rejectsActiveCapacityLargerThanTheExecutor() {
        assertThatThrownBy(() -> new PdfBatchProperties.Processing(2, 3, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed");
    }
}
