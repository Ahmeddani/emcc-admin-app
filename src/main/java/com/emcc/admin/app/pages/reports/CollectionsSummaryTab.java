package com.emcc.admin.app.pages.reports;

import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.Payment;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.util.CsvWriter;
import com.emcc.admin.app.util.DuesCalculator;
import com.emcc.admin.app.util.PeriodScope;
import com.emcc.admin.app.util.ReportExporter;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** The original collection/non-payers report, now one tab among several. */
public class CollectionsSummaryTab {

    private static final String[] MONTHS_SHORT = PeriodScope.MONTHS_SHORT;
    private static final String[] MONTHS_FULL = PeriodScope.MONTHS_FULL;
    private static final String[] QUARTERS = PeriodScope.QUARTERS;

    public static Node create() {
        int currentYear  = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();

        Set<Integer> yearsSet = new TreeSet<>();
        SampleData.getPayments().forEach(p -> {
            try { yearsSet.add(LocalDate.parse(p.getDate()).getYear()); }
            catch (Exception ignored) {}
        });
        if (yearsSet.isEmpty()) yearsSet.add(currentYear);

        ComboBox<Integer> yearBox = new ComboBox<>(FXCollections.observableArrayList(yearsSet));
        yearBox.setValue(yearsSet.contains(currentYear) ? currentYear : yearsSet.iterator().next());

        ComboBox<String> periodBox = new ComboBox<>(
                FXCollections.observableArrayList("Monthly", "Quarterly", "Yearly"));
        periodBox.setValue("Yearly");

        ComboBox<String> monthBox = new ComboBox<>(FXCollections.observableArrayList(MONTHS_FULL));
        monthBox.setValue(MONTHS_FULL[currentMonth - 1]);
        monthBox.setVisible(false);
        monthBox.setManaged(false);

        ComboBox<String> quarterBox = new ComboBox<>(FXCollections.observableArrayList(QUARTERS));
        quarterBox.setValue(QUARTERS[0]);
        quarterBox.setVisible(false);
        quarterBox.setManaged(false);

        Label collectedVal = statNum("$0.00", "#1f7a4d");
        Label paidCountVal = statNum("0/0",   "#1f2a48");
        Label rateVal      = statNum("0%",    "#1f2a48");
        Label nonPayVal    = statNum("0",     "#1f2a48");

        ProgressBar rateBar = new ProgressBar(0);
        rateBar.setPrefWidth(160);
        rateBar.setPrefHeight(7);

        HBox statsRow = new HBox(0,
                statBox(collectedVal, "Total Collected",  true),
                vDivider(),
                statBox(paidCountVal, "Members Paid",     false),
                vDivider(),
                statBox(rateVal,      "Collection Rate",  false, rateBar),
                vDivider(),
                statBox(nonPayVal,    "Non-Payers",       false)
        );
        statsRow.setStyle("-fx-background-color: white; -fx-background-radius: 12;" +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");

        Label gridTitle = new Label();
        gridTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #1f2a48;");

        HBox monthRow = new HBox(8);
        monthRow.setFillHeight(true);

        VBox gridSection = new VBox(14, gridTitle, monthRow);
        gridSection.setPadding(new Insets(20));
        gridSection.setStyle("-fx-background-color: white; -fx-background-radius: 12;" +
                             "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");

        Label nonPayTitle = new Label();
        nonPayTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        TableView<NonPayerRow> nonPayTable = buildNonPayerTable();
        nonPayTable.setVisible(false);
        nonPayTable.setManaged(false);

        VBox nonPaySection = new VBox(12, nonPayTitle, nonPayTable);
        nonPaySection.setPadding(new Insets(20));
        nonPaySection.setStyle("-fx-background-color: white; -fx-background-radius: 12;" +
                               "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 2);");

        Label syncStatus = new Label();
        syncStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa0b2;");

        Runnable refresh = () -> {
            int year     = yearBox.getValue() == null ? currentYear : yearBox.getValue();
            String period = periodBox.getValue();
            int mIdx     = indexOf(MONTHS_FULL, monthBox.getValue()) + 1;
            int qIdx     = indexOf(QUARTERS, quarterBox.getValue()) + 1;

            List<Payment> allPayments = new ArrayList<>(SampleData.getPayments());
            List<Integer> scope = scopeMonths(period, mIdx, qIdx);
            LocalDate today = LocalDate.now();
            List<Member> allMembers = SampleData.getMembers().stream()
                    .filter(m -> !m.getMembershipId().isEmpty() || !m.getName().isEmpty())
                    .collect(Collectors.toList());
            // Eligible for this scope: already joined during at least one scope month,
            // and has a monthly dues amount configured (otherwise we can't judge whether
            // they've paid what they owe).
            List<Member> members = allMembers.stream()
                    .filter(m -> DuesCalculator.hasActiveMonthInScope(m, year, scope, today))
                    .filter(m -> DuesCalculator.duesFor(m).signum() > 0)
                    .collect(Collectors.toList());
            int total = members.size();

            List<Payment> inScope = allPayments.stream()
                    .filter(p -> isCompleted(p) && inYear(p, year) && inMonths(p, scope))
                    .collect(Collectors.toList());

            double collected = inScope.stream().mapToDouble(p -> parseAmount(p.getAmount())).sum();
            // A "non-payer" is anyone missing or short on dues for ANY of their active
            // months in this scope — not just someone who never paid all year. Otherwise
            // a member who paid once in January but missed every month since would still
            // show as "paid" for a Yearly report while the monthly grid shows them red.
            List<Member> nonPayers = members.stream()
                    .filter(m -> !isFullyPaidInScope(m, allPayments, year, scope, today))
                    .collect(Collectors.toList());
            long paid    = total - nonPayers.size();
            double rate  = total == 0 ? 0 : paid * 100.0 / total;
            String rateC = rate >= 100 ? "#1f7a4d" : rate >= 70 ? "#d97706" : "#c73838";

            collectedVal.setText(String.format("$%,.2f", collected));
            paidCountVal.setText(paid + "/" + total);
            rateVal.setText(String.format("%.0f%%", rate));
            rateVal.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: " + rateC + ";");
            rateBar.setProgress(rate / 100.0);
            rateBar.setStyle("-fx-accent: " + rateC + ";");
            nonPayVal.setText(String.valueOf(nonPayers.size()));
            nonPayVal.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: "
                    + (nonPayers.isEmpty() ? "#1f7a4d" : "#c73838") + ";");

            gridTitle.setText("Monthly Overview — " + year
                    + ("Yearly".equals(period) ? "" : "  (" + periodLabel(period, year, mIdx, qIdx) + ")"));
            monthRow.getChildren().clear();
            for (int m = 1; m <= 12; m++) {
                final int fm = m;
                List<Payment> mp = allPayments.stream()
                        .filter(p -> isCompleted(p) && inYear(p, year) && inMonths(p, List.of(fm)))
                        .collect(Collectors.toList());
                double mAmt = mp.stream().mapToDouble(p -> parseAmount(p.getAmount())).sum();
                List<Member> membersActiveThisMonth = allMembers.stream()
                        .filter(mem -> DuesCalculator.isActive(mem, year, fm, today))
                        .filter(mem -> DuesCalculator.duesFor(mem).signum() > 0)
                        .collect(Collectors.toList());
                int monthTotal = membersActiveThisMonth.size();
                long mPaid = membersActiveThisMonth.stream()
                        .filter(mem -> DuesCalculator.statusFor(mem, allPayments, year, fm, today) == DuesCalculator.Status.PAID)
                        .count();
                boolean inScopeMon = scope.contains(m);
                boolean isFuture = year == currentYear && m > currentMonth;
                VBox col = monthCol(MONTHS_SHORT[m - 1], mAmt, (int) mPaid, monthTotal, isFuture, inScopeMon);
                HBox.setHgrow(col, Priority.ALWAYS);
                monthRow.getChildren().add(col);
            }

            if (nonPayers.isEmpty()) {
                nonPayTitle.setText("All members paid — " + periodLabel(period, year, mIdx, qIdx));
                nonPayTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #1f7a4d;");
                nonPayTable.setVisible(false);
                nonPayTable.setManaged(false);
            } else {
                nonPayTitle.setText("Non-Payers — " + periodLabel(period, year, mIdx, qIdx)
                        + " (" + nonPayers.size() + " member" + (nonPayers.size() == 1 ? "" : "s") + ")");
                nonPayTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #c73838;");
                nonPayTable.setVisible(true);
                nonPayTable.setManaged(true);
                List<NonPayerRow> rows = nonPayers.stream()
                        .map(m -> buildNonPayerRow(m, allPayments, year, scope, today))
                        .collect(Collectors.toList());
                nonPayTable.setItems(FXCollections.observableArrayList(rows));
            }
        };

        periodBox.setOnAction(e -> {
            String p = periodBox.getValue();
            monthBox.setVisible("Monthly".equals(p));
            monthBox.setManaged("Monthly".equals(p));
            quarterBox.setVisible("Quarterly".equals(p));
            quarterBox.setManaged("Quarterly".equals(p));
            refresh.run();
        });
        yearBox.setOnAction(e    -> refresh.run());
        monthBox.setOnAction(e   -> refresh.run());
        quarterBox.setOnAction(e -> refresh.run());

        SampleData.getPayments().addListener((javafx.collections.ListChangeListener<Payment>) c -> {
            Set<Integer> updated = new TreeSet<>();
            SampleData.getPayments().forEach(p -> {
                try { updated.add(LocalDate.parse(p.getDate()).getYear()); } catch (Exception ignored) {}
            });
            if (!updated.isEmpty()) {
                Integer sel = yearBox.getValue();
                yearBox.setItems(FXCollections.observableArrayList(updated));
                yearBox.setValue(updated.contains(sel) ? sel
                        : updated.contains(currentYear) ? currentYear
                        : updated.stream().max(Integer::compareTo).orElse(currentYear));
            }
            refresh.run();
        });

        refresh.run();

        syncStatus.setText("Syncing…");
        SampleData.reload(() -> syncStatus.setText("Updated just now"),
                err -> syncStatus.setText(""));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #d7dbe6;" +
                "-fx-border-radius: 8; -fx-background-radius: 8;" +
                "-fx-text-fill: #30334b; -fx-font-size: 12px; -fx-padding: 5 12; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> {
            refreshBtn.setDisable(true);
            syncStatus.setText("Syncing…");
            SampleData.reload(() -> {
                refreshBtn.setDisable(false);
                syncStatus.setText("Updated just now");
            }, err -> {
                refreshBtn.setDisable(false);
                syncStatus.setText("Sync failed");
            });
        });

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().add("action-button");
        Button exportPdfBtn = new Button("Export PDF");
        exportPdfBtn.getStyleClass().add("action-button");

        VBox root = new VBox(16);
        root.setPadding(new Insets(18));

        exportCsvBtn.setOnAction(e -> {
            int year = yearBox.getValue();
            String period = periodBox.getValue();
            int mIdx = indexOf(MONTHS_FULL, monthBox.getValue()) + 1;
            int qIdx = indexOf(QUARTERS, quarterBox.getValue()) + 1;
            String tag = fileTag(period, year, mIdx, qIdx);
            ReportExporter.exportCsv(root.getScene().getWindow(),
                    "EMCC_Report_" + tag + ".csv", buildCsv(year, period, mIdx, qIdx));
        });

        exportPdfBtn.setOnAction(e -> {
            int year = yearBox.getValue();
            String period = periodBox.getValue();
            int mIdx = indexOf(MONTHS_FULL, monthBox.getValue()) + 1;
            int qIdx = indexOf(QUARTERS, quarterBox.getValue()) + 1;
            String tag = fileTag(period, year, mIdx, qIdx);
            ReportExporter.exportPdf(root.getScene().getWindow(),
                    "EMCC_Report_" + tag + ".pdf",
                    pdf -> buildPdf(pdf, year, period, mIdx, qIdx));
        });

        Label pageTitle = new Label("Collections Summary");
        pageTitle.getStyleClass().add("section-title");
        Label pageSubtitle = new Label("Collection rate, monthly overview, and non-payers for a chosen period.");
        pageSubtitle.getStyleClass().add("section-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox filterBar = new HBox(10, spacer,
                syncStatus, refreshBtn, exportCsvBtn, exportPdfBtn,
                new Label("Year:"), yearBox,
                new Label("Period:"), periodBox,
                monthBox, quarterBox);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setStyle("-fx-font-size: 12px; -fx-text-fill: #7d8497;");

        root.getChildren().addAll(pageTitle, pageSubtitle, filterBar, statsRow, gridSection, nonPaySection);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    // ── Month column ──────────────────────────────────────────────────────────

    private static VBox monthCol(String month, double amount, int paid, int total,
                                  boolean isFuture, boolean inScope) {
        Label mLbl = new Label(month);
        mLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #9aa0b2;");

        String amtText = isFuture ? "—" : amount > 0 ? String.format("$%.0f", amount) : "$0";
        Label amtLbl = new Label(amtText);

        String statusText;
        String statusColor;
        String amtColor;
        String bgColor;

        if (isFuture) {
            statusText = "—"; statusColor = "#b0b8cc"; amtColor = "#b0b8cc"; bgColor = "#f8fafd";
        } else if (!inScope) {
            statusText = paid + "/" + total; statusColor = "#b0b8cc"; amtColor = "#b0b8cc"; bgColor = "#f8fafd";
        } else if (total > 0 && paid == total) {
            statusText = "✓ " + paid + "/" + total; statusColor = "#1f7a4d"; amtColor = "#1f7a4d"; bgColor = "#f0faf5";
        } else if (paid > 0) {
            statusText = "◑ " + paid + "/" + total; statusColor = "#d97706"; amtColor = "#d97706"; bgColor = "#fffbeb";
        } else {
            statusText = "✗ 0/" + total; statusColor = "#c73838"; amtColor = "#c73838"; bgColor = "#fff5f5";
        }

        amtLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: " + amtColor + ";");
        Label sLbl = new Label(statusText);
        sLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: " + statusColor + ";");

        VBox col = new VBox(5, mLbl, amtLbl, sLbl);
        col.setPadding(new Insets(10, 6, 10, 6));
        col.setAlignment(Pos.CENTER);
        col.setStyle("-fx-background-color: " + bgColor
                + "; -fx-background-radius: 8; -fx-border-radius: 8;"
                + (inScope && !isFuture ? "; -fx-border-color: " + (
                        total > 0 && paid == total ? "#a8dfc4" :
                        paid > 0 ? "#f6d860" : "#f5b8b8") + "; -fx-border-width: 1;" : ""));
        return col;
    }

    // ── Non-payer table ──────────────────────────────────────────────────────

    /** One non-payer's summary for the currently selected scope: which months they're behind on, and how much. */
    private record NonPayerRow(String memberId, String name, String phone, String monthsBehind, String amountDue) {}

    private static TableView<NonPayerRow> buildNonPayerTable() {
        TableView<NonPayerRow> table = new TableView<>();
        table.setPlaceholder(new Label("No non-payers."));

        TableColumn<NonPayerRow, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().memberId()));
        idCol.setPrefWidth(80);

        TableColumn<NonPayerRow, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        nameCol.setPrefWidth(160);

        TableColumn<NonPayerRow, String> monthsCol = new TableColumn<>("Months Behind");
        monthsCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().monthsBehind()));
        monthsCol.setPrefWidth(260);
        monthsCol.setCellFactory(c -> new TableCell<>() {
            { setWrapText(true); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });

        TableColumn<NonPayerRow, String> amountCol = new TableColumn<>("Amount Due");
        amountCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().amountDue()));
        amountCol.setPrefWidth(110);
        amountCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<NonPayerRow, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().phone()));
        phoneCol.setPrefWidth(120);

        table.getColumns().setAll(List.of(idCol, nameCol, monthsCol, amountCol, phoneCol));
        table.setPrefHeight(220);
        table.setFixedCellSize(-1);
        return table;
    }

    private static NonPayerRow buildNonPayerRow(Member member, List<Payment> payments, int year,
                                                 List<Integer> scope, LocalDate today) {
        List<String> behindMonths = new ArrayList<>();
        BigDecimal amountDue = BigDecimal.ZERO;
        for (int month : scope) {
            if (!DuesCalculator.isActive(member, year, month, today)) continue;
            DuesCalculator.Status status = DuesCalculator.statusFor(member, payments, year, month, today);
            if (status == DuesCalculator.Status.MISSING || status == DuesCalculator.Status.UNDERPAID) {
                String label = MONTHS_SHORT[month - 1] + (status == DuesCalculator.Status.UNDERPAID ? " (partial)" : "");
                behindMonths.add(label);
                BigDecimal owed = DuesCalculator.duesFor(member).subtract(DuesCalculator.amountPaid(member, payments, year, month));
                if (owed.signum() > 0) amountDue = amountDue.add(owed);
            }
        }
        return new NonPayerRow(
                member.getMembershipId(),
                member.getName(),
                member.getPhoneNumber() == null || member.getPhoneNumber().isEmpty() ? "—" : member.getPhoneNumber(),
                behindMonths.isEmpty() ? "—" : String.join(", ", behindMonths),
                "$" + amountDue.setScale(2, RoundingMode.HALF_UP));
    }

    // ── Stat box ──────────────────────────────────────────────────────────────

    private static VBox statBox(Label valueLabel, String caption, boolean first, Node... extras) {
        Label cap = new Label(caption);
        cap.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa0b2; -fx-font-weight: 600;");
        VBox box = new VBox(4, cap, valueLabel);
        for (Node e : extras) { box.getChildren().add(e); VBox.setMargin(e, new Insets(4, 0, 0, 0)); }
        box.setPadding(new Insets(18, 28, 18, first ? 24 : 20));
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static Label statNum(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: " + color + ";");
        return l;
    }

    private static Region vDivider() {
        Region d = new Region();
        d.setPrefWidth(1);
        d.setMinWidth(1);
        d.setMaxWidth(1);
        d.setStyle("-fx-background-color: #e4e7ef;");
        return d;
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private static String fileTag(String period, int year, int mIdx, int qIdx) {
        return switch (period) {
            case "Monthly"   -> MONTHS_FULL[mIdx - 1] + "_" + year;
            case "Quarterly" -> "Q" + qIdx + "_" + year;
            default          -> String.valueOf(year);
        };
    }

    private static String buildCsv(int year, String period, int mIdx, int qIdx) {
        LocalDate today = LocalDate.now();
        List<Payment> allPayments = new ArrayList<>(SampleData.getPayments());
        List<Member> allMembers = SampleData.getMembers().stream()
                .filter(m -> !m.getMembershipId().isEmpty() || !m.getName().isEmpty())
                .collect(Collectors.toList());
        List<Integer> scope = scopeMonths(period, mIdx, qIdx);
        List<Member> members = allMembers.stream()
                .filter(m -> DuesCalculator.hasActiveMonthInScope(m, year, scope, today))
                .filter(m -> DuesCalculator.duesFor(m).signum() > 0)
                .collect(Collectors.toList());
        int total = members.size();
        List<Payment> inScope = allPayments.stream()
                .filter(p -> isCompleted(p) && inYear(p, year) && inMonths(p, scope))
                .collect(Collectors.toList());
        double collected = inScope.stream().mapToDouble(p -> parseAmount(p.getAmount())).sum();
        List<Member> nonPayers = members.stream()
                .filter(m -> !isFullyPaidInScope(m, allPayments, year, scope, today))
                .collect(Collectors.toList());
        long paid = total - nonPayers.size();
        double rate = total == 0 ? 0 : paid * 100.0 / total;
        String pLabel = periodLabel(period, year, mIdx, qIdx);

        CsvWriter csv = new CsvWriter();
        csv.raw("Eritrean Muslims Council of Columbus (EMCC)");
        csv.raw("Financial Report — " + pLabel);
        csv.line("Generated", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        csv.blank();
        csv.raw("SUMMARY");
        csv.line("Total Collected", String.format("$%,.2f", collected));
        csv.line("Payments Made", inScope.size());
        csv.line("Members Paid", paid + " of " + total);
        csv.line("Collection Rate", String.format("%.0f%%", rate));
        csv.line("Non-Payers", nonPayers.size());
        csv.blank();

        if (!"Monthly".equals(period)) {
            csv.raw("MONTHLY BREAKDOWN");
            csv.line("Month", "Collected", "Members Paid", "Non-Payers", "Rate");
            for (int m : scope) {
                MonthStats stats = monthStats(allPayments, allMembers, year, m, today);
                csv.line(MONTHS_FULL[m - 1] + " " + year,
                        String.format("$%,.2f", stats.collected),
                        stats.paid + " of " + stats.total,
                        stats.total - stats.paid,
                        stats.total == 0 ? "—" : String.format("%.0f%%", stats.paid * 100.0 / stats.total));
            }
            csv.blank();
        }

        csv.raw("PAYMENT DETAILS");
        csv.line("Member ID", "Member Name", "Amount", "Date", "Payment Method", "Invoice #", "Status");
        inScope.stream()
                .sorted((a, b) -> (a.getDate() + a.getMemberName()).compareTo(b.getDate() + b.getMemberName()))
                .forEach(p -> csv.line(p.getMemberId(), p.getMemberName(), p.getAmount(), p.getDate(),
                        p.getPaymentMethod(), p.getInvoiceNumber(), p.getStatus()));
        csv.blank();

        csv.raw("NON-PAYERS");
        csv.line("Member ID", "Full Name", "Months Behind", "Amount Due", "Phone", "Email");
        if (nonPayers.isEmpty()) {
            csv.raw("(All members paid)");
        } else {
            nonPayers.forEach(m -> {
                NonPayerRow row = buildNonPayerRow(m, allPayments, year, scope, today);
                csv.line(row.memberId(), row.name(), row.monthsBehind(), row.amountDue(), row.phone(), m.getEmail());
            });
        }
        return csv.toString();
    }

    private static void buildPdf(com.emcc.admin.app.util.PdfReportBuilder pdf, int year, String period, int mIdx, int qIdx) throws Exception {
        LocalDate today = LocalDate.now();
        List<Payment> allPayments = new ArrayList<>(SampleData.getPayments());
        List<Member> allMembers = SampleData.getMembers().stream()
                .filter(m -> !m.getMembershipId().isEmpty() || !m.getName().isEmpty())
                .collect(Collectors.toList());
        List<Integer> scope = scopeMonths(period, mIdx, qIdx);
        List<Member> members = allMembers.stream()
                .filter(m -> DuesCalculator.hasActiveMonthInScope(m, year, scope, today))
                .filter(m -> DuesCalculator.duesFor(m).signum() > 0)
                .collect(Collectors.toList());
        int total = members.size();
        List<Payment> inScope = allPayments.stream()
                .filter(p -> isCompleted(p) && inYear(p, year) && inMonths(p, scope))
                .collect(Collectors.toList());
        double collected = inScope.stream().mapToDouble(p -> parseAmount(p.getAmount())).sum();
        List<Member> nonPayers = members.stream()
                .filter(m -> !isFullyPaidInScope(m, allPayments, year, scope, today))
                .collect(Collectors.toList());
        long paid = total - nonPayers.size();
        double rate = total == 0 ? 0 : paid * 100.0 / total;

        pdf.orgHeader("Eritrean Muslims Council of Columbus (EMCC)");
        pdf.title("Financial Report — " + periodLabel(period, year, mIdx, qIdx));
        pdf.generatedOn();
        pdf.spacer(6);
        pdf.keyValue("Total Collected", String.format("$%,.2f", collected));
        pdf.keyValue("Payments Made", String.valueOf(inScope.size()));
        pdf.keyValue("Members Paid", paid + " of " + total);
        pdf.keyValue("Collection Rate", String.format("%.0f%%", rate));
        pdf.keyValue("Non-Payers", String.valueOf(nonPayers.size()));
        pdf.spacer(10);

        if (!"Monthly".equals(period)) {
            pdf.sectionHeading("Monthly Breakdown");
            List<String[]> rows = new ArrayList<>();
            for (int m : scope) {
                MonthStats stats = monthStats(allPayments, allMembers, year, m, today);
                rows.add(new String[]{MONTHS_FULL[m - 1], String.format("$%,.2f", stats.collected),
                        stats.paid + " of " + stats.total,
                        stats.total == 0 ? "-" : String.format("%.0f%%", stats.paid * 100.0 / stats.total)});
            }
            pdf.table(new String[]{"Month", "Collected", "Paid", "Rate"}, rows, new float[]{1.3f, 1.2f, 1.2f, 1f});
            pdf.spacer(10);
        }

        pdf.sectionHeading("Non-Payers");
        if (nonPayers.isEmpty()) {
            pdf.paragraph("All members paid for this period.");
        } else {
            List<String[]> rows = nonPayers.stream()
                    .map(m -> buildNonPayerRow(m, allPayments, year, scope, today))
                    .map(row -> new String[]{row.memberId(), row.name(), row.monthsBehind(), row.amountDue(), row.phone()})
                    .toList();
            pdf.table(new String[]{"ID", "Name", "Months Behind", "Amount Due", "Phone"}, rows,
                    new float[]{0.7f, 1.4f, 1.8f, 0.9f, 1.1f});
        }
    }

    private static MonthStats monthStats(List<Payment> allPayments, List<Member> allMembers, int year, int month, LocalDate today) {
        final int fm = month;
        List<Payment> mp = allPayments.stream()
                .filter(p -> isCompleted(p) && inYear(p, year) && inMonths(p, List.of(fm)))
                .collect(Collectors.toList());
        double mc = mp.stream().mapToDouble(p -> parseAmount(p.getAmount())).sum();
        List<Member> membersActiveThisMonth = allMembers.stream()
                .filter(mem -> DuesCalculator.isActive(mem, year, fm, today))
                .filter(mem -> DuesCalculator.duesFor(mem).signum() > 0)
                .collect(Collectors.toList());
        int monthTotal = membersActiveThisMonth.size();
        long mPaid = membersActiveThisMonth.stream()
                .filter(mem -> DuesCalculator.statusFor(mem, allPayments, year, fm, today) == DuesCalculator.Status.PAID)
                .count();
        return new MonthStats(mc, (int) mPaid, monthTotal);
    }

    /** True if the member owes nothing outstanding for any of their active months within scope. */
    private static boolean isFullyPaidInScope(Member member, List<Payment> payments, int year,
                                               List<Integer> scope, LocalDate today) {
        for (int month : scope) {
            if (!DuesCalculator.isActive(member, year, month, today)) continue;
            DuesCalculator.Status status = DuesCalculator.statusFor(member, payments, year, month, today);
            if (status == DuesCalculator.Status.MISSING || status == DuesCalculator.Status.UNDERPAID) return false;
        }
        return true;
    }

    private record MonthStats(double collected, int paid, int total) {}

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static List<Integer> scopeMonths(String period, int month, int quarter) {
        return PeriodScope.scopeMonths(period, month, quarter);
    }

    private static boolean isCompleted(Payment p) {
        return "Completed".equalsIgnoreCase(p.getStatus());
    }

    private static boolean inYear(Payment p, int year) {
        try { return LocalDate.parse(p.getDate()).getYear() == year; }
        catch (Exception e) { return false; }
    }

    private static boolean inMonths(Payment p, List<Integer> months) {
        try { return months.contains(LocalDate.parse(p.getDate()).getMonthValue()); }
        catch (Exception e) { return false; }
    }

    private static double parseAmount(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Double.parseDouble(s.replace("$", "").replace(",", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int indexOf(String[] arr, String val) {
        return PeriodScope.indexOf(arr, val);
    }

    private static String periodLabel(String period, int year, int month, int quarter) {
        return PeriodScope.periodLabel(period, year, month, quarter);
    }
}
