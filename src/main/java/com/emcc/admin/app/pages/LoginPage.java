package com.emcc.admin.app.pages;

import com.emcc.admin.app.service.ApiClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

        VBox fields = new VBox(6, emailLabel, emailField, passwordLabel, passwordField, errorLabel);
        VBox card = new VBox(28);
        Runnable showPasswordStep = () -> {
            card.getChildren().setAll(branding, fields, loginButton);
            passwordField.requestFocus();
        };

        // ── Step 2: emailed sign-in code ──────────────────────────────────────
        Consumer<ApiClient.LoginChallenge> showCodeStep = challenge -> {
            Label codeInfo = new Label("We emailed a 6-digit sign-in code to "
                    + (challenge.sentTo().isBlank() ? "your email" : challenge.sentTo()) + ".");
            codeInfo.setWrapText(true);
            codeInfo.getStyleClass().add("login-field-label");

            Label codeLabel = new Label("Sign-in code");
            codeLabel.getStyleClass().add("login-field-label");
            TextField codeField = new TextField();
            codeField.setPromptText("123456");
            codeField.getStyleClass().add("login-field");
            codeField.setTextFormatter(new TextFormatter<String>(change ->
                    change.getControlNewText().matches("\\d{0,6}") ? change : null));

            Label codeError = new Label();
            codeError.getStyleClass().add("login-error");
            codeError.setWrapText(true);
            codeError.setVisible(false);
            codeError.setManaged(false);

            Button verifyButton = new Button("Verify");
            verifyButton.setMaxWidth(Double.MAX_VALUE);
            verifyButton.getStyleClass().add("login-button");

            Hyperlink resendLink = new Hyperlink("Resend code");
            Hyperlink backLink = new Hyperlink("Back to sign in");
            HBox links = new HBox(16, resendLink, backLink);
            links.setAlignment(Pos.CENTER);

            Consumer<String> showCodeError = msg -> {
                codeError.setText(msg);
                codeError.setVisible(true);
                codeError.setManaged(true);
            };

            Runnable attemptVerify = () -> {
                String code = codeField.getText().trim();
                if (code.length() != 6) {
                    showCodeError.accept("Enter the 6-digit code from the email.");
                    return;
                }
                verifyButton.setDisable(true);
                verifyButton.setText("Verifying…");
                codeError.setVisible(false);
                codeError.setManaged(false);

                Task<Void> verifyTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        ApiClient.verifyLoginCode(challenge.challengeId(), code);
                        return null;
                    }
                };
                verifyTask.setOnSucceeded(ev -> onLoginSuccess.accept(emailField.getText().trim(), ApiClient.getAdminName()));
                verifyTask.setOnFailed(ev -> {
                    verifyButton.setDisable(false);
                    verifyButton.setText("Verify");
                    codeField.clear();
                    Throwable ex = verifyTask.getException();
                    if (ex instanceof IllegalStateException) {
                        // Expired or locked: start over
                        showPasswordStep.run();
                        errorLabel.setText(ex.getMessage());
                        errorLabel.setVisible(true);
                        errorLabel.setManaged(true);
                    } else {
                        showCodeError.accept(ex instanceof IllegalArgumentException
                                ? ex.getMessage() : "Verification failed. Please try again.");
                    }
                });
                runInBackground(verifyTask, "verify-code-thread");
            };

            resendLink.setOnAction(e -> {
                resendLink.setDisable(true);
                Task<Void> resendTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        ApiClient.resendLoginCode(challenge.challengeId());
                        return null;
                    }
                };
                resendTask.setOnSucceeded(ev -> {
                    codeInfo.setText("A new code was sent to "
                            + (challenge.sentTo().isBlank() ? "your email" : challenge.sentTo()) + ".");
                    resendLink.setDisable(false);
                });
                resendTask.setOnFailed(ev -> {
                    resendLink.setDisable(false);
                    Throwable ex = resendTask.getException();
                    showCodeError.accept(ex instanceof IllegalArgumentException
                            ? ex.getMessage() : "Could not resend the code.");
                });
                runInBackground(resendTask, "resend-code-thread");
            });

            backLink.setOnAction(e -> showPasswordStep.run());
            verifyButton.setOnAction(e -> attemptVerify.run());
            codeField.setOnAction(e -> attemptVerify.run());

            VBox codeFields = new VBox(6, codeInfo, codeLabel, codeField, codeError);
            card.getChildren().setAll(branding, codeFields, verifyButton, links);
            codeField.requestFocus();
        };

        // ── Step 1: email + password ──────────────────────────────────────────
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

            Task<ApiClient.LoginChallenge> loginTask = new Task<>() {
                @Override
                protected ApiClient.LoginChallenge call() throws Exception {
                    return ApiClient.login(email, password);
                }
            };

            loginTask.setOnSucceeded(ev -> {
                loginButton.setDisable(false);
                loginButton.setText("Sign In");
                ApiClient.LoginChallenge challenge = loginTask.getValue();
                if (challenge == null) {
                    onLoginSuccess.accept(email, ApiClient.getAdminName());
                } else {
                    passwordField.clear();
                    showCodeStep.accept(challenge);
                }
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

            runInBackground(loginTask, "login-thread");
        };

        loginButton.setOnAction(e -> attemptLogin.run());
        passwordField.setOnAction(e -> attemptLogin.run());
        emailField.setOnAction(e -> passwordField.requestFocus());

        card.getChildren().setAll(branding, fields, loginButton);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(380);
        card.setPadding(new Insets(40, 44, 44, 44));
        card.setAlignment(Pos.TOP_CENTER);

        StackPane root = new StackPane(card);
        root.getStyleClass().add("login-pane");
        return root;
    }

    private static void runInBackground(Task<?> task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
    }
}
