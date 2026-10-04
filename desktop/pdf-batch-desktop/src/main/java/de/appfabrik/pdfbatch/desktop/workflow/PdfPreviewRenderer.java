package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.PdfBatchException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

public final class PdfPreviewRenderer {
    public byte[] renderFirstPage(byte[] pdf) {
        return render(pdf, 0);
    }

    public byte[] render(byte[] pdf, int pageIndex) {
        try (PDDocument document = Loader.loadPDF(pdf);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFRenderer renderer = new PDFRenderer(document);
            var bounds = document.getPage(pageIndex).getCropBox();
            float longest = Math.max(bounds.getWidth(), bounds.getHeight());
            if (!Float.isFinite(longest) || longest <= 0)
                throw new IOException("Invalid PDF page dimensions");
            float dpi = Math.min(120, 1800 * 72 / longest);
            var image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
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
