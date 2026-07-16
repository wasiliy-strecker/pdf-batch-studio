package de.appfabrik.pdfbatch.desktop.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DesktopRuntimeSettingsTest {
    @Test
    void usesAProfessionalDefaultWithoutAnEditionLimit() {
        DesktopRuntimeSettings settings = DesktopRuntimeSettings.fromValues(null, null);

        assertThat(settings.maxRows()).isEqualTo(10_000);
        assertThat(settings.documentLimits().maxRows()).isEqualTo(10_000);
    }

    @Test
    void letsTheSystemPropertyOverrideTheEnvironment() {
        DesktopRuntimeSettings settings = DesktopRuntimeSettings.fromValues("25000", "12000");

        assertThat(settings.maxRows()).isEqualTo(25_000);
    }

    @Test
    void rejectsInvalidValuesWithAClearMessage() {
        assertThatThrownBy(() -> DesktopRuntimeSettings.fromValues(null, "unlimited"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive integer");
    }
}
