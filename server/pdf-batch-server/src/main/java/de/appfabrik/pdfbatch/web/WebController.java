package de.appfabrik.pdfbatch.web;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobApplicationService;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.PdfBatchException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private final JobApplicationService jobs;

    public WebController(JobApplicationService jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/")
    String home() {
        return "index";
    }

    @PostMapping("/web/jobs")
    String create(
            @RequestParam("template") MultipartFile template,
            @RequestParam("data") MultipartFile data)
            throws IOException {
        try (InputStream pdfInput = template.getInputStream();
                InputStream csvInput = data.getInputStream()) {
            BatchJob job = jobs.create(pdfInput, csvInput);
            return "redirect:/jobs/" + job.id();
        }
    }

    @GetMapping("/jobs/{id}")
    String job(@PathVariable UUID id, Model model) {
        BatchJob job = jobs.get(id);
        model.addAttribute("job", job);
        model.addAttribute("delimiterName", delimiterName(job.csvDelimiter()));
        model.addAttribute("formattedExpiry", EXPIRY_FORMAT.format(job.expiresAt()));
        HashMap<String, String> mappingByField = new HashMap<>();
        job.mappings().forEach(mapping -> mappingByField.put(mapping.pdfField(), mapping.csvColumn()));
        model.addAttribute("mappingByField", mappingByField);
        return "job";
    }

    @GetMapping("/jobs/{id}/status")
    String status(@PathVariable UUID id, Model model) {
        model.addAttribute("job", jobs.get(id));
        return "job :: status";
    }

    @PostMapping("/web/jobs/{id}/configuration")
    String configure(
            @PathVariable UUID id,
            @RequestParam("pdfField") List<String> pdfFields,
            @RequestParam("csvColumn") List<String> csvColumns,
            @RequestParam("filenamePattern") String filenamePattern,
            RedirectAttributes redirectAttributes) {
        if (pdfFields.size() != csvColumns.size()) {
            throw new IllegalArgumentException("Invalid mapping form submission");
        }
        List<FieldMapping> mappings = new ArrayList<>();
        for (int index = 0; index < pdfFields.size(); index++) {
            if (!csvColumns.get(index).isBlank()) {
                mappings.add(new FieldMapping(pdfFields.get(index), csvColumns.get(index)));
            }
        }
        jobs.configure(id, new JobConfiguration(mappings, filenamePattern));
        redirectAttributes.addFlashAttribute("message", "Mapping saved. Preview is ready.");
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/web/jobs/{id}/process")
    String process(@PathVariable UUID id) {
        jobs.start(id);
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/web/jobs/{id}/cancel")
    String cancel(@PathVariable UUID id) {
        jobs.cancel(id);
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/web/jobs/{id}/delete")
    String delete(@PathVariable UUID id) {
        jobs.delete(id);
        return "redirect:/";
    }

    @ExceptionHandler({PdfBatchException.class, IllegalArgumentException.class, IOException.class})
    ModelAndView error(Exception exception) {
        ModelAndView view = new ModelAndView("error");
        view.setStatus(exception instanceof PdfBatchException
                ? HttpStatus.UNPROCESSABLE_CONTENT
                : HttpStatus.BAD_REQUEST);
        view.addObject("message", exception.getMessage());
        return view;
    }

    private static String delimiterName(char delimiter) {
        return switch (delimiter) {
            case ',' -> "Comma";
            case ';' -> "Semicolon";
            case '\t' -> "Tab";
            default -> "Unknown";
        };
    }
}
