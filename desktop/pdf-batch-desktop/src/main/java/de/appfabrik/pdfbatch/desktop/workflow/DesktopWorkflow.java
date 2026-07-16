package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.FieldMapping;
import java.nio.file.Path;
import java.util.List;

public interface DesktopWorkflow extends AutoCloseable {
    BatchJob inspect(Path pdf, Path csv);

    BatchJob configure(List<FieldMapping> mappings, String filenamePattern);

    byte[] preview();

    BatchJob start();

    BatchJob status();

    BatchJob cancel();

    BatchJob exportResult(Path destination);

    void reset();

    @Override
    void close();
}
