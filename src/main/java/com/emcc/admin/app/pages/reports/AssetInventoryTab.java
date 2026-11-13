package com.emcc.admin.app.pages.reports;

import com.emcc.admin.app.model.Asset;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.util.CsvWriter;
import com.emcc.admin.app.util.ReportExporter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AssetInventoryTab {

    public static Node create() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));

        Label title = new Label("Asset Inventory");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label("A snapshot of community-owned assets — items, quantities, and condition.");
        subtitle.getStyleClass().add("section-subtitle");

        HBox kpiRow = new HBox(14);
        Label typesVal = kpiLabel("0");
        Label itemsVal = kpiLabel("0");
        Label categoriesVal = kpiLabel("0");
        kpiRow.getChildren().addAll(
                kpiCard(typesVal, "Asset Types", "Distinct entries tracked"),
                kpiCard(itemsVal, "Total Items", "Across all assets"),
                kpiCard(categoriesVal, "Categories", "Distinct asset types")
        );

        TableView<Asset> table = new TableView<>();
        table.setPlaceholder(new Label("No assets recorded."));
        TableColumn<Asset, String> nameCol = new TableColumn<>("Asset Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("assetName"));
        nameCol.setPrefWidth(220);
        TableColumn<Asset, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(160);
        TableColumn<Asset, String> countCol = new TableColumn<>("Count");
        countCol.setCellValueFactory(new PropertyValueFactory<>("count"));
        countCol.setPrefWidth(100);
        TableColumn<Asset, String> conditionCol = new TableColumn<>("Condition");
        conditionCol.setCellValueFactory(new PropertyValueFactory<>("condition"));
        conditionCol.setPrefWidth(160);
        table.getColumns().setAll(List.of(nameCol, typeCol, countCol, conditionCol));
        table.setPrefHeight(420);
        table.setItems(SampleData.getAssets());

        Runnable refreshKpis = () -> {
            List<Asset> assets = SampleData.getAssets();
            int total = assets.size();
            int itemCount = assets.stream().mapToInt(a -> parseIntSafe(a.getCount())).sum();
            long categories = assets.stream().map(Asset::getType)
                    .filter(t -> t != null && !t.isBlank())
                    .distinct().count();
            typesVal.setText(String.valueOf(total));
            itemsVal.setText(String.valueOf(itemCount));
            categoriesVal.setText(String.valueOf(categories));
        };
        refreshKpis.run();
        SampleData.getAssets().addListener((javafx.collections.ListChangeListener<Asset>) c -> refreshKpis.run());

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().add("action-button");
        exportCsvBtn.setOnAction(e -> ReportExporter.exportCsv(root.getScene().getWindow(),
                "EMCC_Asset_Inventory.csv", buildCsv()));

        Button exportPdfBtn = new Button("Export PDF");
        exportPdfBtn.getStyleClass().add("action-button");
        exportPdfBtn.setOnAction(e -> ReportExporter.exportPdf(root.getScene().getWindow(),
                "EMCC_Asset_Inventory.pdf", pdf -> {
                    pdf.orgHeader("Eritrean Muslims Community of Columbus (EMCC)");
                    pdf.title("Asset Inventory Report");
                    pdf.generatedOn();
                    pdf.spacer(6);
                    pdf.keyValue("Asset Types", typesVal.getText());
                    pdf.keyValue("Total Items", itemsVal.getText());
                    pdf.keyValue("Categories", categoriesVal.getText());
                    pdf.spacer(10);
                    pdf.sectionHeading("Inventory");
                    List<String[]> rows = SampleData.getAssets().stream()
                            .map(a -> new String[]{a.getAssetName(), a.getType(), a.getCount(), a.getCondition()})
                            .toList();
                    pdf.table(new String[]{"Asset Name", "Type", "Count", "Condition"}, rows,
                            new float[]{2.2f, 1.6f, 1f, 1.6f});
                }));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(10, spacer, exportCsvBtn, exportPdfBtn);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, subtitle, kpiRow, actionRow, table);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    private static String buildCsv() {
        CsvWriter csv = new CsvWriter();
        csv.raw("Eritrean Muslims Community of Columbus (EMCC)");
        csv.raw("Asset Inventory Report");
        csv.line("Generated", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        csv.blank();
        csv.line("Asset Name", "Type", "Count", "Condition");
        for (Asset a : SampleData.getAssets()) {
            csv.line(a.getAssetName(), a.getType(), a.getCount(), a.getCondition());
        }
        return csv.toString();
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static Label kpiLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("kpi-value");
        return l;
    }

    private static VBox kpiCard(Label valueLabel, String label, String caption) {
        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("kpi-label");
        Label captionLbl = new Label(caption);
        captionLbl.getStyleClass().add("kpi-caption");
        VBox card = new VBox(6, labelLbl, valueLabel, captionLbl);
        card.getStyleClass().add("kpi-card");
        card.setPadding(new Insets(16, 20, 16, 20));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}
