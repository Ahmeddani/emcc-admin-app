package com.emcc.admin.app.util;

import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileWriter;

/** Shared "save this report to a file" flow for CSV and PDF exports. */
public class ReportExporter {

    private ReportExporter() {}

    public static void exportCsv(Window owner, String suggestedFileName, String csvContent) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Report");
        chooser.setInitialFileName(suggestedFileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(csvContent);
            showSuccess(file);
        } catch (Exception ex) {
            showFailure(ex);
        }
    }

    public static void exportPdf(Window owner, String suggestedFileName, PdfExportAction action) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Report");
        chooser.setInitialFileName(suggestedFileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        try {
            PdfReportBuilder pdf = new PdfReportBuilder();
            action.build(pdf);
            pdf.save(file);
            showSuccess(file);
        } catch (Exception ex) {
            showFailure(ex);
        }
    }

    private static void showSuccess(File file) {
        Alert ok = new Alert(Alert.AlertType.INFORMATION);
        ok.setTitle("Report Saved");
        ok.setHeaderText(null);
        ok.setContentText("Saved to:\n" + file.getAbsolutePath());
        ok.showAndWait();
    }

    private static void showFailure(Exception ex) {
        Alert err = new Alert(Alert.AlertType.ERROR);
        err.setTitle("Export Failed");
        err.setHeaderText(null);
        err.setContentText("Could not save: " + ex.getMessage());
        err.showAndWait();
    }

    @FunctionalInterface
    public interface PdfExportAction {
        void build(PdfReportBuilder pdf) throws Exception;
    }
}
