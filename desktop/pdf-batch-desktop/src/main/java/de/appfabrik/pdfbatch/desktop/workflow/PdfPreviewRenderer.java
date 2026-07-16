package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.PdfBatchException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

public final class PdfPreviewRenderer {
    public byte[] renderFirstPage(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFRenderer renderer = new PDFRenderer(document);
            var image = renderer.renderImageWithDPI(0, 120, ImageType.RGB);
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("No PNG image writer is available");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "DESKTOP_PREVIEW_RENDER_FAILED", "Could not render the PDF preview", exception);
        }
    }
}
