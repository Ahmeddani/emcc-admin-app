package com.emcc.admin.app.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Minimal PDF report writer built on PDFBox's low-level content-stream API.
 * Handles page breaks, a simple title/table layout, and sanitizes text since
 * the standard Helvetica fonts only support WinAnsi encoding (no checkmarks,
 * em dashes render fine but symbols like the ones used elsewhere in the app's
 * UI, e.g. checkmarks, do not and would throw).
 */
public class PdfReportBuilder {

    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float MARGIN = 50f;
    private static final float BOTTOM_MARGIN = 50f;

    private final PDDocument document = new PDDocument();
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private PDPage page;
    private PDPageContentStream stream;
    private float cursorY;

    public PdfReportBuilder() throws IOException {
        newPage();
    }

    private void newPage() throws IOException {
        if (stream != null) stream.close();
        page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        cursorY = PAGE_HEIGHT - MARGIN;
    }

    private void ensureSpace(float needed) throws IOException {
        if (cursorY - needed < BOTTOM_MARGIN) {
            newPage();
        }
    }

    public PdfReportBuilder orgHeader(String orgName) throws IOException {
        ensureSpace(40);
        drawText(regular, 9, MARGIN, cursorY, sanitize(orgName));
        cursorY -= 8;
        stream.setStrokingColor(0.85f, 0.85f, 0.85f);
        stream.moveTo(MARGIN, cursorY);
        stream.lineTo(PAGE_WIDTH - MARGIN, cursorY);
        stream.stroke();
        cursorY -= 18;
        return this;
    }

    public PdfReportBuilder title(String text) throws IOException {
        ensureSpace(30);
        drawText(bold, 18, MARGIN, cursorY, sanitize(text));
        cursorY -= 24;
        return this;
    }

    public PdfReportBuilder subtitle(String text) throws IOException {
        ensureSpace(20);
        drawText(regular, 11, MARGIN, cursorY, sanitize(text));
        cursorY -= 16;
        return this;
    }

    public PdfReportBuilder generatedOn() throws IOException {
        String text = "Generated " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        ensureSpace(18);
        drawText(italic, 9, MARGIN, cursorY, text);
        cursorY -= 20;
        return this;
    }

    public PdfReportBuilder sectionHeading(String text) throws IOException {
        ensureSpace(24);
        drawText(bold, 13, MARGIN, cursorY, sanitize(text));
        cursorY -= 18;
        return this;
    }

    public PdfReportBuilder paragraph(String text) throws IOException {
        ensureSpace(16);
        drawText(regular, 10, MARGIN, cursorY, sanitize(text));
        cursorY -= 16;
        return this;
    }

    public PdfReportBuilder keyValue(String label, String value) throws IOException {
        ensureSpace(16);
        drawText(bold, 10, MARGIN, cursorY, sanitize(label) + ":");
        drawText(regular, 10, MARGIN + 150, cursorY, sanitize(value));
        cursorY -= 15;
        return this;
    }

    public PdfReportBuilder spacer(float amount) throws IOException {
        cursorY -= amount;
        return this;
    }

    /** Embeds an image (e.g. a chart snapshot), scaled to fit the page width, preserving aspect ratio. */
    public PdfReportBuilder image(BufferedImage img) throws IOException {
        return image(img, PAGE_WIDTH - 2 * MARGIN);
    }

    public PdfReportBuilder image(BufferedImage img, float maxWidthPts) throws IOException {
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, img);
        float scale = Math.min(1f, maxWidthPts / img.getWidth());
        float w = img.getWidth() * scale;
        float h = img.getHeight() * scale;
        ensureSpace(h + 10);
        stream.drawImage(pdImage, MARGIN, cursorY - h, w, h);
        cursorY -= (h + 10);
        return this;
    }

    /**
     * Renders a simple table. Column widths are proportions that get scaled to
     * the available content width.
     */
    public PdfReportBuilder table(String[] headers, List<String[]> rows, float[] colWidthWeights) throws IOException {
        float contentWidth = PAGE_WIDTH - 2 * MARGIN;
        float totalWeight = 0;
        for (float w : colWidthWeights) totalWeight += w;
        float[] colWidths = new float[colWidthWeights.length];
        for (int i = 0; i < colWidthWeights.length; i++) colWidths[i] = contentWidth * (colWidthWeights[i] / totalWeight);

        ensureSpace(24);
        drawRow(headers, colWidths, bold, 9, true);
        for (String[] row : rows) {
            ensureSpace(16);
            drawRow(row, colWidths, regular, 9, false);
        }
        cursorY -= 6;
        return this;
    }

    private void drawRow(String[] cells, float[] colWidths, PDFont font, float size, boolean header) throws IOException {
        float x = MARGIN;
        for (int i = 0; i < cells.length && i < colWidths.length; i++) {
            String cell = truncateToWidth(sanitize(cells[i] == null ? "" : cells[i]), font, size, colWidths[i] - 4);
            drawText(font, size, x, cursorY, cell);
            x += colWidths[i];
        }
        cursorY -= 14;
        if (header) {
            stream.setStrokingColor(0.7f, 0.7f, 0.7f);
            stream.moveTo(MARGIN, cursorY + 4);
            stream.lineTo(PAGE_WIDTH - MARGIN, cursorY + 4);
            stream.stroke();
            cursorY -= 2;
        }
    }

    private String truncateToWidth(String text, PDFont font, float size, float maxWidth) throws IOException {
        if (font.getStringWidth(text) / 1000f * size <= maxWidth) return text;
        String truncated = text;
        while (!truncated.isEmpty() && font.getStringWidth(truncated + "...") / 1000f * size > maxWidth) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated.isEmpty() ? "" : truncated + "...";
    }

    private void drawText(PDFont font, float size, float x, float y, String text) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    /** Strips characters the standard WinAnsi-encoded fonts can't render. */
    private static String sanitize(String s) {
        if (s == null) return "";
        return s
                .replace("✓", "")
                .replace("✗", "")
                .replace("◑", "")
                .replace("⚠", "")
                .replace("↻", "")
                .replace("⬇", "")
                .replaceAll("[^\\x00-\\xFF]", "");
    }

    public void save(File file) throws IOException {
        if (stream != null) stream.close();
        document.save(file);
        document.close();
    }
}
