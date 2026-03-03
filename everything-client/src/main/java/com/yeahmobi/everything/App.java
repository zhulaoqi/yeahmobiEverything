package com.yeahmobi.everything;

import com.yeahmobi.everything.admin.AdminService;
import com.yeahmobi.everything.admin.AdminServiceImpl;
import com.yeahmobi.everything.auth.AuthService;
import com.yeahmobi.everything.auth.AuthServiceImpl;
import com.yeahmobi.everything.auth.EmailService;
import com.yeahmobi.everything.auth.FeishuOAuthService;
import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.cli.LocalCliGatewayServer;
import com.yeahmobi.everything.auth.SmtpEmailService;
import com.yeahmobi.everything.chat.ChatService;
import com.yeahmobi.everything.chat.ChatServiceImpl;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.feedback.FeedbackService;
import com.yeahmobi.everything.feedback.FeedbackServiceImpl;
import com.yeahmobi.everything.hrassist.HrCaseService;
import com.yeahmobi.everything.hrassist.HrCaseServiceImpl;
import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.knowledge.KnowledgeBaseServiceImpl;
import com.yeahmobi.everything.notification.FeishuNotifier;
import com.yeahmobi.everything.notification.FeishuNotifierImpl;
import com.yeahmobi.everything.personalskill.PersonalSkillService;
import com.yeahmobi.everything.personalskill.PersonalSkillServiceImpl;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.cache.CacheServiceImpl;
import com.yeahmobi.everything.repository.cache.RedisManager;
import com.yeahmobi.everything.repository.local.ChatRepositoryImpl;
import com.yeahmobi.everything.repository.local.FavoriteRepositoryImpl;
import com.yeahmobi.everything.repository.local.LocalDatabaseManager;
import com.yeahmobi.everything.repository.local.PersonalSkillRepositoryImpl;
import com.yeahmobi.everything.repository.local.SessionRepositoryImpl;
import com.yeahmobi.everything.repository.local.SettingsRepository;
import com.yeahmobi.everything.repository.local.SettingsRepositoryImpl;
import com.yeahmobi.everything.repository.local.UsageRepositoryImpl;
import com.yeahmobi.everything.repository.mysql.FeedbackRepositoryImpl;
import com.yeahmobi.everything.repository.mysql.KnowledgeFileRepositoryImpl;
import com.yeahmobi.everything.repository.mysql.MySQLDatabaseManager;
import com.yeahmobi.everything.repository.mysql.SkillKnowledgeBindingRepository;
import com.yeahmobi.everything.repository.mysql.SkillKnowledgeBindingRepositoryImpl;
import com.yeahmobi.everything.repository.mysql.SkillRepositoryImpl;
import com.yeahmobi.everything.skill.SkillService;
import com.yeahmobi.everything.skill.SkillServiceImpl;
import com.yeahmobi.everything.ui.MainController;
import com.yeahmobi.everything.workfollowup.WorkFollowupService;
import com.yeahmobi.everything.workfollowup.WorkFollowupServiceImpl;
import com.yeahmobi.everything.workfollowup.WorkReminderEmailDispatcher;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.Region;

import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for the Yeahmobi Everything desktop application.
 * <p>
 * Initializes all infrastructure (local SQLite, MySQL, Redis, configuration),
 * wires up service layers, and launches the JavaFX primary stage with the
 * login screen. After successful login, transitions to the main application window.
 * </p>
 * <p>
 * Supports cross-platform system tray integration:
 * <ul>
 *   <li>Windows: Uses {@link java.awt.SystemTray} for minimize-to-tray behavior</li>
 *   <li>macOS: Adapts Dock behavior (app stays running when window is closed)</li>
 * </ul>
 * </p>
 * <p>
 * Minimum window size is enforced at 800×600 pixels.
 * </p>
 *
 * @see Config
 * @see LocalDatabaseManager
 * @see MySQLDatabaseManager
 * @see RedisManager
 */
public class App extends Application {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    /** Minimum window width in pixels. */
    static final double MIN_WIDTH = 800;

    /** Minimum window height in pixels. */
    static final double MIN_HEIGHT = 600;

    /** Default window width in pixels. */
    private static final double DEFAULT_WIDTH = 1100;

    /** Default window height in pixels. */
    private static final double DEFAULT_HEIGHT = 700;

    // Infrastructure
    private Config config;
    private LocalDatabaseManager localDatabaseManager;
    private MySQLDatabaseManager mysqlDatabaseManager;
    private RedisManager redisManager;

