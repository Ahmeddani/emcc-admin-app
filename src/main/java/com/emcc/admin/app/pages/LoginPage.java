package com.emcc.admin.app.pages;

import com.emcc.admin.app.service.ApiClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

public class LoginPage {

    public static Parent createContent(BiConsumer<String, String> onLoginSuccess) {
        Label logoLabel = new Label("EMCC");
        logoLabel.getStyleClass().add("login-logo");

        Label subtitleLabel = new Label("Admin Portal");
        subtitleLabel.getStyleClass().add("login-subtitle");

        VBox branding = new VBox(4, logoLabel, subtitleLabel);
        branding.setAlignment(Pos.CENTER);

        Label emailLabel = new Label("Email Address");
        emailLabel.getStyleClass().add("login-field-label");
        TextField emailField = new TextField();
        emailField.setPromptText("you@emcc.org");
        emailField.getStyleClass().add("login-field");

        Label passwordLabel = new Label("Password");
        passwordLabel.getStyleClass().add("login-field-label");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.getStyleClass().add("login-field");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button loginButton = new Button("Sign In");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.getStyleClass().add("login-button");

        Runnable attemptLogin = () -> {
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            if (email.isEmpty() || password.isEmpty()) {
                errorLabel.setText("Please enter your email and password.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                return;
            }

            loginButton.setDisable(true);
            loginButton.setText("Signing in…");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);

            Task<Void> loginTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    ApiClient.login(email, password);
                    return null;
                }
            };

            loginTask.setOnSucceeded(ev -> {
                loginButton.setDisable(false);
                loginButton.setText("Sign In");
                onLoginSuccess.accept(email, ApiClient.getAdminName());
            });

            loginTask.setOnFailed(ev -> {
                loginButton.setDisable(false);
                loginButton.setText("Sign In");
                Throwable ex = loginTask.getException();
                String msg;
                if (ex instanceof IllegalArgumentException) {
                    msg = ex.getMessage();
                } else if (ex != null && ex.getMessage() != null
                        && (ex.getMessage().contains("Connection refused")
                            || ex.getMessage().contains("ConnectException"))) {
                    msg = "Cannot connect to server. Is the backend running?";
                } else {
                    msg = "Login failed. Please try again.";
                }
                errorLabel.setText(msg);
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                passwordField.clear();
            });

            Thread t = new Thread(loginTask, "login-thread");
            t.setDaemon(true);
            t.start();
        };

        loginButton.setOnAction(e -> attemptLogin.run());
        passwordField.setOnAction(e -> attemptLogin.run());
        emailField.setOnAction(e -> passwordField.requestFocus());

        VBox fields = new VBox(6, emailLabel, emailField, passwordLabel, passwordField, errorLabel);

        VBox card = new VBox(28, branding, fields, loginButton);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(380);
        card.setPadding(new Insets(40, 44, 44, 44));
        card.setAlignment(Pos.TOP_CENTER);

        StackPane root = new StackPane(card);
        root.getStyleClass().add("login-pane");
        return root;
    }
}
