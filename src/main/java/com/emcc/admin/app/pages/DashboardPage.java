package com.emcc.admin.app.pages;

import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.Payment;
import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.util.DuesCalculator;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DashboardPage {

    public static Node createContent(Consumer<String> navigator) {
        VBox root = new VBox(18);
        root.setPadding(new Insets(18));

        Label header = new Label("Community hub");
        header.getStyleClass().add("section-title");

        Label subtitle = new Label("A simple control panel for your small community.");
        subtitle.getStyleClass().add("section-subtitle");

        VBox titleBox = new VBox(6, header, subtitle);
        titleBox.setFillWidth(true);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Node logo = createLogo();
        HBox topRow = new HBox(16, titleBox, logo);
        topRow.setFillHeight(true);

        LocalDate today = LocalDate.now();
        int memberCount = (int) SampleData.getMembers().stream()
                .filter(m -> !m.getMembershipId().isEmpty() || !m.getName().isEmpty())
                .count();
        long pendingPayments = SampleData.getPayments().stream()
                .filter(p -> "Pending".equalsIgnoreCase(p.getStatus()))
                .filter(p -> {
                    if (p.getDate() == null || p.getDate().isEmpty()) return false;
                    try { return !LocalDate.parse(p.getDate()).isAfter(today); }
                    catch (Exception e) { return false; }
                })
                .count();
        int assetCount = SampleData.getAssets().size();

        List<Payment> allPayments = SampleData.getPayments();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();
        List<Member> owingMembers = SampleData.getMembers().stream()
                .filter(m -> !m.getMembershipId().isEmpty() || !m.getName().isEmpty())
                .filter(m -> {
                    DuesCalculator.Status status = DuesCalculator.statusFor(m, allPayments, currentYear, currentMonth, today);
                    return status == DuesCalculator.Status.MISSING || status == DuesCalculator.Status.UNDERPAID;
                })
                .collect(java.util.stream.Collectors.toList());

        HBox summaryRow = new HBox(14);
        summaryRow.setFillHeight(true);
        summaryRow.getChildren().addAll(
                createSummaryCard("Members", String.valueOf(memberCount), "Active members"),
                createSummaryCard("Assets", String.valueOf(assetCount), "Tracked items"),
                createSummaryCard("Pending payments", String.valueOf(pendingPayments), "Needs review"),
                createSummaryCard("Owe dues this month", String.valueOf(owingMembers.size()), "Needs follow-up")
        );

        HBox actions = new HBox(12,
                createActionButton("Manage Members", () -> navigator.accept("Manage Members")),
                createActionButton("Manage Assets", () -> navigator.accept("Manage Assets")),
                createActionButton("Review Payments", () -> navigator.accept("Manage Payments"))
        );

        List<Label> activityLabels = createRecentActivityLabels();
        VBox activityCard = createCard("Recent activity", activityLabels.toArray(new Label[0]));

        VBox duesCard = createOwingDuesCard(owingMembers, allPayments, currentYear, currentMonth);

        VBox highlightCard = createCard("Community highlights",
                new Label("Member spotlight: Aisha is the volunteer coordinator."),
                new Label("Next meeting: Friday, 6 PM at the community center."),
                new Label("Reminder: renew two memberships this month.")
        );

        root.getChildren().addAll(topRow, summaryRow, actions, duesCard, activityCard, highlightCard);
        return root;
    }

    private static VBox createOwingDuesCard(List<Member> owingMembers, List<Payment> payments, int year, int month) {
        if (owingMembers.isEmpty()) {
            return createCard("Dues status — this month",
                    new Label("Everyone who owes dues this month has paid. Nice work."));
        }
        List<Label> rows = new ArrayList<>();
        int shown = Math.min(owingMembers.size(), 5);
        for (int i = 0; i < shown; i++) {
            Member member = owingMembers.get(i);
            BigDecimal owed = DuesCalculator.duesFor(member)
                    .subtract(DuesCalculator.amountPaid(member, payments, year, month));
            rows.add(new Label(member.getName() + " owes $" + String.format("%.2f", owed) + " for this month."));
        }
        int remaining = owingMembers.size() - shown;
        if (remaining > 0) {
            Label more = new Label("... and " + remaining + " more member" + (remaining == 1 ? "" : "s") + ".");
            more.getStyleClass().add("detail-text");
            rows.add(more);
        }
        return createCard("Members who still owe dues this month", rows.toArray(new Label[0]));
    }

    public static Node createLogo() {
        Image logoImage = new Image(
                DashboardPage.class.getResourceAsStream("/emcc-icon-mark.png"),
                120, 120, true, true);
        ImageView icon = new ImageView(logoImage);
        icon.setFitWidth(120);
        icon.setFitHeight(120);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        icon.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 10, 0, 0, 3);");

        Label logomark = new Label("EMCC");
        logomark.setTextFill(Color.web("#144d29"));
        logomark.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-letter-spacing: 0.2em;");

        Label organizationName = new Label("Eritrean Muslims Community of Columbus, Ohio");
        organizationName.setTextFill(Color.web("#47525e"));
        organizationName.setStyle("-fx-font-size: 10px; -fx-text-alignment: center; -fx-wrap-text: true;");
        organizationName.setMaxWidth(160);

        VBox logoBox = new VBox(8, icon, logomark, organizationName);
        logoBox.setPadding(new Insets(12));
        logoBox.setStyle("-fx-alignment: center; -fx-background-color: white; -fx-background-radius: 18; -fx-border-color: rgba(31, 122, 77, 0.24); -fx-border-radius: 18;");
        return logoBox;
    }

    private static List<Label> createRecentActivityLabels() {
        List<Label> labels = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String currentMonthName = today.getMonth().name().substring(0, 1)
                + today.getMonth().name().substring(1, 3).toLowerCase();

        Set<String> newMembers = SampleData.getMembers().stream()
                .filter(member -> member.getCreatedDate() != null && !member.getCreatedDate().isEmpty())
                .filter(member -> {
                    try {
                        LocalDate created = LocalDate.parse(member.getCreatedDate());
                        return !created.isAfter(today) && created.isAfter(today.minusDays(30));
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .map(Member::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> pendingPayments = new TreeSet<>();
        Set<String> completedPayments = new TreeSet<>();
        Set<String> overduePayments = new TreeSet<>();
        for (Payment payment : SampleData.getPayments()) {
            if (payment.getStatus() == null) {
                continue;
            }
            if (payment.getDate() == null || payment.getDate().isEmpty()) {
                if ("Pending".equalsIgnoreCase(payment.getStatus())) {
                    pendingPayments.add(payment.getMemberName() + " has a pending payment with no date assigned.");
                }
                continue;
            }
            LocalDate paymentDate;
            try {
                paymentDate = LocalDate.parse(payment.getDate());
            } catch (Exception ex) {
                continue;
            }
            if (paymentDate.getYear() == today.getYear() && paymentDate.getMonth() == today.getMonth()) {
                if ("Pending".equalsIgnoreCase(payment.getStatus())) {
                    pendingPayments.add(payment.getMemberName() + " has a pending payment for " + currentMonthName + ".");
                } else if ("Completed".equalsIgnoreCase(payment.getStatus())) {
                    completedPayments.add(payment.getMemberName() + " completed payment for " + currentMonthName + ".");
                }
            } else if (paymentDate.isBefore(today.withDayOfMonth(1)) && "Pending".equalsIgnoreCase(payment.getStatus())) {
                overduePayments.add(payment.getMemberName() + " has an overdue payment from "
                        + paymentDate.getMonth().name().substring(0, 1)
                        + paymentDate.getMonth().name().substring(1, 3).toLowerCase()
                        + ".");
            }
        }

        newMembers.forEach(name -> labels.add(new Label(name + " joined the community.")));
        pendingPayments.forEach(item -> labels.add(new Label(item)));
        completedPayments.forEach(item -> labels.add(new Label(item)));
        overduePayments.forEach(item -> labels.add(new Label(item)));

        if (labels.isEmpty()) {
            labels.add(new Label("No recent activity yet. Everything looks up to date."));
        }

        if (labels.size() <= 4) return labels;
        int extra = labels.size() - 4;
        List<Label> result = new ArrayList<>(labels.subList(0, 4));
        Label moreLabel = new Label("... and " + extra + " more item" + (extra == 1 ? "" : "s"));
        moreLabel.getStyleClass().add("detail-text");
        result.add(moreLabel);
        return result;
    }

    private static VBox createSummaryCard(String title, String value, String caption) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("summary-title");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("summary-value");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("summary-caption");

        VBox card = new VBox(8, titleLabel, valueLabel, captionLabel);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("summary-card");
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private static Button createActionButton(String label, Runnable action) {
        Button button = new Button(label);
        button.getStyleClass().add("action-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private static VBox createCard(String title, Label... content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");

        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("activity-card");
        card.getChildren().add(titleLabel);
        card.getChildren().addAll(content);
        return card;
    }
}