    // Services
    private CacheService cacheService;
    private AuthService authService;
    private SkillService skillService;
    private ChatService chatService;
    private FeedbackService feedbackService;
    private AdminService adminService;
    private KnowledgeBaseService knowledgeBaseService;
    private SettingsRepository settingsRepository;
    private SkillKnowledgeBindingRepository bindingRepository;
    private PersonalSkillService personalSkillService;
    private WorkFollowupService workFollowupService;
    private WorkReminderEmailDispatcher workReminderEmailDispatcher;
    private HrCaseService hrCaseService;

    // System tray
    private TrayIcon trayIcon;

    // Primary stage reference
    private Stage primaryStage;
    private LocalCliGatewayServer localCliGatewayServer;

    // Auth controller for OAuth callback cleanup
    private com.yeahmobi.everything.ui.AuthController authController;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        try {
            primaryStage.initStyle(StageStyle.TRANSPARENT);
            initializeInfrastructure();
            initializeServices();
            startLocalCliGateway();
            setupSystemTray(primaryStage);
            showLoginScreen(primaryStage);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start application", e);
            showErrorAndExit("启动失败", "应用程序启动失败：" + e.getMessage());
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Shutting down Yeahmobi Everything...");
        removeSystemTray();

        // Stop OAuth callback server if running
        if (authController != null) {
            authController.stopCallbackServer();
        }

        if (localDatabaseManager != null) {
            localDatabaseManager.shutdown();
        }
        if (mysqlDatabaseManager != null) {
            mysqlDatabaseManager.shutdown();
        }
        if (redisManager != null) {
            redisManager.close();
        }
        if (localCliGatewayServer != null) {
            localCliGatewayServer.stop();
        }
        stopWorkReminderDispatcher();

        LOGGER.info("Application shutdown complete.");
    }

    // ---- Infrastructure initialization ----

    /**
     * Initializes all infrastructure components: Config, SQLite, MySQL, Redis.
     * SQLite is required; MySQL and Redis failures are logged as warnings
     * but do not prevent the application from starting.
     */
    void initializeInfrastructure() throws IOException, SQLException {
        // 1. Load configuration
        config = Config.getInstance();
        LOGGER.info("Configuration loaded.");

        // 2. Initialize local SQLite database (required)
        localDatabaseManager = new LocalDatabaseManager(config);
        localDatabaseManager.initialize();
        LOGGER.info("Local SQLite database initialized at: " + localDatabaseManager.getDbPath());

        // 3. Initialize MySQL (optional - log warning on failure)
        try {
            mysqlDatabaseManager = new MySQLDatabaseManager(config);
            mysqlDatabaseManager.initialize();
            LOGGER.info("MySQL database connected: " + mysqlDatabaseManager.getJdbcUrl());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "MySQL connection failed. Some features may be unavailable.", e);
            mysqlDatabaseManager = null;
        }

        // 4. Initialize Redis (optional - log warning on failure)
        try {
            redisManager = new RedisManager(config);
            if (redisManager.isAvailable()) {
                LOGGER.info("Redis connected: " + config.getRedisHost() + ":" + config.getRedisPort());
            } else {
                LOGGER.warning("Redis is not available. Caching will be disabled.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis initialization failed. Caching will be disabled.", e);
            redisManager = null;
        }
    }

