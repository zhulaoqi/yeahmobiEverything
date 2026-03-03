package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.repository.local.SettingsRepository;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the settings page.
 * <p>
 * Displays user profile information, supports theme toggling,
 * password change, and logout functionality.
 * </p>
 */
public class SettingsController {

    private static final Logger LOGGER = Logger.getLogger(SettingsController.class.getName());
    static final String THEME_KEY = "theme";
    static final String THEME_DARK = "dark";
    static final String THEME_LIGHT = "light";
    static final String THEME_SYSTEM = "system";

    // User info labels
    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label userIdLabel;
    @FXML private Label loginTypeLabel;
    @FXML private Label createdAtLabel;

    // Password section (only visible for email login)
    @FXML private VBox passwordSection;

    // Theme selector
    @FXML private ChoiceBox<String> themeChoice;

    private SettingsRepository settingsRepository;
    private boolean darkTheme;
    private String userId;
    private String username;
    private String email;
    private long createdAt;
    private Consumer<Boolean> onThemeToggle;
    private Runnable onLogout;
    private boolean suppressThemeChange;

    public void setSettingsRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public void setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
        // Update theme choice selection if available
        if (themeChoice != null) {
            String current = themeChoice.getSelectionModel().getSelectedItem();
            if (!"跟随系统".equals(current)) {
                suppressThemeChange = true;
                themeChoice.getSelectionModel().select(darkTheme ? "深色" : "浅色");
                suppressThemeChange = false;
            }
        }
    }

    public void setOnThemeToggle(Consumer<Boolean> onThemeToggle) {
        this.onThemeToggle = onThemeToggle;
        applySavedTheme();
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    /**
     * Set user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
        updateUserInfo();
    }

    /**
     * Set username
     */
    public void setUsername(String username) {
        this.username = username;
        updateUserInfo();
    }

    /**
     * Set email (optional, for display)
     */
    public void setEmail(String email) {
        this.email = email;
        updateUserInfo();
    }

    /**
     * Set created time
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
        updateUserInfo();
    }

    /**
     * Initialize the controller (called after FXML loaded)
     */
    @FXML
    public void initialize() {
        // Initialize theme choice
        if (themeChoice != null) {
            themeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (suppressThemeChange) {
                    return;
                }
                onThemeChange(newVal);
            });
        }
    }

    /**
     * Update user information display
     */
    private void updateUserInfo() {
        if (usernameLabel != null && username != null) {
            usernameLabel.setText(username);
        }
        if (emailLabel != null) {
            emailLabel.setText(email != null ? email : "未设置");
        }
        if (userIdLabel != null && userId != null) {
            userIdLabel.setText(userId);
        }

        // Default to email login (can be enhanced later)
        if (loginTypeLabel != null) {
            loginTypeLabel.setText("邮箱账号");
        }

        // Show password section by default
        if (passwordSection != null) {
            passwordSection.setManaged(true);
            passwordSection.setVisible(true);
        }

        if (createdAtLabel != null) {
            if (createdAt > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                createdAtLabel.setText(sdf.format(new Date(createdAt)));
            } else {
                createdAtLabel.setText("未知");
            }
        }
    }

    /**
     * Handle theme change from ChoiceBox
     */
    private void onThemeChange(String newTheme) {
        if (newTheme == null) return;

        switch (newTheme) {
            case "浅色":
                setDarkTheme(false);
                applyTheme(false, THEME_LIGHT);
                break;
            case "深色":
                setDarkTheme(true);
                applyTheme(true, THEME_DARK);
                break;
            case "跟随系统":
                boolean systemDark = detectSystemDarkTheme();
                setDarkTheme(systemDark);
                applyTheme(systemDark, THEME_SYSTEM);
                break;
        }
    }

    /**
     * Apply theme and persist
     */
    private void applyTheme(boolean dark, String preference) {
        darkTheme = dark;

        // Persist the preference
        if (settingsRepository != null) {
            try {
                settingsRepository.saveSetting(THEME_KEY, preference);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save theme setting", e);
            }
        }

        // Notify the main controller to apply the theme
        if (onThemeToggle != null) {
            onThemeToggle.accept(darkTheme);
        }
    }

    private void applySavedTheme() {
        if (settingsRepository == null || themeChoice == null) {
            return;
        }
        try {
            String saved = settingsRepository.getSetting(THEME_KEY).orElse(THEME_LIGHT);
            switch (saved) {
                case THEME_DARK -> {
                    suppressThemeChange = true;
                    themeChoice.getSelectionModel().select("深色");
                    suppressThemeChange = false;
                    setDarkTheme(true);
                }
                case THEME_SYSTEM -> {
                    suppressThemeChange = true;
                    themeChoice.getSelectionModel().select("跟随系统");
                    suppressThemeChange = false;
                    boolean systemDark = detectSystemDarkTheme();
                    setDarkTheme(systemDark);
                }
                default -> {
                    suppressThemeChange = true;
                    themeChoice.getSelectionModel().select("浅色");
                    suppressThemeChange = false;
                    setDarkTheme(false);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load theme setting", e);
        }
    }

    private boolean detectSystemDarkTheme() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                String output = runCommand(new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"});
                return output != null && output.toLowerCase().contains("dark");
            }
            if (os.contains("win")) {
                String output = runCommand(new String[]{"reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"});
                return output != null && output.contains("0x0");
            }
            if (os.contains("linux")) {
                String output = runCommand(new String[]{"gsettings", "get",
                        "org.gnome.desktop.interface", "color-scheme"});
                return output != null && output.toLowerCase().contains("dark");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "System theme detection failed", e);
        }
        return darkTheme;
    }

    private String runCommand(String[] command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        process.waitFor();
        return new String(bytes).trim();
    }

    /**
     * Handle change password button
     */
    @FXML
    public void onChangePassword() {
        // TODO: implement password change dialog
        DialogHelper.showInformation("", "密码修改功能即将推出，敬请期待！");
    }

    /**
     * Handle logout button
     */
    @FXML
    public void onLogout() {
        // Show confirmation dialog
        DialogHelper.showConfirmation("", "确定要退出当前账号吗？")
            .ifPresent(response -> {
                if (response == ButtonType.OK) {
                    if (onLogout != null) {
                        onLogout.run();
                    }
                }
            });
    }

    // ---- Package-private accessors for testing ----

    ChoiceBox<String> getThemeChoice() { return themeChoice; }
    boolean isDarkTheme() { return darkTheme; }
    String getUserId() { return userId; }
    String getUsername() { return username; }
}
