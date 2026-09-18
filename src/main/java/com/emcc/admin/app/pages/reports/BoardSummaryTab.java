package com.emcc.admin.app.pages.reports;

import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.Payment;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.util.CsvWriter;
import com.emcc.admin.app.util.DuesCalculator;
import com.emcc.admin.app.util.PeriodScope;
import com.emcc.admin.app.util.ReportExporter;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BoardSummaryTab {

    private static final String[] MONTHS_SHORT = PeriodScope.MONTHS_SHORT;
    private static final String[] MONTHS_FULL = PeriodScope.MONTHS_FULL;
    private static final String[] QUARTERS = PeriodScope.QUARTERS;

    public static Node create() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));

        Label title = new Label("Board / Annual Summary");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label("A presentation-ready overview of dues collection — share with the board or at a general meeting.");
        subtitle.getStyleClass().add("section-subtitle");
        subtitle.setWrapText(true);

        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();

        ComboBox<Integer> yearBox = new ComboBox<>();
        ComboBox<String> periodBox = new ComboBox<>(FXCollections.observableArrayList("Monthly", "Quarterly", "Yearly"));
        periodBox.setValue("Yearly");
        ComboBox<String> monthBox = new ComboBox<>(FXCollections.observableArrayList(MONTHS_FULL));
        monthBox.setValue(MONTHS_FULL[currentMonth - 1]);
        monthBox.setVisible(false);
        monthBox.setManaged(false);
        ComboBox<String> quarterBox = new ComboBox<>(FXCollections.observableArrayList(QUARTERS));
        quarterBox.setValue(QUARTERS[0]);
        quarterBox.setVisible(false);
        quarterBox.setManaged(false);

        HBox filterRow = new HBox(10, new Label("Year:"), yearBox, new Label("Period:"), periodBox, monthBox, quarterBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        Label collectedVal = kpiLabel("$0.00");
        Label rateVal = kpiLabel("0%");
        Label outstandingVal = kpiLabel("$0.00");
        Label statusVal = kpiLabel("0/0");

        HBox kpiRow = new HBox(14);
        kpiRow.getChildren().addAll(
                kpiCard(collectedVal, "Total Collected", "Completed payments in period"),
                kpiCard(rateVal, "Collection Rate", "Members paid vs. eligible"),
                kpiCard(outstandingVal, "Outstanding", "Owed for months elapsed"),
                kpiCard(statusVal, "Members Current", "Fully paid as of today")
        );

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Month");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Collected ($)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Collections by Month");
        chart.setLegendVisible(false);
        chart.setPrefHeight(320);
        chart.getStyleClass().add("chart");
        // Disabled so a PDF export snapshot taken right after the data changes
        // always shows fully-grown bars instead of catching them mid-animation.
        chart.setAnimated(false);

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().add("action-button");
        Button exportPdfBtn = new Button("Export PDF");
        exportPdfBtn.getStyleClass().add("action-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(10, spacer, exportCsvBtn, exportPdfBtn);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        double[] monthlyTotals = new double[12];

        Runnable[] refreshHolder = new Runnable[1];

        Runnable populateYears = () -> {
            Set<Integer> years = new TreeSet<>();
            years.add(currentYear);
            for (Payment p : SampleData.getPayments()) {
                try { years.add(LocalDate.parse(p.getDate()).getYear()); } catch (Exception ignored) {}
            }
            Integer prev = yearBox.getValue();
            yearBox.setItems(FXCollections.observableArrayList(years));
            yearBox.setValue(prev != null && years.contains(prev) ? prev : currentYear);
        };

        refreshHolder[0] = () -> {
            int year = yearBox.getValue() == null ? currentYear : yearBox.getValue();
            String period = periodBox.getValue();
            int mIdx = PeriodScope.indexOf(MONTHS_FULL, monthBox.getValue()) + 1;
            int qIdx = PeriodScope.indexOf(QUARTERS, quarterBox.getValue()) + 1;
            List<Integer> scope = PeriodScope.scopeMonths(period, mIdx, qIdx);

            LocalDate today = LocalDate.now();
            List<Payment> payments = SampleData.getPayments();
            List<Member> members = SampleData.getMembers().stream()
                    .filter(m -> !m.getMembershipId().isEmpty() || !m.getName().isEmpty())
                    .collect(Collectors.toList());

            BigDecimal collected = BigDecimal.ZERO;
            java.util.Arrays.fill(monthlyTotals, 0);
            for (Payment p : payments) {
                if (!"Completed".equalsIgnoreCase(p.getStatus())) continue;
                LocalDate d;
                try { d = LocalDate.parse(p.getDate()); } catch (Exception e) { continue; }
                if (d.getYear() != year || !scope.contains(d.getMonthValue())) continue;
                BigDecimal amt = DuesCalculator.parseAmount(p.getAmount());
                collected = collected.add(amt);
                monthlyTotals[d.getMonthValue() - 1] += amt.doubleValue();
            }
            collectedVal.setText("$" + collected.setScale(2, RoundingMode.HALF_UP));

            // Outstanding + current-members: only over the scope's months.
            BigDecimal outstanding = BigDecimal.ZERO;
            int eligibleMembers = 0, currentMembers = 0;
            for (Member m : members) {
                boolean hasAnyEligibleMonth = false;
                boolean fullyCurrent = true;
                for (int month : scope) {
                    if (!DuesCalculator.isActive(m, year, month, today)) continue;
                    hasAnyEligibleMonth = true;
                    DuesCalculator.Status status = DuesCalculator.statusFor(m, payments, year, month, today);
                    if (status == DuesCalculator.Status.MISSING || status == DuesCalculator.Status.UNDERPAID) {
                        fullyCurrent = false;
                        BigDecimal owed = DuesCalculator.duesFor(m).subtract(DuesCalculator.amountPaid(m, payments, year, month));
                        if (owed.signum() > 0) outstanding = outstanding.add(owed);
                    }
                }
                if (hasAnyEligibleMonth) {
                    eligibleMembers++;
                    if (fullyCurrent) currentMembers++;
                }
            }
            outstandingVal.setText("$" + outstanding.setScale(2, RoundingMode.HALF_UP));
            statusVal.setText(currentMembers + "/" + eligibleMembers);
            double rate = eligibleMembers == 0 ? 0 : currentMembers * 100.0 / eligibleMembers;
            rateVal.setText(String.format("%.0f%%", rate));

            chart.setTitle("Collections by Month — " + PeriodScope.periodLabel(period, year, mIdx, qIdx));
            // CategoryAxis remembers every category it has ever shown and won't drop
            // stale ones just because the series data changed, so clear it explicitly
            // before repopulating — otherwise e.g. "Jan" lingers on the axis after
            // switching from Yearly to a Monthly view of a later month.
            chart.getData().clear();
            xAxis.getCategories().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (int m : scope) {
                series.getData().add(new XYChart.Data<>(MONTHS_SHORT[m - 1], monthlyTotals[m - 1]));
            }
            chart.getData().add(series);
        };

        periodBox.setOnAction(e -> {
            String p = periodBox.getValue();
            monthBox.setVisible("Monthly".equals(p));
            monthBox.setManaged("Monthly".equals(p));
            quarterBox.setVisible("Quarterly".equals(p));
            quarterBox.setManaged("Quarterly".equals(p));
            refreshHolder[0].run();
        });
        yearBox.setOnAction(e -> refreshHolder[0].run());
        monthBox.setOnAction(e -> refreshHolder[0].run());
        quarterBox.setOnAction(e -> refreshHolder[0].run());

        populateYears.run();
        refreshHolder[0].run();

        SampleData.getPayments().addListener((javafx.collections.ListChangeListener<Payment>) c -> {
            populateYears.run();
            refreshHolder[0].run();
        });
        SampleData.getMembers().addListener((javafx.collections.ListChangeListener<Member>) c -> refreshHolder[0].run());

        exportCsvBtn.setOnAction(e -> {
            int year = yearBox.getValue();
            String period = periodBox.getValue();
            int mIdx = PeriodScope.indexOf(MONTHS_FULL, monthBox.getValue()) + 1;
            int qIdx = PeriodScope.indexOf(QUARTERS, quarterBox.getValue()) + 1;
            List<Integer> scope = PeriodScope.scopeMonths(period, mIdx, qIdx);
            String tag = fileTag(period, year, mIdx, qIdx);
            ReportExporter.exportCsv(root.getScene().getWindow(),
                    "EMCC_Board_Summary_" + tag + ".csv",
                    buildCsv(period, year, mIdx, qIdx, collectedVal.getText(), rateVal.getText(),
                            outstandingVal.getText(), statusVal.getText(), scope, monthlyTotals));
        });

        exportPdfBtn.setOnAction(e -> {
            int year = yearBox.getValue();
            String period = periodBox.getValue();
            int mIdx = PeriodScope.indexOf(MONTHS_FULL, monthBox.getValue()) + 1;
            int qIdx = PeriodScope.indexOf(QUARTERS, quarterBox.getValue()) + 1;
            List<Integer> scope = PeriodScope.scopeMonths(period, mIdx, qIdx);
            String tag = fileTag(period, year, mIdx, qIdx);

            // Snapshot the live chart now, on the FX thread, before the file dialog blocks it.
            // Force CSS + layout synchronously first — otherwise snapshot() can catch the
            // chart before its bars have been styled/laid out and only the axis/gridline
            // background shows up in the image.
            chart.applyCss();
            chart.layout();
            SnapshotParameters snapshotParams = new SnapshotParameters();
            snapshotParams.setFill(javafx.scene.paint.Color.WHITE);
            WritableImage fxImage = chart.snapshot(snapshotParams, null);
            BufferedImage chartImage = SwingFXUtils.fromFXImage(fxImage, null);

            ReportExporter.exportPdf(root.getScene().getWindow(),
                    "EMCC_Board_Summary_" + tag + ".pdf", pdf -> {
                        pdf.orgHeader("Eritrean Muslims Council of Columbus (EMCC)");
                        pdf.title("Annual Financial Summary — " + PeriodScope.periodLabel(period, year, mIdx, qIdx));
                        pdf.generatedOn();
                        pdf.spacer(6);
                        pdf.keyValue("Total Collected", collectedVal.getText());
                        pdf.keyValue("Collection Rate", rateVal.getText());
                        pdf.keyValue("Outstanding", outstandingVal.getText());
                        pdf.keyValue("Members Current", statusVal.getText());
                        pdf.spacer(10);
                        pdf.sectionHeading("Collections by Month");
                        pdf.image(chartImage);
                        pdf.spacer(6);
                        List<String[]> rows = new ArrayList<>();
                        for (int m : scope) {
                            rows.add(new String[]{MONTHS_FULL[m - 1], "$" + BigDecimal.valueOf(monthlyTotals[m - 1]).setScale(2, RoundingMode.HALF_UP)});
                        }
                        pdf.table(new String[]{"Month", "Collected"}, rows, new float[]{1f, 1f});
                    });
        });

        root.getChildren().addAll(title, subtitle, filterRow, kpiRow, actionRow, chart);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    private static String fileTag(String period, int year, int mIdx, int qIdx) {
        return switch (period) {
            case "Monthly"   -> MONTHS_FULL[mIdx - 1] + "_" + year;
            case "Quarterly" -> "Q" + qIdx + "_" + year;
            default          -> String.valueOf(year);
        };
    }

    private static String buildCsv(String period, int year, int mIdx, int qIdx, String collected, String rate,
                                    String outstanding, String current, List<Integer> scope, double[] monthlyTotals) {
        CsvWriter csv = new CsvWriter();
        csv.raw("Eritrean Muslims Council of Columbus (EMCC)");
        csv.raw("Annual Financial Summary — " + PeriodScope.periodLabel(period, year, mIdx, qIdx));
        csv.line("Generated", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        csv.blank();
        csv.line("Total Collected", collected);
        csv.line("Collection Rate", rate);
        csv.line("Outstanding", outstanding);
        csv.line("Members Current", current);
        csv.blank();
        csv.line("Month", "Collected");
        for (int m : scope) {
            csv.line(MONTHS_FULL[m - 1], "$" + BigDecimal.valueOf(monthlyTotals[m - 1]).setScale(2, RoundingMode.HALF_UP));
        }
        return csv.toString();
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
