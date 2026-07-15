package de.appfabrik.pdfbatch.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

public final class SampleTemplateGenerator {
    private SampleTemplateGenerator() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the output PDF path as the only argument");
        }
        Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, createTemplate());
    }

    public static byte[] createTemplate() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDAcroForm form = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(form);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDResources resources = new PDResources();
            COSName fontName = resources.add(font);
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/" + fontName.getName() + " 11 Tf 0 g");

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                write(content, bold, 22, 70, 770, "Customer welcome sheet");
                write(content, font, 11, 70, 742, "Safe sample template for PDF Batch Community");
                write(content, bold, 11, 70, 705, "Full name");
                write(content, bold, 11, 70, 645, "Customer number");
            }

            addTextField(form, page, "fullName", 670);
            addTextField(form, page, "customerNumber", 610);
            document.getDocumentInformation().setTitle("PDF Batch Community sample template");
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void write(
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

    private static void addTextField(PDAcroForm form, PDPage page, String name, float y)
            throws IOException {
        PDTextField field = new PDTextField(form);
        field.setPartialName(name);
        field.setDefaultAppearance(form.getDefaultAppearance());
        PDAnnotationWidget widget = field.getWidgets().getFirst();
        widget.setRectangle(new PDRectangle(70, y, 360, 28));
        widget.setPage(page);
        page.getAnnotations().add(widget);
        form.getFields().add(field);
    }
}
