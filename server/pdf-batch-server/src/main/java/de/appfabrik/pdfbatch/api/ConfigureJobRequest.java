package de.appfabrik.pdfbatch.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ConfigureJobRequest(
        @NotEmpty List<@Valid MappingRequest> mappings,
        @NotBlank @Size(max = 512) String filenamePattern) {
    public record MappingRequest(
            @NotBlank @Size(max = 512) String pdfField,
            @NotBlank @Size(max = 512) String csvColumn) {}
}