    /**
     * Initializes all service layer components by wiring dependencies.
     */
    void initializeServices() {
        // Use the longer timeout between LLM and AgentScope for shared HTTP client
        int llmTimeout = config.getLlmApiTimeout();
        int agentScopeTimeout = config.getAgentScopeRequestTimeoutMs();
        int sharedTimeout = Math.max(llmTimeout, agentScopeTimeout);
        HttpClientUtil httpClientUtil = new HttpClientUtil(Duration.ofMillis(sharedTimeout));

        // Cache service (gracefully handles null RedisManager)
        cacheService = (redisManager != null)
                ? new CacheServiceImpl(redisManager)
                : createNoOpCacheService();

        // Local repositories
        var sessionRepository = new SessionRepositoryImpl(localDatabaseManager);
        var chatRepository = new ChatRepositoryImpl(localDatabaseManager);
        var favoriteRepository = new FavoriteRepositoryImpl(localDatabaseManager);
        var usageRepository = new UsageRepositoryImpl(localDatabaseManager);
        var personalSkillRepository = new PersonalSkillRepositoryImpl(localDatabaseManager);
        settingsRepository = new SettingsRepositoryImpl(localDatabaseManager);

        // MySQL repositories (may be null if MySQL is unavailable)
        var userRepository = (mysqlDatabaseManager != null)
                ? new com.yeahmobi.everything.repository.mysql.UserRepositoryImpl(mysqlDatabaseManager)
                : null;
        var skillRepository = (mysqlDatabaseManager != null)
                ? new SkillRepositoryImpl(mysqlDatabaseManager)
                : null;
        var feedbackRepository = (mysqlDatabaseManager != null)
                ? new FeedbackRepositoryImpl(mysqlDatabaseManager)
                : null;
        var knowledgeFileRepository = (mysqlDatabaseManager != null)
                ? new KnowledgeFileRepositoryImpl(mysqlDatabaseManager)
                : null;
        bindingRepository = (mysqlDatabaseManager != null)
                ? new SkillKnowledgeBindingRepositoryImpl(mysqlDatabaseManager)
                : null;

        // Feishu OAuth service
        var feishuOAuthService = new FeishuOAuthService(config, httpClientUtil);

        // Email service (SMTP)
        EmailService emailService = new SmtpEmailService(config);

        // Auth service
        authService = new AuthServiceImpl(sessionRepository, userRepository, cacheService, feishuOAuthService, emailService);

        // Anthropic skills import is performed manually by admin for persistent seeding.

        // Skill service
        skillService = (skillRepository != null)
                ? new SkillServiceImpl(skillRepository, cacheService, favoriteRepository, usageRepository, chatRepository)
                : null;

        // Knowledge base service
        knowledgeBaseService = (knowledgeFileRepository != null && bindingRepository != null)
                ? new KnowledgeBaseServiceImpl(knowledgeFileRepository, bindingRepository, cacheService)
                : null;

        // Chat service
        chatService = new ChatServiceImpl(
                chatRepository,
                cacheService,
                skillRepository,
                httpClientUtil,
                config,
                knowledgeBaseService
        );

        // Feedback service
        FeishuNotifier feishuNotifier = new FeishuNotifierImpl(config, httpClientUtil);
        feedbackService = (feedbackRepository != null)
                ? new FeedbackServiceImpl(feedbackRepository, feishuNotifier)
                : null;

        // Personal skill service (local, always available)
        personalSkillService = new PersonalSkillServiceImpl(
                personalSkillRepository,
                feishuNotifier,
                skillRepository,
                cacheService
        );

        // Admin service
        adminService = (skillRepository != null && feedbackRepository != null && userRepository != null)
                ? new AdminServiceImpl(skillRepository, feedbackRepository, cacheService, knowledgeBaseService, userRepository)
                : null;

        // Work follow-up service (local persistent, always available)
        workFollowupService = new WorkFollowupServiceImpl();
        hrCaseService = new HrCaseServiceImpl();

        LOGGER.info("All services initialized.");
    }

    private void startLocalCliGateway() {
        try {
            int port = config.getLocalCliGatewayPort();
            String token = config.getLocalCliGatewayToken();
            localCliGatewayServer = new LocalCliGatewayServer(port, token);
            localCliGatewayServer.start();
            LOGGER.info("Local CLI gateway started on 127.0.0.1:" + port);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to start local CLI gateway", e);
            localCliGatewayServer = null;
        }
    }

    // ---- UI Screen Management ----

