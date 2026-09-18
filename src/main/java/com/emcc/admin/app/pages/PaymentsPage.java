package com.emcc.admin.app.pages;

import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.Payment;
import com.emcc.admin.app.model.PlaidConnectInfo;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.model.UnmatchedPayment;
import com.emcc.admin.app.service.ApiClient;
import com.emcc.admin.app.util.DuesCalculator;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.awt.Desktop;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PaymentsPage {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] MONTH_NAMES = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] MONTH_FILTERS = {"All", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    public static Node createContent(BiConsumer<String, Node> detailUpdater, BiConsumer<String, String> messageUpdater) {
        VBox root = new VBox(14);
        root.setPadding(new Insets(18));

        Label header = new Label("Manage Payments");
        header.getStyleClass().add("section-title");

        // ── Bank integration toolbar ──────────────────────────────────────────
        Label bankStatusLabel = new Label("Checking bank connection…");
        bankStatusLabel.getStyleClass().add("detail-message");

        Button connectButton = new Button("Connect Bank");
        connectButton.setVisible(false);
        connectButton.setManaged(false);

        Button syncButton = new Button("Sync from Bank");
        syncButton.setVisible(false);
        syncButton.setManaged(false);

        Label syncResultLabel = new Label();
        syncResultLabel.getStyleClass().add("detail-message");

        HBox bankBar = new HBox(10, bankStatusLabel, connectButton, syncButton, syncResultLabel);
        bankBar.setPadding(new Insets(0, 0, 4, 0));

        // Check bank connection status on load
        runAsync(ApiClient::isPlaidConnected, connected -> {
            if (connected) {
                runAsync(ApiClient::getConnectedInstitution, name -> {
                    bankStatusLabel.setText("Bank: " + (name.isEmpty() ? "Connected" : name));
                    syncButton.setVisible(true);
                    syncButton.setManaged(true);
                }, err -> {
                    bankStatusLabel.setText("Bank: Connected");
                    syncButton.setVisible(true);
                    syncButton.setManaged(true);
                });
            } else {
                bankStatusLabel.setText("No bank connected.");
                connectButton.setVisible(true);
                connectButton.setManaged(true);
            }
        }, err -> {
            bankStatusLabel.setText("No bank connected.");
            connectButton.setVisible(true);
            connectButton.setManaged(true);
        });

        // Unmatched transactions panel
        ObservableList<UnmatchedPayment> unmatchedList = FXCollections.observableArrayList();
        TableView<UnmatchedPayment> unmatchedTable = buildUnmatchedTable(unmatchedList, syncResultLabel);
        Label unmatchedHeader = new Label("Unmatched Bank Transactions — assign these to members:");
        unmatchedHeader.getStyleClass().add("card-title");
        VBox unmatchedPanel = new VBox(8, unmatchedHeader, unmatchedTable);
        unmatchedPanel.setPadding(new Insets(12));
        unmatchedPanel.getStyleClass().add("activity-card");
        unmatchedPanel.setVisible(false);
        unmatchedPanel.setManaged(false);

        Runnable refreshUnmatched = () -> runAsync(ApiClient::getUnmatchedPayments, items -> {
            unmatchedList.setAll(items);
            unmatchedPanel.setVisible(!items.isEmpty());
            unmatchedPanel.setManaged(!items.isEmpty());
        }, err -> {});

        // Sync action — pull transactions from Plaid and reload payments
        Runnable runSync = () -> {
            syncButton.setDisable(true);
            syncButton.setText("Syncing…");
            syncResultLabel.setText("");
            runAsync(ApiClient::syncPayments, counts -> {
                syncButton.setDisable(false);
                syncButton.setText("Sync from Bank");
                syncResultLabel.setText(counts[0] + " matched, " + counts[1] + " unmatched, " + counts[2] + " already processed.");
                SampleData.reload(null, err -> {});
                refreshUnmatched.run();
            }, err -> {
                syncButton.setDisable(false);
                syncButton.setText("Sync from Bank");
                syncResultLabel.setText("Sync failed: " + err.getMessage());
            });
        };
        syncButton.setOnAction(e -> runSync.run());

        // Connect Bank action. Sandbox uses an embedded in-app window (fast, no
        // real bank involved). Development/Production open the system browser
        // instead — real banks route through OAuth login pages that often refuse
        // to load inside an embedded WebView, so a real browser is required there.
        connectButton.setOnAction(e -> {
            connectButton.setDisable(true);
            connectButton.setText("Opening…");
            runAsync(ApiClient::getPlaidConnectInfo, info -> {
                connectButton.setDisable(false);
                connectButton.setText("Connect Bank");
                if (info.isSandbox()) {
                    openEmbeddedBankConnect(info.getUrl(), connected -> {
                        if (connected) {
                            bankStatusLabel.setText("Bank connected! Syncing…");
                            connectButton.setVisible(false);
                            connectButton.setManaged(false);
                            syncButton.setVisible(true);
                            syncButton.setManaged(true);
                            runSync.run();
                        }
                    });
                } else {
                    try {
                        Desktop.getDesktop().browse(URI.create(info.getUrl()));
                        bankStatusLabel.setText("Complete the connection in your browser, then click Sync.");
                        syncButton.setVisible(true);
                        syncButton.setManaged(true);
                    } catch (Exception ex) {
                        bankStatusLabel.setText("Could not open browser: " + ex.getMessage());
                    }
                }
            }, err -> {
                connectButton.setDisable(false);
                connectButton.setText("Connect Bank");
                bankStatusLabel.setText("Error: " + err.getMessage());
            });
        });

        refreshUnmatched.run();

        // ─────────────────────────────────────────────────────────────────────
        Label summaryLabel = new Label();
        summaryLabel.getStyleClass().add("section-subtitle");

        TableView<MemberMonthlySummary> summaryTable = new TableView<>();
        summaryTable.getColumns().add(createTextColumn("Member ID", MemberMonthlySummary::getMemberId, 120));
        summaryTable.getColumns().add(createTextColumn("Member", MemberMonthlySummary::getMemberName, 140));
        for (int monthIndex = 1; monthIndex <= MONTH_NAMES.length; monthIndex++) {
            summaryTable.getColumns().add(createStatusColumn(MONTH_NAMES[monthIndex - 1], getMonthMapper(monthIndex), 90));
        }
        summaryTable.getColumns().add(createTextColumn("Total", MemberMonthlySummary::getYearTotal, 100));
        summaryTable.setPrefHeight(260);

        Label recommendation = new Label(
                "The grid below shows each member's dues status per month: Paid, Underpaid (partial amount received), " +
                        "Missing (nothing received), or a dash for months before they joined or that haven't arrived yet. " +
                        "\"Sync from Bank\" above pulls new deposits and auto-matches them to members by name.");
        recommendation.setWrapText(true);
        recommendation.getStyleClass().add("detail-message");

        ObservableList<Payment> allPayments = SampleData.getPayments();
        ObservableList<Payment> filteredPayments = FXCollections.observableArrayList();

        Set<Integer> availableYears = getAvailableYears(allPayments);
        int initialYear = availableYears.contains(LocalDate.now().getYear()) ? LocalDate.now().getYear()
                : availableYears.stream().findFirst().orElse(LocalDate.now().getYear());

        ComboBox<Integer> yearFilter = new ComboBox<>(FXCollections.observableArrayList(availableYears));
        yearFilter.setValue(initialYear);
        ComboBox<String> monthFilter = new ComboBox<>(FXCollections.observableArrayList(MONTH_FILTERS));
        monthFilter.setValue("All");

        yearFilter.setOnAction(event -> updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel));
        monthFilter.setOnAction(event -> updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel));

        HBox filterRow = new HBox(12, new Label("Year:"), yearFilter, new Label("Month:"), monthFilter);
        filterRow.setPadding(new Insets(0, 0, 12, 0));

        TableView<Payment> paymentsTable = new TableView<>();
        paymentsTable.setPlaceholder(new Label("No payments found"));

        TableColumn<Payment, String> idCol = new TableColumn<>("Member ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("memberId"));
        idCol.setCellFactory(TextFieldTableCell.forTableColumn());
        idCol.setOnEditCommit(event -> {
            event.getRowValue().setMemberId(event.getNewValue());
            paymentsTable.refresh();
            updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel);
        });

        TableColumn<Payment, String> memberCol = new TableColumn<>("Member");
        memberCol.setCellValueFactory(new PropertyValueFactory<>("memberName"));
        memberCol.setCellFactory(TextFieldTableCell.forTableColumn());
        memberCol.setOnEditCommit(event -> {
            event.getRowValue().setMemberName(event.getNewValue());
            paymentsTable.refresh();
            updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel);
        });

        TableColumn<Payment, String> amountCol = new TableColumn<>("Monthly Fee");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setCellFactory(TextFieldTableCell.forTableColumn());
        amountCol.setOnEditCommit(event -> {
            event.getRowValue().setAmount(event.getNewValue());
            paymentsTable.refresh();
        });

        TableColumn<Payment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(TextFieldTableCell.forTableColumn());
        statusCol.setOnEditCommit(event -> {
            event.getRowValue().setStatus(event.getNewValue());
            paymentsTable.refresh();
            updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel);
        });

        TableColumn<Payment, String> dateCol = new TableColumn<>("Payment Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setCellFactory(TextFieldTableCell.forTableColumn());
        dateCol.setOnEditCommit(event -> {
            event.getRowValue().setDate(event.getNewValue());
            paymentsTable.refresh();
            updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel);
        });

        TableColumn<Payment, String> monthCol = new TableColumn<>("Month");
        monthCol.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getMonth()));
        monthCol.setPrefWidth(80);

        TableColumn<Payment, String> methodCol = new TableColumn<>("Payment Method");
        methodCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        methodCol.setCellFactory(TextFieldTableCell.forTableColumn());
        methodCol.setOnEditCommit(event -> {
            event.getRowValue().setPaymentMethod(event.getNewValue());
            paymentsTable.refresh();
        });

        TableColumn<Payment, String> invoiceCol = new TableColumn<>("Invoice #");
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        invoiceCol.setCellFactory(TextFieldTableCell.forTableColumn());
        invoiceCol.setOnEditCommit(event -> {
            event.getRowValue().setInvoiceNumber(event.getNewValue());
            paymentsTable.refresh();
        });

        paymentsTable.getColumns().setAll(idCol, memberCol, amountCol, statusCol, dateCol, monthCol, methodCol, invoiceCol);
        paymentsTable.setEditable(true);
        paymentsTable.setPrefHeight(320);
        paymentsTable.setItems(filteredPayments);

        Button addPaymentButton = new Button("Add Payment");
        addPaymentButton.setOnAction(event -> {
            Payment newPayment = new Payment("", "", "", "Pending", "", "ACH", "");
            allPayments.add(newPayment);
            filteredPayments.add(newPayment);
            paymentsTable.getSelectionModel().select(newPayment);
            paymentsTable.scrollTo(newPayment);
        });

        Button removePaymentButton = new Button("Remove Payment");
        removePaymentButton.disableProperty().bind(paymentsTable.getSelectionModel().selectedItemProperty().isNull());
        removePaymentButton.setOnAction(event -> {
            Payment selected = paymentsTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Remove payment for \"" + selected.getMemberName() + "\"? This cannot be undone.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Remove Payment");
            confirm.setHeaderText(null);
            confirm.showAndWait().filter(btn -> btn == ButtonType.OK).ifPresent(btn -> {
                Long dbId = selected.getId();
                allPayments.remove(selected);
                filteredPayments.remove(selected);
                paymentsTable.getSelectionModel().clearSelection();
                updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel);
                if (dbId != null) {
                    runVoidAsync(() -> ApiClient.deletePayment(dbId), null, null);
                }
            });
        });

        HBox actionRow = new HBox(10, addPaymentButton, removePaymentButton);
        actionRow.setPadding(new Insets(4, 0, 12, 0));

        messageUpdater.accept("Manage Payments", "This page shows member payments and provides audit-ready year/month filtering.");

        paymentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldPayment, newPayment) -> {
            if (newPayment != null) {
                String title = newPayment.getMemberName().isEmpty() ? "Add Payment" : newPayment.getMemberName();
                detailUpdater.accept(title, createPaymentDetailForm(newPayment, paymentsTable,
                        () -> updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel)));
            } else {
                messageUpdater.accept("Manage Payments", "This page shows member payments and provides audit-ready year/month filtering.");
            }
        });

        // Re-populate year filter whenever the master list changes (e.g., after API reload)
        allPayments.addListener((javafx.collections.ListChangeListener<Payment>) change -> {
            Set<Integer> years = getAvailableYears(allPayments);
            yearFilter.setItems(FXCollections.observableArrayList(years));
            if (!years.isEmpty() && !years.contains(yearFilter.getValue())) {
                yearFilter.setValue(years.stream().findFirst().orElse(LocalDate.now().getYear()));
            }
            updateFilteredPayments(allPayments, filteredPayments, yearFilter.getValue(), monthFilter.getValue(), summaryTable, summaryLabel);
        });

        updateFilteredPayments(allPayments, filteredPayments, initialYear, "All", summaryTable, summaryLabel);

        root.getChildren().addAll(header, bankBar, summaryLabel, filterRow, summaryTable, recommendation, actionRow, paymentsTable, unmatchedPanel);
        return root;
    }

    private static TableColumn<MemberMonthlySummary, String> createTextColumn(String title,
                                                                               Function<MemberMonthlySummary, String> mapper,
                                                                               double width) {
        TableColumn<MemberMonthlySummary, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    private static TableColumn<MemberMonthlySummary, String> createStatusColumn(String title,
                                                                                 Function<MemberMonthlySummary, String> mapper,
                                                                                 double width) {
        TableColumn<MemberMonthlySummary, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); setStyle(""); return; }
                if (item == null || item.isBlank()) {
                    setText("–");
                    setStyle("-fx-text-fill: #b0b8cc;");
                    return;
                }
                setText(item);
                setStyle(switch (item) {
                    case "Paid" -> "-fx-text-fill: #1f7a4d; -fx-font-weight: bold;";
                    case "Underpaid" -> "-fx-text-fill: #d97706; -fx-font-weight: bold;";
                    case "N/A" -> "-fx-text-fill: #9aa0b2; -fx-font-style: italic;";
                    default -> "-fx-text-fill: #c73838; -fx-font-weight: bold;"; // Missing
                });
            }
        });
        col.setPrefWidth(width);
        return col;
    }

    private static Function<MemberMonthlySummary, String> getMonthMapper(int month) {
        return switch (month) {
            case 1 -> MemberMonthlySummary::getJan;
            case 2 -> MemberMonthlySummary::getFeb;
            case 3 -> MemberMonthlySummary::getMar;
            case 4 -> MemberMonthlySummary::getApr;
            case 5 -> MemberMonthlySummary::getMay;
            case 6 -> MemberMonthlySummary::getJun;
            case 7 -> MemberMonthlySummary::getJul;
            case 8 -> MemberMonthlySummary::getAug;
            case 9 -> MemberMonthlySummary::getSep;
            case 10 -> MemberMonthlySummary::getOct;
            case 11 -> MemberMonthlySummary::getNov;
            case 12 -> MemberMonthlySummary::getDec;
            default -> s -> "Not Paid";
        };
    }

    private static void updateFilteredPayments(ObservableList<Payment> all,
                                               ObservableList<Payment> filtered,
                                               int year, String monthFilter,
                                               TableView<MemberMonthlySummary> summaryTable,
                                               Label summaryLabel) {
        filtered.setAll(getFilteredPayments(all, year, monthFilter));
        summaryTable.setItems(createMonthlySummary(all, year));
        summaryLabel.setText("Monthly payment status for " + year);
    }

    private static ObservableList<Payment> getFilteredPayments(List<Payment> payments, int year, String monthFilter) {
        return payments.stream()
                .filter(p -> {
                    if (p.getDate() == null || p.getDate().isEmpty()) return false;
                    try {
                        LocalDate d = LocalDate.parse(p.getDate(), DATE_FORMAT);
                        if (d.getYear() != year) return false;
                        if (monthFilter == null || monthFilter.equals("All")) return true;
                        return MONTH_NAMES[d.getMonthValue() - 1].equals(monthFilter);
                    } catch (Exception e) { return false; }
                })
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
    }

    private static ObservableList<MemberMonthlySummary> createMonthlySummary(List<Payment> payments, int year) {
        LocalDate today = LocalDate.now();
        Map<String, MemberMonthlySummary> map = new LinkedHashMap<>();

        for (Member member : SampleData.getMembers()) {
            if (member.getMembershipId().isEmpty() && member.getName().isEmpty()) continue;
            MemberMonthlySummary summary = new MemberMonthlySummary(member.getMembershipId(), member.getName());
            for (int month = 1; month <= 12; month++) {
                DuesCalculator.Status status = DuesCalculator.statusFor(member, payments, year, month, today);
                BigDecimal paid = DuesCalculator.amountPaid(member, payments, year, month);
                summary.setMonthStatus(month, statusLabel(status), paid);
            }
            map.put(member.getMembershipId(), summary);
        }

        // Payments that don't match any current member (e.g. a mistyped or removed member)
        // still need to be visible for auditing, so surface them as their own row.
        for (Payment p : payments) {
            if (!"Completed".equalsIgnoreCase(p.getStatus())) continue;
            if (p.getDate() == null || p.getDate().isEmpty()) continue;
            LocalDate d;
            try { d = LocalDate.parse(p.getDate(), DATE_FORMAT); } catch (Exception e) { continue; }
            if (d.getYear() != year) continue;
            boolean matchesKnownMember = SampleData.getMembers().stream().anyMatch(m ->
                    (!m.getMembershipId().isEmpty() && m.getMembershipId().equals(p.getMemberId()))
                    || (!m.getName().isEmpty() && m.getName().equals(p.getMemberName())));
            if (matchesKnownMember) continue;
            String key = (p.getMemberId() != null && !p.getMemberId().isEmpty()) ? p.getMemberId() : p.getMemberName();
            map.computeIfAbsent(key, id -> new MemberMonthlySummary(key, p.getMemberName()))
                    .setMonthStatus(d.getMonthValue(), "Paid", DuesCalculator.parseAmount(p.getAmount()));
        }

        map.values().forEach(MemberMonthlySummary::computeTotal);
        return FXCollections.observableArrayList(map.values());
    }

    private static String statusLabel(DuesCalculator.Status status) {
        return switch (status) {
            case PAID -> "Paid";
            case UNDERPAID -> "Underpaid";
            case MISSING -> "Missing";
            case NO_DUES_SET -> "N/A";
            case NOT_DUE -> "";
        };
    }

    private static VBox createPaymentDetailForm(Payment payment, TableView<Payment> table, Runnable refreshSummary) {
        Label memberIdLabel = new Label("Member ID");
        TextField memberIdField = new TextField(payment.getMemberId());
        Label memberLabel = new Label("Member");
        TextField memberField = new TextField(payment.getMemberName());
        Label invoiceLabel = new Label("Invoice Number");
        TextField invoiceField = new TextField(payment.getInvoiceNumber());
        Label amountLabel = new Label("Amount");
        TextField amountField = new TextField(payment.getAmount());
        Label methodLabel = new Label("Payment Method");
        TextField methodField = new TextField(payment.getPaymentMethod());
        Label statusLabel = new Label("Status");
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Pending", "Completed");
        statusCombo.setValue(payment.getStatus() == null || payment.getStatus().isEmpty() ? "Pending" : payment.getStatus());
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        Label dateLabel = new Label("Date");
        TextField dateField = new TextField(payment.getDate());

        Label saveMessage = new Label();
        saveMessage.getStyleClass().add("detail-message");

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            payment.setMemberId(memberIdField.getText());
            payment.setMemberName(memberField.getText());
            payment.setInvoiceNumber(invoiceField.getText());
            payment.setAmount(amountField.getText());
            payment.setPaymentMethod(methodField.getText());
            payment.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : "Pending");
            payment.setDate(dateField.getText());
            table.refresh();
            refreshSummary.run();

            saveButton.setDisable(true);
            saveMessage.setText("Saving…");
            runAsync(
                    () -> ApiClient.savePayment(payment),
                    saved -> {
                        payment.setId(saved.getId());
                        saveButton.setDisable(false);
                        saveMessage.setText("Payment saved.");
                    },
                    err -> { saveButton.setDisable(false); saveMessage.setText("Save failed: " + err.getMessage()); }
            );
        });

        VBox form = new VBox(12,
                memberIdLabel, memberIdField,
                memberLabel, memberField,
                invoiceLabel, invoiceField,
                amountLabel, amountField,
                methodLabel, methodField,
                statusLabel, statusCombo,
                dateLabel, dateField,
                saveButton, saveMessage);
        form.setPadding(new Insets(8));
        form.getStyleClass().add("detail-form");
        return form;
    }

    private static Set<Integer> getAvailableYears(List<Payment> payments) {
        return payments.stream()
                .map(Payment::getDate)
                .filter(d -> d != null && !d.isEmpty())
                .map(d -> { try { return LocalDate.parse(d, DATE_FORMAT).getYear(); } catch (Exception e) { return null; } })
                .filter(y -> y != null)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private static TableView<UnmatchedPayment> buildUnmatchedTable(ObservableList<UnmatchedPayment> items,
                                                                    Label feedbackLabel) {
        TableView<UnmatchedPayment> table = new TableView<>(items);
        table.setPlaceholder(new Label("No unmatched transactions."));
        table.setPrefHeight(160);

        TableColumn<UnmatchedPayment, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getDescription()));
        descCol.setPrefWidth(200);

        TableColumn<UnmatchedPayment, String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getAmount()));
        amtCol.setPrefWidth(90);

        TableColumn<UnmatchedPayment, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getDate()));
        dateCol.setPrefWidth(100);

        TableColumn<UnmatchedPayment, Void> assignCol = new TableColumn<>("Action");
        assignCol.setPrefWidth(120);
        assignCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Button btn = new Button("Assign Member");
            {
                btn.setOnAction(e -> {
                    UnmatchedPayment row = getTableView().getItems().get(getIndex());
                    showAssignDialog(row, items, feedbackLabel);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().setAll(descCol, amtCol, dateCol, assignCol);
        return table;
    }

    private static void showAssignDialog(UnmatchedPayment row, ObservableList<UnmatchedPayment> items,
                                          Label feedbackLabel) {
        javafx.scene.control.Dialog<String[]> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Assign Transaction");
        dialog.setHeaderText("Assign \"" + row.getDescription() + "\" (" + row.getAmount() + ") to a member.");

        TextField memberIdField = new TextField();
        memberIdField.setPromptText("Membership ID (e.g. M-001)");
        TextField memberNameField = new TextField();
        memberNameField.setPromptText("Member full name");

        // Pre-fill from existing members for convenience
        ComboBox<String> memberCombo = new ComboBox<>();
        SampleData.getMembers().forEach(m -> memberCombo.getItems().add(m.getMembershipId() + " — " + m.getName()));
        memberCombo.setPromptText("Select existing member…");
        memberCombo.setMaxWidth(Double.MAX_VALUE);
        memberCombo.setOnAction(e -> {
            String sel = memberCombo.getValue();
            if (sel != null && sel.contains(" — ")) {
                String[] parts = sel.split(" — ", 2);
                memberIdField.setText(parts[0]);
                memberNameField.setText(parts[1]);
            }
        });

        VBox content = new VBox(8, memberCombo, new Label("Or enter manually:"), memberIdField, memberNameField);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> btn == ButtonType.OK
                ? new String[]{memberIdField.getText().trim(), memberNameField.getText().trim()}
                : null);

        dialog.showAndWait().ifPresent(result -> {
            String memberId = result[0];
            String memberName = result[1];
            if (memberId.isEmpty() && memberName.isEmpty()) return;
            runVoidAsync(() -> ApiClient.assignUnmatched(row.getId(), memberId, memberName), () -> {
                items.remove(row);
                SampleData.reload(null, err -> {});
                feedbackLabel.setText("Assigned and payment created.");
            }, err -> feedbackLabel.setText("Assign failed: " + err.getMessage()));
        });
    }

    // ── Embedded Plaid Link (no external browser needed) ───────────────────────

    /**
     * Opens the Plaid Link connect page in an in-app WebView window instead of the
     * system browser. A small Java/JS bridge lets the page tell us the moment the
     * bank connection succeeds so we can close the window and kick off a sync
     * automatically, instead of the user having to switch windows and click Sync.
     */
    private static void openEmbeddedBankConnect(String url, Consumer<Boolean> onFinished) {
        Stage stage = new Stage();
        stage.setTitle("Connect Bank Account");
        stage.initModality(Modality.APPLICATION_MODAL);

        WebView webView = new WebView();
        webView.getEngine().load(url);

        boolean[] connected = {false};

        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webView.getEngine().executeScript("window");
                window.setMember("javaBridge", new Object() {
                    public void onConnected() {
                        connected[0] = true;
                        Platform.runLater(stage::close);
                    }

                    public void onExit(String errorMessage) {
                        if (errorMessage != null && !errorMessage.isEmpty()) {
                            System.err.println("[Plaid Link] exited with error: " + errorMessage);
                        }
                    }
                });
            }
        });

        stage.setScene(new Scene(webView, 480, 640));
        stage.setOnHidden(e -> onFinished.accept(connected[0]));
        stage.showAndWait();
    }

    private static <T> void runAsync(ApiCall<T> call, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return call.execute(); }
        };
        if (onSuccess != null) task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        if (onError != null) task.setOnFailed(e -> onError.accept(task.getException()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private static void runVoidAsync(VoidApiCall call, Runnable onSuccess, Consumer<Throwable> onError) {
        runAsync(() -> { call.execute(); return null; },
                onSuccess != null ? v -> onSuccess.run() : null, onError);
    }

    @FunctionalInterface private interface ApiCall<T> { T execute() throws Exception; }
    @FunctionalInterface private interface VoidApiCall { void execute() throws Exception; }

    // ── Monthly summary model ─────────────────────────────────────────────────

    private static class MemberMonthlySummary {
        private final String memberId, memberName;
        private String jan = "", feb = "", mar = "", apr = "";
        private String may = "", jun = "", jul = "", aug = "";
        private String sep = "", oct = "", nov = "", dec = "";
        private String yearTotal = "$0.00";
        private BigDecimal totalAmount = BigDecimal.ZERO;

        MemberMonthlySummary(String memberId, String memberName) {
            this.memberId = memberId;
            this.memberName = memberName;
        }

        public String getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public String getJan() { return jan; }
        public String getFeb() { return feb; }
        public String getMar() { return mar; }
        public String getApr() { return apr; }
        public String getMay() { return may; }
        public String getJun() { return jun; }
        public String getJul() { return jul; }
        public String getAug() { return aug; }
        public String getSep() { return sep; }
        public String getOct() { return oct; }
        public String getNov() { return nov; }
        public String getDec() { return dec; }
        public String getYearTotal() { return yearTotal; }

        void setMonthStatus(int month, String label, BigDecimal amountPaid) {
            switch (month) {
                case 1 -> jan = label; case 2 -> feb = label; case 3 -> mar = label;
                case 4 -> apr = label; case 5 -> may = label; case 6 -> jun = label;
                case 7 -> jul = label; case 8 -> aug = label; case 9 -> sep = label;
                case 10 -> oct = label; case 11 -> nov = label; case 12 -> dec = label;
            }
            if (amountPaid != null) totalAmount = totalAmount.add(amountPaid);
        }

        void computeTotal() { yearTotal = "$" + String.format("%.2f", totalAmount); }
    }
}
