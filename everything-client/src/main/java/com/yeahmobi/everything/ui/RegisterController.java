package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.auth.AuthResult;
import com.yeahmobi.everything.auth.AuthService;
import com.yeahmobi.everything.auth.Session;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the standalone registration view (register.fxml).
 * <p>
 * Handles email verification code sending, registration submission,
 * input validation, and error display. Provides callbacks for
 * successful registration and navigation back to login.
 * </p>
 */
public class RegisterController {

    private static final Logger LOGGER = Logger.getLogger(RegisterController.class.getName());

    @FXML private TextField regEmailField;
    @FXML private TextField regCodeField;
    @FXML private Button sendCodeBtn;
    @FXML private PasswordField regPasswordField;
    @FXML private PasswordField regConfirmPasswordField;
    @FXML private Button registerBtn;
    @FXML private Label registerErrorLabel;
    @FXML private ProgressIndicator registerProgress;
    @FXML private Hyperlink backToLoginLink;

    private AuthService authService;

    /** Callback invoked when registration succeeds. */
    private AuthController.LoginSuccessCallback onRegisterSuccess;

    /** Callback invoked when user clicks "back to login". */
    private Runnable onBackToLoginCallback;

    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    public void setOnRegisterSuccess(AuthController.LoginSuccessCallback callback) {
        this.onRegisterSuccess = callback;
    }

    public void setOnBackToLoginCallback(Runnable callback) {
        this.onBackToLoginCallback = callback;
    }

    @FXML
    public void initialize() {
        // No-op; fields are ready from FXML.
    }

    @FXML
    void onSendCode() {
        clearError();
        String email = regEmailField.getText();

        if (email == null || email.isBlank()) {
            showError("请输入邮箱地址");
            return;
        }

        sendCodeBtn.setDisable(true);
        sendCodeBtn.setText("发送中...");

        CompletableFuture.supplyAsync(() -> authService.sendVerificationCode(email))
                .thenAcceptAsync(success -> {
                    if (success) {
                        sendCodeBtn.setText("已发送");
                        startCodeCooldown();
                    } else {
                        sendCodeBtn.setDisable(false);
                        sendCodeBtn.setText("发送验证码");
                        showError("验证码发送失败，请重试");
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        sendCodeBtn.setDisable(false);
                        sendCodeBtn.setText("发送验证码");
                        showError("发送失败，请检查网络连接");
                    });
                    LOGGER.log(Level.WARNING, "Send verification code failed", ex);
                    return null;
                });
    }

    @FXML
    void onRegister() {
        clearError();
        String email = regEmailField.getText();
        String code = regCodeField.getText();
        String password = regPasswordField.getText();
        String confirmPassword = regConfirmPasswordField.getText();

        if (email == null || email.isBlank()) {
            showError("请输入邮箱地址");
            return;
        }
        if (code == null || code.isBlank()) {
            showError("请输入验证码");
            return;
        }
        if (password == null || password.isBlank()) {
            showError("请设置密码");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("两次输入的密码不一致");
            return;
        }

        setLoading(true);

        CompletableFuture.supplyAsync(() -> authService.register(email, code, password))
                .thenAcceptAsync(result -> {
                    setLoading(false);
                    if (result.success()) {
                        if (onRegisterSuccess != null) {
                            onRegisterSuccess.onSuccess(result.session());
                        }
                    } else {
                        showError(result.message());
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setLoading(false);
                        showError("注册失败，请检查网络连接");
                    });
                    LOGGER.log(Level.WARNING, "Registration failed", ex);
                    return null;
                });
    }

    @FXML
    void onBackToLogin() {
        if (onBackToLoginCallback != null) {
            onBackToLoginCallback.run();
        }
    }

    // ---- Internal helpers ----

    private void showError(String message) {
        registerErrorLabel.setText(message);
        registerErrorLabel.setVisible(true);
        registerErrorLabel.setManaged(true);
    }

    private void clearError() {
        registerErrorLabel.setText("");
        registerErrorLabel.setVisible(false);
        registerErrorLabel.setManaged(false);
    }

    private void setLoading(boolean loading) {
        registerProgress.setVisible(loading);
        registerProgress.setManaged(loading);
        registerBtn.setDisable(loading);
        regEmailField.setDisable(loading);
        regCodeField.setDisable(loading);
        regPasswordField.setDisable(loading);
        regConfirmPasswordField.setDisable(loading);
    }

    private void startCodeCooldown() {
        CompletableFuture.runAsync(() -> {
            for (int i = 60; i > 0; i--) {
                int remaining = i;
                Platform.runLater(() -> sendCodeBtn.setText(remaining + "s"));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Platform.runLater(() -> {
                sendCodeBtn.setText("发送验证码");
                sendCodeBtn.setDisable(false);
            });
        });
    }

    // ---- Getters for testing ----

    TextField getRegEmailField() { return regEmailField; }
    TextField getRegCodeField() { return regCodeField; }
    PasswordField getRegPasswordField() { return regPasswordField; }
    PasswordField getRegConfirmPasswordField() { return regConfirmPasswordField; }
    Button getSendCodeBtn() { return sendCodeBtn; }
    Button getRegisterBtn() { return registerBtn; }
    Label getRegisterErrorLabel() { return registerErrorLabel; }
    ProgressIndicator getRegisterProgress() { return registerProgress; }
}