    /**
     * Shows the login screen as the initial view.
     */
    void showLoginScreen(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent loginRoot = loader.load();

        this.authController = loader.getController();
        authController.setAuthService(authService);
        authController.setOnLoginSuccess(session -> onLoginSuccess(stage, session));
        authController.setStage(stage);

        Scene scene = new Scene(loginRoot, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        applyTheme(scene);
        applyWindowClip(loginRoot);

        stage.setTitle("");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);

        // Set application icon (PNG)
        try {
            var iconUrl = getClass().getResource("/images/app-icon.png");
            if (iconUrl != null) {
                stage.getIcons().add(new javafx.scene.image.Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to load app icon", e);
        }

        stage.show();

        // Attempt auto-login after the stage is shown
        authController.tryAutoLogin();
    }

    /**
     * Handles successful login by transitioning to the main application window.
     */
    void onLoginSuccess(Stage stage, Session session) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-wechat.fxml"));
            Parent mainRoot = loader.load();

            MainController mainController = loader.getController();
            mainController.setSkillService(skillService);
            mainController.setChatService(chatService);
            mainController.setFeedbackService(feedbackService);
            mainController.setAdminService(adminService);
            mainController.setKnowledgeBaseService(knowledgeBaseService);
            mainController.setBindingRepository(bindingRepository);
            mainController.setSettingsRepository(settingsRepository);
            mainController.setPersonalSkillService(personalSkillService);
            mainController.setWorkFollowupService(workFollowupService);
            mainController.setHrCaseService(hrCaseService);
            mainController.setUserId(session.userId());
            mainController.setUsername(session.username());
            mainController.setUserEmail(session.email());
            mainController.setUserCreatedAt(session.createdAt());
            mainController.setIsAdmin(session.isAdmin());
            mainController.setStage(stage);

            // Set up Skill click callback to navigate to chat
            mainController.setOnSkillClick(mainController::loadChatPage);

            // Set up logout callback to clear session and return to login screen
            mainController.setOnLogout(() -> {
                try {
                    if (authService != null) {
                        authService.logout();
                    }
                    stopWorkReminderDispatcher();
                    showLoginScreen(stage);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to show login screen after logout", e);
                }
            });

            Scene scene = new Scene(mainRoot, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            scene.setFill(Color.TRANSPARENT);
            applyTheme(scene);
            applyWindowClip(mainRoot);

            stage.setScene(scene);
            stage.setTitle("");
            stage.setMinWidth(MIN_WIDTH);
            stage.setMinHeight(MIN_HEIGHT);

            // Load the default view (compatible with both layouts)
            // onNavSkills() will delegate to onIconSkills() for WeChat layout
            mainController.onNavSkills();
            startWorkReminderDispatcher(session.email());

            LOGGER.info("User logged in: " + session.username() + " (" + session.loginType() + ")");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load main window", e);
            showError("加载失败", "无法加载主界面：" + e.getMessage());
        }
    }


    // ---- Theme Management ----

    /**
     * Applies the configured theme (light or dark) to the given scene.
     */
    private void applyTheme(Scene scene) {
        String theme = config.getAppTheme();
        String cssPath = "dark".equalsIgnoreCase(theme)
                ? "/css/dark-theme.css"
                : "/css/light-theme.css";

        var cssUrl = getClass().getResource(cssPath);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
    }

    private void applyWindowClip(Parent root) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        root.setClip(clip);
        if (root instanceof Region region) {
            clip.widthProperty().bind(region.widthProperty());
            clip.heightProperty().bind(region.heightProperty());
        } else {
            root.layoutBoundsProperty().addListener((obs, oldVal, bounds) -> {
                clip.setWidth(bounds.getWidth());
                clip.setHeight(bounds.getHeight());
            });
        }
        root.getStyleClass().addListener((javafx.collections.ListChangeListener<? super String>) c -> {
            boolean maximized = root.getStyleClass().contains("window-maximized");
            clip.setArcWidth(maximized ? 0 : 24);
            clip.setArcHeight(maximized ? 0 : 24);
        });
    }

    // ---- System Tray ----

