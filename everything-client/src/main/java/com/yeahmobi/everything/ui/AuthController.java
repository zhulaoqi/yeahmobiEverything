package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.auth.AuthService;
import com.yeahmobi.everything.auth.OAuthCallbackServer;
import com.yeahmobi.everything.auth.Session;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.geometry.Rectangle2D;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the login and registration views.
 * <p>
 * Handles email/password login, Feishu OAuth login, user registration
 * with verification codes, and auto-login on startup.
 * </p>
 */
public class AuthController {

    private static final Logger LOGGER = Logger.getLogger(AuthController.class.getName());

    // --- Login form fields ---
    @FXML private VBox loginContainer;
    @FXML private VBox registerContainer;
    @FXML private HBox windowBar;
    @FXML private Button windowCloseBtn;
    @FXML private Button windowMinBtn;
    @FXML private Button windowMaxBtn;
    @FXML private Button feishuLoginBtn;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button emailLoginBtn;
    @FXML private Label loginErrorLabel;
    @FXML private ProgressIndicator loginProgress;
    @FXML private Hyperlink registerLink;

    // --- Register form fields ---
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
    private Stage stage;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean windowMaximized = false;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;

    /** Local HTTP server for receiving Feishu OAuth callback. */
    private OAuthCallbackServer callbackServer;

    /** Callback invoked when login succeeds, passing the session. */
    private LoginSuccessCallback onLoginSuccess;

    /**
     * Functional interface for login success callback.
     */
    @FunctionalInterface
    public interface LoginSuccessCallback {
        void onSuccess(Session session);
    }

    /**
     * Sets the AuthService dependency.
     */
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Sets the callback to invoke when login succeeds.
     */
    public void setOnLoginSuccess(LoginSuccessCallback callback) {
        this.onLoginSuccess = callback;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        setupWindowControls();
    }

    @FXML
    public void initialize() {
        // Login view is shown by default; register view is hidden.
        setupWindowControls();
    }

    @FXML
    private void onWindowClose() {
        if (stage != null) {
            stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }

    @FXML
    private void onWindowMinimize() {
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    private void onWindowMaximize() {
        if (stage != null) {
            if (stage.isFullScreen()) {
                stage.setFullScreen(false);
            }
            if (windowMaximized) {
                stage.setMaximized(false);
                stage.setX(restoreX);
                stage.setY(restoreY);
                stage.setWidth(restoreWidth);
                stage.setHeight(restoreHeight);
                windowMaximized = false;
                setWindowMaximizedStyle(false);
            } else {
                restoreX = stage.getX();
                restoreY = stage.getY();
                restoreWidth = stage.getWidth();
                restoreHeight = stage.getHeight();
                Rectangle2D bounds = getVisualBounds();
                stage.setMaximized(false);
                stage.setX(bounds.getMinX());
                stage.setY(bounds.getMinY());
                stage.setWidth(bounds.getWidth());
                stage.setHeight(bounds.getHeight());
                windowMaximized = true;
                setWindowMaximizedStyle(true);
            }
        }
    }

    private Rectangle2D getVisualBounds() {
        if (stage != null) {
            var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                    stage.getWidth(), stage.getHeight());
            if (!screens.isEmpty()) {
                return screens.get(0).getVisualBounds();
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private void setWindowMaximizedStyle(boolean maximized) {
        if (windowBar == null || windowBar.getScene() == null) {
            return;
        }
        var root = windowBar.getScene().getRoot();
        if (root == null) {
            return;
        }
        if (maximized) {
            if (!root.getStyleClass().contains("window-maximized")) {
                root.getStyleClass().add("window-maximized");
            }
        } else {
            root.getStyleClass().remove("window-maximized");
        }
    }

    private void setupWindowControls() {
        if (windowBar == null) {
            return;
        }
        windowBar.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (stage == null) {
                return;
            }
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        windowBar.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (stage == null) {
                return;
            }
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });
    }

    /**
     * Attempts auto-login by checking for a stored session.
     * Should be called after the controller is fully initialized and authService is set.
     */
    public void tryAutoLogin() {
        if (authService == null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> authService.getStoredSession())
                .thenAcceptAsync(sessionOpt -> {
                    if (sessionOpt.isPresent()) {
                        Session session = sessionOpt.get();
                        if (session.expiresAt() > System.currentTimeMillis()) {
                            Platform.runLater(() -> handleLoginSuccess(session));
                        }
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Auto-login check failed", ex);
                    return null;
                });
    }

    // ---- Login handlers ----

    @FXML
    void onEmailLogin() {
        clearLoginError();
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email == null || email.isBlank()) {
            showLoginError("请输入邮箱地址");
            return;
        }
        if (password == null || password.isBlank()) {
            showLoginError("请输入密码");
            return;
        }

        setLoginLoading(true);

        CompletableFuture.supplyAsync(() -> authService.loginWithEmail(email, password))
                .thenAcceptAsync(result -> {
                    setLoginLoading(false);
                    if (result.success()) {
                        handleLoginSuccess(result.session());
                    } else {
                        showLoginError(result.message());
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setLoginLoading(false);
                        showLoginError("登录失败，请检查网络连接");
                    });
                    LOGGER.log(Level.WARNING, "Email login failed", ex);
                    return null;
                });
    }

    @FXML
    void onFeishuLogin() {
        clearLoginError();
        if (authService == null) {
            showLoginError("服务未初始化");
            return;
        }

        setLoginLoading(true);

        CompletableFuture.runAsync(() -> {
            // Step 1: Start the local callback server to receive the OAuth redirect
            try {
                startCallbackServer();
            } catch (IOException e) {
                Platform.runLater(() -> {
                    setLoginLoading(false);
                    showLoginError("无法启动本地回调服务器（端口 8080 可能被占用）");
                });
                LOGGER.log(Level.WARNING, "Failed to start OAuth callback server", e);
                return;
            }

            // Step 2: Open the Feishu OAuth page in the system browser
            String oauthUrl = authService.getFeishuOAuthUrl();
            Platform.runLater(() -> {
                openInBrowser(oauthUrl);
                // Don't clear loading - wait for the callback
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                setLoginLoading(false);
                showLoginError("无法打开飞书授权页面");
            });
            LOGGER.log(Level.WARNING, "Feishu OAuth URL generation failed", ex);
            return null;
        });
    }

    /**
     * Starts the local OAuth callback server.
     * When the callback is received, it processes the authorization code
     * on the JavaFX Application Thread.
     */
    private void startCallbackServer() throws IOException {
        stopCallbackServer();

        callbackServer = new OAuthCallbackServer(code -> {
            LOGGER.info("Received Feishu OAuth callback code.");
            Platform.runLater(() -> handleFeishuCallback(code));
        });
        callbackServer.start();
    }

    /**
     * Stops the callback server if it is running.
     * Public for cleanup during application shutdown.
     */
    public void stopCallbackServer() {
        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer = null;
        }
    }

