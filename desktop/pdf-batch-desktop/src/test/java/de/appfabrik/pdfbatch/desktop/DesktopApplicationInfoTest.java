package de.appfabrik.pdfbatch.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DesktopApplicationInfoTest {
    @Test
    void readsTheProductVersionFromTheMavenFilteredResource() {
        assertThat(DesktopApplicationInfo.VERSION).isEqualTo("0.1.0");
    }
}