    /**
     * Sets up system tray integration based on the current operating system.
     * <ul>
     *   <li>Windows: Adds a system tray icon; closing the window minimizes to tray</li>
     *   <li>macOS: Configures Dock behavior so the app stays running when the window is closed</li>
     * </ul>
     */
    void setupSystemTray(Stage stage) {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            setupMacOSDock(stage);
        } else if (os.contains("win")) {
            setupWindowsSystemTray(stage);
        }
        // Linux and other platforms: default JavaFX behavior (close window = exit)
    }

    /**
     * Sets up Windows system tray with an icon and context menu.
     * When the user closes the window, the app minimizes to the system tray
     * instead of exiting.
     */
    private void setupWindowsSystemTray(Stage stage) {
        if (!SystemTray.isSupported()) {
            LOGGER.info("System tray is not supported on this platform.");
            return;
        }

        // Keep the JavaFX application running even when all windows are hidden
        Platform.setImplicitExit(false);

        try {
            SystemTray systemTray = SystemTray.getSystemTray();

            // Create a simple tray icon (16x16 default)
            Image trayImage = createTrayImage();
            PopupMenu popup = new PopupMenu();

            MenuItem showItem = new MenuItem("显示窗口");
            ActionListener showAction = e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            });
            showItem.addActionListener(showAction);

            MenuItem exitItem = new MenuItem("退出");
            exitItem.addActionListener(e -> {
                Platform.runLater(() -> {
                    removeSystemTray();
                    Platform.exit();
                });
            });

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(trayImage, config.getAppName(), popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(showAction); // Double-click to show

            systemTray.add(trayIcon);

            // Override close behavior: hide window instead of exiting
            stage.setOnCloseRequest(event -> {
                event.consume();
                stage.hide();
                if (trayIcon != null) {
                    trayIcon.displayMessage(
                            config.getAppName(),
                            "应用已最小化到系统托盘",
                            TrayIcon.MessageType.INFO
                    );
                }
            });

            LOGGER.info("Windows system tray initialized.");
        } catch (AWTException e) {
            LOGGER.log(Level.WARNING, "Failed to add system tray icon", e);
        }
    }

    /**
     * Sets up macOS Dock behavior.
     * On macOS, closing the window does not exit the application;
     * the app remains accessible via the Dock icon.
     */
    private void setupMacOSDock(Stage stage) {
        // On macOS, keep the app running when the window is closed
        Platform.setImplicitExit(false);

        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });

        // macOS Dock click re-shows the window
        // This is handled by the JavaFX runtime on macOS when the Dock icon is clicked
        // and the app is still running. We register a listener to show the stage.
        if (Taskbar.isTaskbarSupported()) {
            try {
                Taskbar.getTaskbar();
                // Set a badge or icon if needed in the future
                LOGGER.info("macOS Taskbar/Dock integration initialized.");
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Taskbar integration not fully available", e);
            }
        }

        LOGGER.info("macOS Dock behavior configured.");
    }

    /**
     * Creates a simple tray icon image.
     * Uses a small generated image with the app's primary color.
     */
    private Image createTrayImage() {
        // Create a 16x16 tray icon: green-to-blue gradient rounded rect + white "E" + sparkle
        int size = 16;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Gradient background (green → blue)
        java.awt.GradientPaint gradient = new java.awt.GradientPaint(
                0, 0, new java.awt.Color(0x1F, 0x88, 0x3D),
                size, size, new java.awt.Color(0x09, 0x69, 0xDA));
        g2d.setPaint(gradient);
        g2d.fillRoundRect(1, 1, size - 2, size - 2, 4, 4);

        // White "E" letter
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
        g2d.drawString("E", 3, 12);

        // Small sparkle dot (top-right)
        g2d.fillOval(11, 2, 3, 3);

        g2d.dispose();
        return image;
    }

    /**
     * Removes the system tray icon if it was added.
     */
    private void removeSystemTray() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    // ---- Error Handling ----

    /**
     * Shows an error dialog and exits the application.
     */
    private void showErrorAndExit(String title, String message) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to show error dialog: " + message, e);
        }
        Platform.exit();
        System.exit(1);
    }

    /**
     * Shows a non-fatal error dialog.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Creates a no-op CacheService for when Redis is unavailable.
     * All cache operations return empty/false, ensuring the app
     * degrades gracefully without caching.
     */
    private CacheService createNoOpCacheService() {
        return new NoOpCacheService();
    }

    // ---- Accessors for testing ----

    Config getConfig() { return config; }
    LocalDatabaseManager getLocalDatabaseManager() { return localDatabaseManager; }
    MySQLDatabaseManager getMysqlDatabaseManager() { return mysqlDatabaseManager; }
    RedisManager getRedisManager() { return redisManager; }
    AuthService getAuthService() { return authService; }
    SkillService getSkillService() { return skillService; }
    ChatService getChatService() { return chatService; }
    FeedbackService getFeedbackService() { return feedbackService; }
    AdminService getAdminService() { return adminService; }
    KnowledgeBaseService getKnowledgeBaseService() { return knowledgeBaseService; }
    Stage getPrimaryStage() { return primaryStage; }

    private synchronized void startWorkReminderDispatcher(String loginEmail) {
        stopWorkReminderDispatcher();
        if (workFollowupService == null || config == null) {
            return;
        }
        workReminderEmailDispatcher = new WorkReminderEmailDispatcher(workFollowupService, config, loginEmail);
        workReminderEmailDispatcher.start();
    }

    private synchronized void stopWorkReminderDispatcher() {
        if (workReminderEmailDispatcher != null) {
            workReminderEmailDispatcher.stop();
            workReminderEmailDispatcher = null;
        }
    }

    /**
     * Application entry point.
     * Launches the JavaFX application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
