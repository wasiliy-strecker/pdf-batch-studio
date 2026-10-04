package de.appfabrik.pdfbatch.desktop.workflow;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DemoService {
    public record Demo(Path pdf, Path csv, String name) {}

    public Demo create(Path workspace, boolean certificate, boolean german) throws IOException {
        Path directory = Files.createTempDirectory(workspace, "example-");
        Path pdf = directory.resolve("template.pdf");
        Path csv = directory.resolve("data.csv");
        String title =
                certificate
                        ? (german ? "Teilnahmebescheinigung" : "Certificate of participation")
                        : (german ? "Willkommen im Team" : "Welcome to the team");
        List<String> fields =
                certificate
                        ? List.of("name", "course", "date")
                        : List.of("name", "department", "startDate");
        List<String> labels =
                certificate
                        ? (german
                                ? List.of("Name", "Veranstaltung", "Datum")
                                : List.of("Name", "Course", "Date"))
                        : (german
                                ? List.of("Name", "Abteilung", "Erster Arbeitstag")
                                : List.of("Name", "Department", "First working day"));
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            var form = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(form);
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            var resources = new PDResources();
            COSName fontName = resources.add(font);
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/" + fontName.getName() + " 15 Tf 0.12 0.19 0.29 rg");
            try (var content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(0.08f, 0.16f, 0.25f);
                content.addRect(0, 730, 595, 112);
                content.fill();
                content.setNonStrokingColor(1f, 1f, 1f);
                text(content, bold, 23, 50, 780, title);
                text(content, font, 10, 50, 753, "PDF BATCH STUDIO  /  DEMO");
                content.setNonStrokingColor(0.22f, 0.29f, 0.37f);
                text(
                        content,
                        font,
                        12,
                        50,
                        683,
                        certificate
                                ? (german
                                        ? "Vielen Dank für die erfolgreiche Teilnahme."
                                        : "Thank you for your successful participation.")
                                : (german
                                        ? "Wir freuen uns auf die Zusammenarbeit."
                                        : "We look forward to working with you."));
                for (int i = 0; i < fields.size(); i++) {
                    float y = 580 - i * 105;
                    text(content, bold, 10, 50, y + 42, labels.get(i));
                    content.setStrokingColor(0.7f, 0.77f, 0.82f);
                    content.moveTo(50, y - 4);
                    content.lineTo(540, y - 4);
                    content.stroke();
                    var field = new PDTextField(form);
                    field.setPartialName(fields.get(i));
                    field.setRequired(true);
                    field.setDefaultAppearance(form.getDefaultAppearance());
                    var widget = field.getWidgets().getFirst();
                    widget.setRectangle(new PDRectangle(50, y, 490, 28));
                    widget.setPage(page);
                    page.getAnnotations().add(widget);
                    form.getFields().add(field);
                }
                text(
                        content,
                        font,
                        9,
                        50,
                        65,
                        german
                                ? "Beispieldaten. Vollständig lokal erstellt."
                                : "Sample data. Created entirely offline.");
            }
            document.save(pdf.toFile());
        }
        Files.writeString(
                csv,
                certificate
                        ? "name,course,date\n"
                                + "Anna Müller,Java Grundlagen,04.10.2026\n"
                                + "Max Weiß,SQL Workshop,05.10.2026\n"
                                + "Sofia García,Java Grundlagen,04.10.2026\n"
                        : "name,department,startDate\n"
                                + "Anna Müller,Entwicklung,01.11.2026\n"
                                + "Max Weiß,Qualitätssicherung,01.11.2026\n"
                                + "Sofia García,Produktmanagement,15.11.2026\n");
        return new Demo(pdf, csv, title);
    }

    private static void text(
            PDPageContentStream content,
            PDType1Font font,
            float size,
            float x,
            float y,
            String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }
}