    /**
     * Handles a Feishu OAuth callback with the authorization code.
     * Called externally when the OAuth redirect is intercepted.
     */
    public void handleFeishuCallback(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            showLoginError("飞书授权失败");
            return;
        }

        setLoginLoading(true);

        CompletableFuture.supplyAsync(() -> authService.loginWithFeishu(authorizationCode))
                .thenAcceptAsync(result -> {
                    setLoginLoading(false);
                    if (result.success()) {
                        handleLoginSuccess(result.session());
                    } else {
                        showLoginError(result.message());
                        LOGGER.log(Level.WARNING, "Feishu login failed: {0}", result.message());
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setLoginLoading(false);
                        String errorMsg = "飞书登录异常：" + (ex.getCause() != null ? ex.getCause().getMessage() : "请检查网络连接");
                        showLoginError(errorMsg);
                    });
                    LOGGER.log(Level.SEVERE, "Feishu login exception", ex);
                    return null;
                });
    }

    // ---- Registration handlers ----

    @FXML
    void onSendCode() {
        clearRegisterError();
        String email = regEmailField.getText();

        if (email == null || email.isBlank()) {
            showRegisterError("请输入邮箱地址");
            return;
        }

        sendCodeBtn.setDisable(true);
        sendCodeBtn.setText("发送中");

        CompletableFuture.supplyAsync(() -> authService.sendVerificationCode(email))
                .thenAcceptAsync(success -> {
                    if (success) {
                        sendCodeBtn.setText("已发送");
                        startCodeCooldown();
                    } else {
                        sendCodeBtn.setDisable(false);
                        sendCodeBtn.setText("发送");
                        showRegisterError("验证码发送失败，请重试");
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        sendCodeBtn.setDisable(false);
                        sendCodeBtn.setText("发送");
                        showRegisterError("发送失败，请检查网络连接");
                    });
                    LOGGER.log(Level.WARNING, "Send verification code failed", ex);
                    return null;
                });
    }

    @FXML
    void onRegister() {
        clearRegisterError();
        String email = regEmailField.getText();
        String code = regCodeField.getText();
        String password = regPasswordField.getText();
        String confirmPassword = regConfirmPasswordField.getText();

        if (email == null || email.isBlank()) {
            showRegisterError("请输入邮箱地址");
            return;
        }
        if (code == null || code.isBlank()) {
            showRegisterError("请输入验证码");
            return;
        }
        if (password == null || password.isBlank()) {
            showRegisterError("请设置密码");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showRegisterError("两次输入的密码不一致");
            return;
        }

        setRegisterLoading(true);

        CompletableFuture.supplyAsync(() -> authService.register(email, code, password))
                .thenAcceptAsync(result -> {
                    setRegisterLoading(false);
                    if (result.success()) {
                        showRegisterSuccess(result.message());
                        // Switch to login screen after success
                        onShowLogin();
                        // Prefill email for convenience
                        emailField.setText(email);
                        passwordField.clear();
                    } else {
                        showRegisterError(result.message());
                        LOGGER.log(Level.INFO, "Registration failed: {0}", result.message());
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setRegisterLoading(false);
                        String errorMsg = "注册失败：" + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                        showRegisterError(errorMsg);
                    });
                    LOGGER.log(Level.SEVERE, "Registration exception", ex);
                    return null;
                });
    }

    // ---- View switching ----

    @FXML
    void onShowRegister() {
        loginContainer.setVisible(false);
        loginContainer.setManaged(false);
        registerContainer.setVisible(true);
        registerContainer.setManaged(true);
        clearLoginError();
        clearRegisterError();
    }

    @FXML
    void onShowLogin() {
        registerContainer.setVisible(false);
        registerContainer.setManaged(false);
        loginContainer.setVisible(true);
        loginContainer.setManaged(true);
        clearLoginError();
        clearRegisterError();
    }

    // ---- Internal helpers ----

    private void handleLoginSuccess(Session session) {
        stopCallbackServer();
        if (onLoginSuccess != null) {
            onLoginSuccess.onSuccess(session);
        }
    }

    private void showLoginError(String message) {
        loginErrorLabel.setText(message);
        loginErrorLabel.setVisible(true);
        loginErrorLabel.setManaged(true);
    }

    private void clearLoginError() {
        loginErrorLabel.setText("");
        loginErrorLabel.setVisible(false);
        loginErrorLabel.setManaged(false);
    }

    private void showRegisterError(String message) {
        registerErrorLabel.setText(message);
        registerErrorLabel.getStyleClass().remove("success-label");
        if (!registerErrorLabel.getStyleClass().contains("login-error-label")) {
            registerErrorLabel.getStyleClass().add("login-error-label");
        }
        registerErrorLabel.setVisible(true);
        registerErrorLabel.setManaged(true);
    }

    private void showRegisterSuccess(String message) {
        registerErrorLabel.setText(message);
        registerErrorLabel.getStyleClass().remove("login-error-label");
        if (!registerErrorLabel.getStyleClass().contains("success-label")) {
            registerErrorLabel.getStyleClass().add("success-label");
        }
        registerErrorLabel.setVisible(true);
        registerErrorLabel.setManaged(true);
    }

    private void clearRegisterError() {
        registerErrorLabel.setText("");
        registerErrorLabel.setVisible(false);
        registerErrorLabel.setManaged(false);
    }

    private void setLoginLoading(boolean loading) {
        loginProgress.setVisible(loading);
        loginProgress.setManaged(loading);
        emailLoginBtn.setDisable(loading);
        feishuLoginBtn.setDisable(loading);
        emailField.setDisable(loading);
        passwordField.setDisable(loading);
    }

    private void setRegisterLoading(boolean loading) {
        registerProgress.setVisible(loading);
        registerProgress.setManaged(loading);
        registerBtn.setDisable(loading);
        regEmailField.setDisable(loading);
        regCodeField.setDisable(loading);
        regPasswordField.setDisable(loading);
        regConfirmPasswordField.setDisable(loading);
    }

    /**
     * Starts a 60-second cooldown on the send code button.
     */
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

    /**
     * Opens a URL in the system default browser.
     */
    void openInBrowser(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            desktop.browse(new java.net.URI(url));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open browser for URL: " + url, e);
            showLoginError("无法打开浏览器，请手动访问: " + url);
        }
    }

    // ---- Getters for testing ----

    VBox getLoginContainer() { return loginContainer; }
    VBox getRegisterContainer() { return registerContainer; }
    Label getLoginErrorLabel() { return loginErrorLabel; }
    Label getRegisterErrorLabel() { return registerErrorLabel; }
    TextField getEmailField() { return emailField; }
    PasswordField getPasswordField() { return passwordField; }
    TextField getRegEmailField() { return regEmailField; }
    TextField getRegCodeField() { return regCodeField; }
    PasswordField getRegPasswordField() { return regPasswordField; }
    PasswordField getRegConfirmPasswordField() { return regConfirmPasswordField; }
    Button getFeishuLoginBtn() { return feishuLoginBtn; }
    Button getEmailLoginBtn() { return emailLoginBtn; }
    Button getSendCodeBtn() { return sendCodeBtn; }
    Button getRegisterBtn() { return registerBtn; }
    ProgressIndicator getLoginProgress() { return loginProgress; }
    ProgressIndicator getRegisterProgress() { return registerProgress; }
}
