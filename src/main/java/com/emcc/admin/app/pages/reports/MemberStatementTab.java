package com.emcc.admin.app.pages.reports;

import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.Payment;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.util.CsvWriter;
import com.emcc.admin.app.util.DuesCalculator;
import com.emcc.admin.app.util.ReportExporter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class MemberStatementTab {

    public static Node create() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));

        Label title = new Label("Member Statement");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label("Pick a member to see their full payment history and current standing.");
        subtitle.getStyleClass().add("section-subtitle");

        ComboBox<Member> memberBox = new ComboBox<>();
        memberBox.setPromptText("Select a member…");
        memberBox.setConverter(new StringConverter<>() {
            @Override public String toString(Member m) {
                return m == null ? "" : m.getMembershipId() + " — " + m.getName();
            }
            @Override public Member fromString(String s) { return null; }
        });
        memberBox.setPrefWidth(260);

        ComboBox<String> yearBox = new ComboBox<>();
        yearBox.setPrefWidth(120);

        HBox filterRow = new HBox(12, new Label("Member:"), memberBox, new Label("Year:"), yearBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // ── Member info card ─────────────────────────────────────────────────
        Label nameVal = infoValue();
        Label idVal = infoValue();
        Label joinedVal = infoValue();
        Label duesVal = infoValue();
        Label contactVal = infoValue();

        VBox infoCard = new VBox(8,
                infoRow("Name", nameVal), infoRow("Membership ID", idVal),
                infoRow("Joined", joinedVal), infoRow("Monthly Dues", duesVal),
                infoRow("Contact", contactVal));
        infoCard.getStyleClass().add("activity-card");
        infoCard.setPadding(new Insets(16));
        infoCard.setVisible(false);
        infoCard.setManaged(false);

        Label standingBanner = new Label();
        standingBanner.setWrapText(true);
        standingBanner.setPadding(new Insets(4));
        standingBanner.setVisible(false);
        standingBanner.setManaged(false);

        Label totalPaidVal = infoValue();
        HBox totalsRow = new HBox(infoRow("Total Paid (selected period)", totalPaidVal));
        totalsRow.setVisible(false);
        totalsRow.setManaged(false);

        TableView<Payment> historyTable = new TableView<>();
        historyTable.setPlaceholder(new Label("Select a member to view their payment history."));
        TableColumn<Payment, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(110);
        TableColumn<Payment, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(100);
        TableColumn<Payment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(110);
        TableColumn<Payment, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        methodCol.setPrefWidth(110);
        TableColumn<Payment, String> invoiceCol = new TableColumn<>("Invoice #");
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        invoiceCol.setPrefWidth(140);
        historyTable.getColumns().setAll(List.of(dateCol, amountCol, statusCol, methodCol, invoiceCol));
        historyTable.setPrefHeight(320);

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().add("action-button");
        Button exportPdfBtn = new Button("Export PDF");
        exportPdfBtn.getStyleClass().add("action-button");
        exportCsvBtn.setDisable(true);
        exportPdfBtn.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(10, spacer, exportCsvBtn, exportPdfBtn);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Runnable[] refreshHolder = new Runnable[1];

        Runnable populateMembers = () -> {
            Member selected = memberBox.getValue();
            List<Member> members = SampleData.getMembers().stream()
                    .filter(m -> !m.getMembershipId().isEmpty() || !m.getName().isEmpty())
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .collect(Collectors.toList());
            memberBox.setItems(FXCollections.observableArrayList(members));
            if (selected != null && members.stream().anyMatch(m -> m.getMembershipId().equals(selected.getMembershipId()))) {
                memberBox.setValue(selected);
            }
        };

        Runnable populateYears = () -> {
            Member m = memberBox.getValue();
            Set<String> years = new TreeSet<>((a, b) -> b.compareTo(a));
            years.add("All Years");
            if (m != null) {
                for (Payment p : SampleData.getPayments()) {
                    if (!matches(m, p)) continue;
                    LocalDate d = parseDate(p.getDate());
                    if (d != null) years.add(String.valueOf(d.getYear()));
                }
            }
            String prev = yearBox.getValue();
            yearBox.setItems(FXCollections.observableArrayList(years));
            yearBox.setValue(prev != null && years.contains(prev) ? prev : "All Years");
        };

        memberBox.setOnAction(e -> {
            populateYears.run();
            refreshHolder[0].run();
        });
        yearBox.setOnAction(e -> refreshHolder[0].run());

        refreshHolder[0] = () -> {
            Member m = memberBox.getValue();
            boolean hasMember = m != null;
            infoCard.setVisible(hasMember); infoCard.setManaged(hasMember);
            standingBanner.setVisible(hasMember); standingBanner.setManaged(hasMember);
            totalsRow.setVisible(hasMember); totalsRow.setManaged(hasMember);
            exportCsvBtn.setDisable(!hasMember);
            exportPdfBtn.setDisable(!hasMember);
            if (!hasMember) {
                historyTable.setItems(FXCollections.observableArrayList());
                return;
            }

            nameVal.setText(m.getName());
            idVal.setText(m.getMembershipId());
            joinedVal.setText(m.getCreatedDate() == null || m.getCreatedDate().isEmpty() ? "—" : m.getCreatedDate());
            BigDecimal dues = DuesCalculator.duesFor(m);
            duesVal.setText(dues.signum() > 0 ? "$" + dues.setScale(2, java.math.RoundingMode.HALF_UP) : "Not set");
            String contact = List.of(nullToEmpty(m.getPhoneNumber()), nullToEmpty(m.getEmail())).stream()
                    .filter(s -> !s.isEmpty()).collect(Collectors.joining("  •  "));
            contactVal.setText(contact.isEmpty() ? "—" : contact);

            LocalDate today = LocalDate.now();
            DuesCalculator.Status status = DuesCalculator.statusFor(m, SampleData.getPayments(), today.getYear(), today.getMonthValue(), today);
            String monthName = today.getMonth().name().substring(0, 1) + today.getMonth().name().substring(1).toLowerCase();
            switch (status) {
                case PAID -> {
                    standingBanner.setText("Up to date — dues for " + monthName + " have been paid in full.");
                    standingBanner.getStyleClass().setAll("nonpayer-banner-ok");
                }
                case UNDERPAID -> {
                    BigDecimal owed = dues.subtract(DuesCalculator.amountPaid(m, SampleData.getPayments(), today.getYear(), today.getMonthValue()));
                    standingBanner.setText("Partial payment for " + monthName + " — $" + owed.setScale(2, java.math.RoundingMode.HALF_UP) + " still owed.");
                    standingBanner.getStyleClass().setAll("nonpayer-banner-alert");
                }
                case MISSING -> {
                    standingBanner.setText("No payment recorded yet for " + monthName + " — $" + dues.setScale(2, java.math.RoundingMode.HALF_UP) + " owed.");
                    standingBanner.getStyleClass().setAll("nonpayer-banner-alert");
                }
                case NOT_DUE -> {
                    standingBanner.setText("Not yet due this month (recently joined or dues start later).");
                    standingBanner.getStyleClass().setAll("nonpayer-banner-ok");
                }
                case NO_DUES_SET -> {
                    standingBanner.setText("No monthly dues amount configured for this member — set one in Manage Members.");
                    standingBanner.getStyleClass().setAll("nonpayer-banner-alert");
                }
            }

            String yearFilter = yearBox.getValue();
            List<Payment> history = SampleData.getPayments().stream()
                    .filter(p -> matches(m, p))
                    .filter(p -> "All Years".equals(yearFilter) || matchesYear(p, yearFilter))
                    .sorted((a, b) -> compareDatesDesc(a.getDate(), b.getDate()))
                    .collect(Collectors.toCollection(ArrayList::new));
            historyTable.setItems(FXCollections.observableArrayList(history));

            BigDecimal total = history.stream()
                    .filter(p -> "Completed".equalsIgnoreCase(p.getStatus()))
                    .map(p -> DuesCalculator.parseAmount(p.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPaidVal.setText("$" + total.setScale(2, java.math.RoundingMode.HALF_UP));
        };

        populateMembers.run();
        SampleData.getMembers().addListener((javafx.collections.ListChangeListener<Member>) c -> populateMembers.run());
        SampleData.getPayments().addListener((javafx.collections.ListChangeListener<Payment>) c -> refreshHolder[0].run());

        exportCsvBtn.setOnAction(e -> {
            Member m = memberBox.getValue();
            if (m == null) return;
            ReportExporter.exportCsv(root.getScene().getWindow(),
                    "EMCC_Statement_" + safeFileName(m.getName()) + ".csv",
                    buildCsv(m, historyTable.getItems(), totalPaidVal.getText(), standingBanner.getText()));
        });

        exportPdfBtn.setOnAction(e -> {
            Member m = memberBox.getValue();
            if (m == null) return;
            ReportExporter.exportPdf(root.getScene().getWindow(),
                    "EMCC_Statement_" + safeFileName(m.getName()) + ".pdf", pdf -> {
                        pdf.orgHeader("Eritrean Muslims Council of Columbus (EMCC)");
                        pdf.title("Member Payment Statement");
                        pdf.generatedOn();
                        pdf.spacer(6);
                        pdf.keyValue("Name", nameVal.getText());
                        pdf.keyValue("Membership ID", idVal.getText());
                        pdf.keyValue("Joined", joinedVal.getText());
                        pdf.keyValue("Monthly Dues", duesVal.getText());
                        pdf.keyValue("Contact", contactVal.getText());
                        pdf.spacer(6);
                        pdf.paragraph(standingBanner.getText());
                        pdf.keyValue("Total Paid (selected period)", totalPaidVal.getText());
                        pdf.spacer(10);
                        pdf.sectionHeading("Payment History");
                        List<String[]> rows = historyTable.getItems().stream()
                                .map(p -> new String[]{p.getDate(), p.getAmount(), p.getStatus(), p.getPaymentMethod(), p.getInvoiceNumber()})
                                .toList();
                        pdf.table(new String[]{"Date", "Amount", "Status", "Method", "Invoice #"}, rows,
                                new float[]{1f, 1f, 1f, 1f, 1.4f});
                    });
        });

        root.getChildren().addAll(title, subtitle, filterRow, infoCard, standingBanner, totalsRow, actionRow, historyTable);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    private static String buildCsv(Member m, List<Payment> history, String totalPaid, String standing) {
        CsvWriter csv = new CsvWriter();
        csv.raw("Eritrean Muslims Council of Columbus (EMCC)");
        csv.raw("Member Payment Statement");
        csv.line("Generated", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        csv.blank();
        csv.line("Name", m.getName());
        csv.line("Membership ID", m.getMembershipId());
        csv.line("Joined", m.getCreatedDate());
        csv.line("Standing", standing);
        csv.line("Total Paid (selected period)", totalPaid);
        csv.blank();
        csv.line("Date", "Amount", "Status", "Method", "Invoice #");
        for (Payment p : history) {
            csv.line(p.getDate(), p.getAmount(), p.getStatus(), p.getPaymentMethod(), p.getInvoiceNumber());
        }
        return csv.toString();
    }

    private static boolean matches(Member m, Payment p) {
        String id = m.getMembershipId();
        if (id != null && !id.isEmpty() && id.equals(p.getMemberId())) return true;
        String name = m.getName();
        return name != null && !name.isEmpty() && name.equals(p.getMemberName());
    }

    private static boolean matchesYear(Payment p, String year) {
        LocalDate d = parseDate(p.getDate());
        return d != null && String.valueOf(d.getYear()).equals(year);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    private static int compareDatesDesc(String a, String b) {
        LocalDate da = parseDate(a), db = parseDate(b);
        if (da == null && db == null) return 0;
        if (da == null) return 1;
        if (db == null) return -1;
        return db.compareTo(da);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String safeFileName(String name) {
        return name == null ? "Member" : name.replaceAll("[^a-zA-Z0-9]+", "_");
    }

    private static Label infoValue() {
        Label l = new Label();
        l.setStyle("-fx-font-size: 13px; -fx-text-fill: #1f2a48;");
        return l;
    }

    private static HBox infoRow(String label, Label valueLabel) {
        Label labelLbl = new Label(label + ":");
        labelLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #7d8497; -fx-min-width: 180;");
        HBox row = new HBox(8, labelLbl, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
