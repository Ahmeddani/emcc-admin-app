package com.emcc.admin.app;

import com.emcc.admin.app.model.SampleData;
import com.emcc.admin.app.pages.AssetsPage;
import com.emcc.admin.app.pages.DashboardPage;
import com.emcc.admin.app.pages.LoginPage;
import com.emcc.admin.app.pages.MembersPage;
import com.emcc.admin.app.pages.PaymentsPage;
import com.emcc.admin.app.pages.ReportsPage;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MainApp extends Application {

    private Stage primaryStage;
    private BorderPane rootPane;
    private StackPane contentPane;
    private VBox detailPane;
    private Label titleLabel;
    private Label detailTitleLabel;
    private Label detailTextLabel;
    private VBox detailContent;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        Scene splashScene = new Scene(createSplashPane(), 520, 420);
        splashScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("EMCC Admin App");
        stage.setScene(splashScene);
        stage.show();

        PauseTransition splashDelay = new PauseTransition(Duration.seconds(1.8));
        splashDelay.setOnFinished(event -> showLoginScene(stage));
        splashDelay.play();
    }

    private void showLoginScene(Stage stage) {
        Scene loginScene = new Scene(LoginPage.createContent((email, password) -> showMainStage(stage)), 520, 560);
        loginScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setScene(loginScene);
        stage.setWidth(520);
        stage.setHeight(560);
        stage.centerOnScreen();
    }

    private void showMainStage(Stage stage) {
        rootPane = new BorderPane();
        contentPane = new StackPane();
        contentPane.getStyleClass().add("content-pane");
        titleLabel = new Label("EMCC Admin");
        titleLabel.getStyleClass().add("page-title");

        detailTitleLabel = new Label("Details");
        detailTitleLabel.getStyleClass().add("section-title");
        detailTextLabel = new Label(getPageDetails("Dashboard"));
        detailTextLabel.getStyleClass().add("detail-text");
        detailTextLabel.setWrapText(true);
        detailContent = new VBox(detailTextLabel);
        detailPane = new VBox(10, detailTitleLabel, detailContent);
        detailPane.setPadding(new Insets(18));
        detailPane.getStyleClass().add("detail-panel");

        rootPane.setTop(createTopBar());
        rootPane.setLeft(createSidebar());
        rootPane.setRight(detailPane);

        contentPane.getChildren().add(DashboardPage.createContent(this::navigateTo));
        rootPane.setCenter(contentPane);

        Scene scene = new Scene(rootPane, 1180, 720);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setScene(scene);
        stage.setWidth(1180);
        stage.setHeight(720);
        stage.centerOnScreen();

        SampleData.reload(null, err -> System.err.println("Data load failed: " + err.getMessage()));
    }

    private VBox createTopBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutButton = new Button("Logout");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> showLoginScene(primaryStage));

        HBox titleRow = new HBox(titleLabel, spacer, logoutButton);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox topBar = new VBox(titleRow);
        topBar.setPadding(new Insets(10, 18, 10, 18));
        topBar.getStyleClass().add("top-bar");
        return topBar;
    }

    private StackPane createSplashPane() {
        Node splashLogo = DashboardPage.createLogo();
        StackPane splashPane = new StackPane(splashLogo);
        splashPane.getStyleClass().add("splash-pane");
        return splashPane;
    }

    private VBox createSidebar() {
        Button dashboardButton = new Button("Dashboard");
        Button membersButton = new Button("Members");
        Button assetsButton = new Button("Assets");
        Button paymentsButton = new Button("Payments");
        Button reportsButton = new Button("Reports");

        dashboardButton.setMaxWidth(Double.MAX_VALUE);
        membersButton.setMaxWidth(Double.MAX_VALUE);
        assetsButton.setMaxWidth(Double.MAX_VALUE);
        paymentsButton.setMaxWidth(Double.MAX_VALUE);
        reportsButton.setMaxWidth(Double.MAX_VALUE);

        dashboardButton.setOnAction(event -> navigateTo("Dashboard"));
        membersButton.setOnAction(event -> navigateTo("Manage Members"));
        assetsButton.setOnAction(event -> navigateTo("Manage Assets"));
        paymentsButton.setOnAction(event -> navigateTo("Manage Payments"));
        reportsButton.setOnAction(event -> navigateTo("Reports"));

        VBox sidebar = new VBox(12, dashboardButton, membersButton, assetsButton, paymentsButton, reportsButton);
        sidebar.setPadding(new Insets(18));
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private void navigateTo(String pageName) {
        switch (pageName) {
            case "Members", "Manage Members" -> switchPage("Manage Members", MembersPage.createContent(this::setDetailForm, this::setDetailMessage));
            case "Assets", "Manage Assets" -> switchPage("Manage Assets", AssetsPage.createContent(this::setDetailForm, this::setDetailMessage));
            case "Payments", "Manage Payments", "Review Payments" -> switchPage("Manage Payments", PaymentsPage.createContent(this::setDetailForm, this::setDetailMessage));
            case "Reports" -> switchPage("Reports", ReportsPage.createContent(this::setDetailForm, this::setDetailMessage));
            default -> switchPage("Dashboard", DashboardPage.createContent(this::navigateTo));
        }
    }

    private void switchPage(String pageName, javafx.scene.Node content) {
        setDetailMessage(pageName.equals("Dashboard") ? "Overview" : pageName, getPageDetails(pageName));
        if (contentPane.getChildren().isEmpty()) {
            contentPane.getChildren().setAll(content);
            return;
        }

        javafx.scene.Node currentPage = contentPane.getChildren().get(0);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), currentPage);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> {
            contentPane.getChildren().setAll(content);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), content);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
    }

    private String getPageDetails(String pageName) {
        return switch (pageName) {
            case "Members", "Manage Members" -> "Manage member records, membership status, and contact details from this panel.";
            case "Assets", "Manage Assets" -> "Track inventory items, condition, and quantity for community assets.";
            case "Payments", "Manage Payments", "Review Payments" -> "Review payments, check transaction status, and track recent invoices.";
            case "Reports" -> "Monthly, quarterly, and yearly financial reports — collections, non-payers, and totals.";
            default -> "See high-level statistics and recent activity for your community administration.";
        };
    }

    private void setDetailMessage(String title, String message) {
        detailTitleLabel.setText(title);
        detailContent.getChildren().setAll(new Label(message));
    }

    private void setDetailForm(String title, javafx.scene.Node form) {
        detailTitleLabel.setText(title);
        detailContent.getChildren().setAll(form);
    }

    public static void main(String[] args) {
        launch();
    }
}
