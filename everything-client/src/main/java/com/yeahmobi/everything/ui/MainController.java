package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.admin.AdminService;
import com.yeahmobi.everything.chat.ChatService;
import com.yeahmobi.everything.chat.ChatSession;
import com.yeahmobi.everything.feedback.FeedbackService;
import com.yeahmobi.everything.hrassist.HrCaseService;
import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.personalskill.PersonalSkillService;
import com.yeahmobi.everything.repository.local.SettingsRepository;
import com.yeahmobi.everything.repository.mysql.SkillKnowledgeBindingRepository;
import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillService;
import com.yeahmobi.everything.skill.SkillType;
import com.yeahmobi.everything.workfollowup.WorkFollowupService;
import com.yeahmobi.everything.workfollowup.WorkTodo;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.geometry.Rectangle2D;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the main application window.
 * <p>
 * Manages left-side navigation and right-side content area page switching,
 * as well as light/dark theme toggling.
 * </p>
 */
public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final String LIGHT_THEME = "/css/light-theme.css";
    private static final String DARK_THEME = "/css/dark-theme.css";
    private static final String NAV_ITEM_ACTIVE = "sidebar-item-active";

    // Old layout (legacy)
    @FXML private VBox navBar;
    
    // WeChat-style layout
    @FXML private VBox iconBar;
    @FXML private VBox skillSidebar;
    @FXML private javafx.scene.layout.BorderPane chatArea;
    @FXML private javafx.scene.control.Label skillSidebarTitle;
    @FXML private javafx.scene.control.TextField skillSearchField;
    @FXML private VBox skillSidebarHeader;
    @FXML private VBox skillListContainer;
    @FXML private VBox skillEmptyState;
    
    // Skill view type state
    private enum SkillViewType {
        ALL,        // Skills - 显示全部
        MARKETPLACE, // 集市 - 显示外部可安装的
        FAVORITES   // 我的收藏 - 显示收藏的
    }

    private enum RecommendActionType {
        SEARCH_INFO,
        READ_FILES,
        CREATE_TODO,
        SET_REMINDER,
        HR_EXEC
    }
    private SkillViewType currentSkillViewType = SkillViewType.ALL;
    
    @FXML private StackPane contentArea;
    @FXML private HBox windowBar;
    @FXML private HBox windowBtnGroup;
    @FXML private Button windowCloseBtn;
    @FXML private Button windowMinBtn;
    @FXML private Button windowMaxBtn;
    @FXML private VBox todoReminderBubble;
    @FXML private Label todoReminderLabel;
    @FXML private Button todoReminderLaterBtn;
    @FXML private Button todoReminderDoneBtn;

    // Old nav buttons (legacy)
    @FXML private Button navSkills;
    @FXML private Button navMarket;
    @FXML private Button navHistory;
    @FXML private Button navFavorites;
    @FXML private Button navPersonalSkills;
    @FXML private Button navFeedback;
    @FXML private Button navSettings;
    @FXML private Button navAdmin;
    @FXML private Button navLogout;
    
    // WeChat-style icon buttons
    @FXML private Button iconSkills;
    @FXML private Button iconMarket;
    @FXML private Button iconFavorites;
    @FXML private Button iconTodos;
    @FXML private Button iconPersonal;
    @FXML private Button iconSettings;
    @FXML private Button iconAdmin;
    @FXML private Button iconLogout;
    private Region iconBarSpacer;

    private Button activeNavButton;
    // SVG icon paths (Material Design style, 24x24 viewport)
    /** Chat bubble - Skills */
    private static final String ICON_CHAT = "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z";
    /** Shopping bag - Market */
    private static final String ICON_BAG = "M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm6 16H6V8h12v12z";
    /** Star outline - Favorites */
    private static final String ICON_STAR = "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z";
    /** Star filled - Favorited */
    private static final String ICON_STAR_FILLED = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
    /** Person - Personal */
    private static final String ICON_PERSON = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    /** Check-list - Todos */
    private static final String ICON_TODOS = "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 14l-3-3 1.41-1.41L10 14.17l5.59-5.59L17 10l-7 7z";
    /** Gear - Settings */
    private static final String ICON_GEAR = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.44.17-.47.41l-.36 2.54c-.59.24-1.13.56-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    /** Person with glasses - Admin (uses EVEN_ODD fill for glasses cutout) */
    private static final String ICON_ADMIN = "M12 2C9.24 2 7 4.24 7 7c0 2.76 2.24 5 5 5s5-2.24 5-5-2.24-5-5-5zM7.7 6.3A1.8 1.8 0 1 1 11.3 6.3 1.8 1.8 0 1 1 7.7 6.3zM12.7 6.3A1.8 1.8 0 1 1 16.3 6.3 1.8 1.8 0 1 1 12.7 6.3zM4 20c0-3.31 3.58-6 8-6s8 2.69 8 6z";
    /** Power button - Logout */
    private static final String ICON_POWER = "M13 3h-2v10h2V3zm4.83 2.17l-1.42 1.42C17.99 7.86 19 9.81 19 12c0 3.87-3.13 7-7 7s-7-3.13-7-7c0-2.19 1.01-4.14 2.58-5.42L6.17 5.17C4.23 6.82 3 9.26 3 12c0 4.97 4.03 9 9 9s9-4.03 9-9c0-2.74-1.23-5.18-3.17-6.83z";

    private boolean darkTheme = false;
    private Stage stage;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean windowMaximized = false;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;

    private SkillService skillService;
    private ChatService chatService;
    private FeedbackService feedbackService;
    private AdminService adminService;
    private KnowledgeBaseService knowledgeBaseService;
    private PersonalSkillService personalSkillService;
    private WorkFollowupService workFollowupService;
    private HrCaseService hrCaseService;
    private Timeline todoReminderTimeline;
    private WorkTodo activeReminderTodo;
    private SkillKnowledgeBindingRepository bindingRepository;
    private SettingsRepository settingsRepository;
    private String userId;
    private String username;
    private String userEmail;
    private long userCreatedAt;
    private boolean isAdmin;

    /** Callback invoked when a user clicks on a Skill card to enter chat. */
    private SkillController.SkillClickCallback onSkillClick;
    private ChatController currentChatController;
    /**
     * Temporary pin list for marketplace-entered skills.
     * - When user clicks "进入聊天使用技能" from marketplace preview, we pin the skill into Skills list immediately.
     * - If the user never sends any message (conversation history stays empty), the pin is removed when leaving chat,
     *   so next refresh it disappears.
     */
    private final java.util.Set<String> marketplacePinnedSkillIds = new java.util.LinkedHashSet<>();

    /** Callback invoked when the user logs out from the settings page. */
    private Runnable logoutCallback;

    @FXML
    public void initialize() {
        // Detect layout type
        boolean isWeChatLayout = (iconBar != null && skillSidebar != null);
        boolean isLegacyLayout = (navBar != null && navSkills != null);
        
        if (isLegacyLayout) {
            activeNavButton = navSkills;
        }
        
        setIsAdmin(false);
        setupWindowControls();
        setupWindowButtonSymbols();
        setupExtendedWindowDragZones();
        
        // WeChat-style layout initialization
        if (isWeChatLayout) {
            setupWeChatLayout();
        }
    }
    
    private void setupWeChatLayout() {
        normalizeIconBarStructure();
        // Setup SVG icons for sidebar buttons
        setupIconGraphics();

        // Lock HBox growth rules to prevent sidebar interactions affecting icon bar
        if (iconBar != null) {
            HBox.setHgrow(iconBar, Priority.NEVER);
            // Enforce fixed width even after layout changes
            iconBar.setMinWidth(ICON_BAR_FIXED_WIDTH);
            iconBar.setPrefWidth(ICON_BAR_FIXED_WIDTH);
            iconBar.setMaxWidth(ICON_BAR_FIXED_WIDTH);
        }
        if (skillSidebar != null) {
            HBox.setHgrow(skillSidebar, Priority.NEVER);
            skillSidebar.setMinWidth(SKILL_SIDEBAR_MIN_WIDTH);
            skillSidebar.setPrefWidth(SKILL_SIDEBAR_PREF_WIDTH);
            skillSidebar.setMaxWidth(SKILL_SIDEBAR_MAX_WIDTH);
        }
        if (chatArea != null) {
            HBox.setHgrow(chatArea, Priority.ALWAYS);
        }
        
        // Setup skill search
        skillSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterSkillList(newVal);
        });
        
        // Load skills into the middle panel
        loadSkillListToSidebar();
    }
    
    private static final String ICON_COLOR_INACTIVE = "#999999";
    private static final String ICON_COLOR_HOVER = "#333333";
    private static final String ICON_COLOR_ACTIVE = "#FFFFFF";
    private static final String ICON_COLOR_LOGOUT_HOVER = "#EF4444";
    private static final double ICON_BAR_FIXED_WIDTH = 64;
    private static final double SKILL_SIDEBAR_MIN_WIDTH = 220;
    private static final double SKILL_SIDEBAR_PREF_WIDTH = 260;
    private static final double SKILL_SIDEBAR_MAX_WIDTH = 300;
    private static final DateTimeFormatter TODO_DUE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    /**
     * Sets up SVG vector icons for all sidebar buttons.
     * Colors are set directly via inline style - NO CSS looked-up colors.
     * This prevents JavaFX CSS re-rendering from breaking icon visibility.
     */
    private void setupIconGraphics() {
        setButtonSvgIcon(iconSkills, ICON_CHAT, false);
        setButtonSvgIcon(iconMarket, ICON_BAG, false);
        setButtonSvgIcon(iconFavorites, ICON_STAR, false);
        setButtonSvgIcon(iconTodos, ICON_TODOS, false);
        setButtonSvgIcon(iconPersonal, ICON_PERSON, false);
        setButtonSvgIcon(iconSettings, ICON_GEAR, false);
        setButtonSvgIcon(iconAdmin, ICON_ADMIN, true);
        setButtonSvgIcon(iconLogout, ICON_POWER, false);
        
        // Set up hover color effects (programmatic, not CSS)
        setupIconHoverEffects();
    }
    
    /**
     * Creates an SVG icon Region and sets it as the button's graphic.
     * Color is set directly via inline style (not CSS class) for stability.
     */
    private void setButtonSvgIcon(Button button, String svgContent, boolean evenOdd) {
        if (button == null) return;
        
        SVGPath path = new SVGPath();
        path.setContent(svgContent);
        if (evenOdd) {
            path.setFillRule(FillRule.EVEN_ODD);
        }
        
        Region icon = new Region();
        icon.setShape(path);
        icon.setMinSize(20, 20);
        icon.setPrefSize(20, 20);
        icon.setMaxSize(20, 20);
        icon.setMouseTransparent(true);
        // Set color directly - no CSS class dependency
        icon.setStyle("-fx-background-color: " + ICON_COLOR_INACTIVE + ";");
        
        button.setGraphic(icon);
        button.setText("");
    }
    
    /**
     * Sets up mouse hover effects for icon buttons.
     * All color changes are done via inline style to avoid CSS rendering issues.
     */
    private void setupIconHoverEffects() {
        if (iconBar == null) return;
        for (javafx.scene.Node node : iconBar.getChildren()) {
            if (node instanceof Button btn && btn.getGraphic() instanceof Region) {
                boolean isLogout = btn == iconLogout;
                btn.setOnMouseEntered(e -> {
                    if (!btn.getStyleClass().contains("icon-bar-btn-active")) {
                        Region icon = (Region) btn.getGraphic();
                        icon.setStyle("-fx-background-color: " + 
                            (isLogout ? ICON_COLOR_LOGOUT_HOVER : ICON_COLOR_HOVER) + ";");
                    }
                });
                btn.setOnMouseExited(e -> {
                    if (!btn.getStyleClass().contains("icon-bar-btn-active")) {
                        Region icon = (Region) btn.getGraphic();
                        icon.setStyle("-fx-background-color: " + ICON_COLOR_INACTIVE + ";");
                    }
                });
            }
        }
    }
    
    private void loadSkillListToSidebar() {
        if (skillService == null || skillListContainer == null) {
            return;
        }
        
        skillListContainer.getChildren().clear();
        
        try {
            List<Skill> allSkills = skillService.fetchSkills();
            
            // Filter skills based on current view type
            List<Skill> filteredSkills = filterSkillsByViewType(allSkills, currentSkillViewType);
            
            if (filteredSkills.isEmpty()) {
                skillEmptyState.setManaged(true);
                skillEmptyState.setVisible(true);
                return;
            }
            
            skillEmptyState.setManaged(false);
            skillEmptyState.setVisible(false);
            
            // Sort by usage (most recent first)
            List<Skill> sortedSkills = new java.util.ArrayList<>(filteredSkills);
            // TODO: Add real usage-based sorting
            
            for (Skill skill : sortedSkills) {
                VBox skillItem = createSkillListItem(skill);
                skillListContainer.getChildren().add(skillItem);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load skills to sidebar", e);
        }
    }
    
    private List<Skill> filterSkillsByViewType(List<Skill> allSkills, SkillViewType viewType) {
        if (allSkills == null || allSkills.isEmpty() || skillService == null) {
            return List.of();
        }
        
        return switch (viewType) {
            case ALL -> {
                // Skills: 只显示用过的（有聊天记录的）
                try {
                    List<Skill> usedSkills = skillService.getUsedSkills(userId);
                    // Also include marketplace pinned skills (in-memory only).
                    java.util.Map<String, Skill> byId = new java.util.HashMap<>();
                    for (Skill s : allSkills) {
                        if (s != null && s.id() != null) {
                            byId.put(s.id(), s);
                        }
                    }
                    java.util.LinkedHashMap<String, Skill> merged = new java.util.LinkedHashMap<>();
                    for (String id : marketplacePinnedSkillIds) {
                        Skill s = byId.get(id);
                        if (s != null && s.enabled()) {
                            merged.put(s.id(), s);
                        }
                    }
                    for (Skill s : usedSkills) {
                        if (s != null && s.id() != null && s.enabled()) {
                            merged.put(s.id(), s);
                        }
                    }
                    yield new java.util.ArrayList<>(merged.values());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to get used skills", e);
                    yield List.of();
                }
            }
            case MARKETPLACE -> allSkills.stream()
                    .filter(skill -> skill.enabled())
                    .filter(skill -> {
                        String source = skill.source();
                        return source != null && 
                               (source.contains("external") || 
                                source.contains("importer") || 
                                source.contains("market") ||
                                source.contains("seed"));
                    })
                    .toList();
            case FAVORITES -> {
                // 我的收藏: 显示用户收藏的 skills
                try {
                    List<Skill> favorites = skillService.getFavorites(userId);
                    yield favorites.stream()
                            .filter(Skill::enabled)
                            .toList();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to get favorite skills", e);
                    yield List.of();
                }
            }
        };
    }
    
    private VBox createSkillListItem(Skill skill) {
        VBox item = new VBox(4);
        item.getStyleClass().add("skill-list-item");
        item.setPadding(new javafx.geometry.Insets(12));
        
        SkillI18nZhCn zh = SkillI18nUtil.zhCn(skill);
        String displayName = (zh != null && zh.displayName() != null) ? zh.displayName() : skill.name();
        String oneLine = (zh != null && zh.oneLine() != null) ? zh.oneLine() : skill.description();

        // Title row with favorite button
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        Label titleLabel = new Label(displayName != null ? displayName : "");
        titleLabel.getStyleClass().add("skill-list-item-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        
        // Favorite button (star icon)
        Button favoriteBtn = new Button();
        favoriteBtn.getStyleClass().add("favorite-btn");
        favoriteBtn.setMinSize(24, 24);
        favoriteBtn.setMaxSize(24, 24);
        favoriteBtn.setCursor(javafx.scene.Cursor.HAND);
        
        // Check if favorited and set icon
        boolean isFavorited = skillService != null && skillService.isFavorite(userId, skill.id());
        updateFavoriteButtonIcon(favoriteBtn, isFavorited);
        
        // Toggle favorite on click
        favoriteBtn.setOnAction(e -> {
            e.consume(); // Prevent item click
            if (skillService != null) {
                skillService.toggleFavorite(userId, skill.id());
                boolean newState = skillService.isFavorite(userId, skill.id());
                updateFavoriteButtonIcon(favoriteBtn, newState);
                // Refresh list if in favorites view
                if (currentSkillViewType == SkillViewType.FAVORITES) {
                    loadSkillListToSidebar();
                }
            }
        });
        
        titleRow.getChildren().addAll(titleLabel, favoriteBtn);
        
        // Description
        Label descLabel = new Label(oneLine != null ? oneLine : "");
        descLabel.getStyleClass().add("skill-list-item-desc");
        descLabel.setMaxWidth(Double.MAX_VALUE);
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(36);
        
        // Time (placeholder)
        Label timeLabel = new Label("最近使用");
        timeLabel.getStyleClass().add("skill-list-item-time");
        
        HBox bottomRow = new HBox(8);
        bottomRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bottomRow.getChildren().add(timeLabel);
        
        item.getChildren().addAll(titleRow, descLabel, bottomRow);
        
        // Click item (but not button) to load chat
        item.setCursor(javafx.scene.Cursor.HAND);
        item.setOnMouseClicked(e -> {
            if (e.getTarget() != favoriteBtn && !isDescendant(favoriteBtn, e.getTarget())) {
                onSkillItemClicked(skill, item);
            }
        });
        
        return item;
    }
    
    private void updateFavoriteButtonIcon(Button btn, boolean isFavorited) {
        if (btn == null) return;
        String starPath = isFavorited ? ICON_STAR_FILLED : ICON_STAR;
        SVGPath svg = new SVGPath();
        svg.setContent(starPath);
        svg.setFillRule(FillRule.EVEN_ODD);
        Region icon = new Region();
        icon.setShape(svg);
        icon.setPrefSize(16, 16);
        icon.setMinSize(16, 16);
        icon.setMaxSize(16, 16);
        icon.setStyle("-fx-background-color: " + (isFavorited ? "#FFD700" : "#999999") + ";");
        btn.setGraphic(icon);
    }
    
    private boolean isDescendant(javafx.scene.Node parent, Object target) {
        if (!(target instanceof javafx.scene.Node)) return false;
        javafx.scene.Node node = (javafx.scene.Node) target;
        while (node != null) {
            if (node == parent) return true;
            node = node.getParent();
        }
        return false;
    }
    
    private void onSkillItemClicked(Skill skill, VBox skillItem) {
        // Remove active state from all items
        skillListContainer.getChildren().forEach(node -> {
            node.getStyleClass().remove("skill-list-item-active");
        });
        
        // Add active state to clicked item
        skillItem.getStyleClass().add("skill-list-item-active");

        // In marketplace list, open preview-only page first.
        if (currentSkillViewType == SkillViewType.MARKETPLACE) {
            loadMarketSkillPreviewPage(skill);
            return;
        }
        
        // Load chat for this skill
        if (onSkillClick != null) {
            onSkillClick.onSkillClicked(skill);
        }
    }
    
    private void filterSkillList(String keyword) {
        if (skillListContainer == null) {
            return;
        }
        
        String lowerKeyword = keyword != null ? keyword.toLowerCase().trim() : "";
        
        skillListContainer.getChildren().forEach(node -> {
            if (node instanceof VBox) {
                VBox item = (VBox) node;
                // Get the title label
                if (!item.getChildren().isEmpty() && item.getChildren().get(0) instanceof Label) {
                    Label titleLabel = (Label) item.getChildren().get(0);
                    String title = titleLabel.getText().toLowerCase();
                    
                    boolean matches = lowerKeyword.isEmpty() || title.contains(lowerKeyword);
                    item.setManaged(matches);
                    item.setVisible(matches);
                }
            }
        });
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        setupWindowControls();
    }

    /**
     * Sets the SkillService dependency for loading the Skill list page.
     */
    public void setSkillService(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * Sets the ChatService dependency for loading the chat page.
     */
    public void setChatService(ChatService chatService) {
        this.chatService = chatService;
    }


    /**
     * Sets the FeedbackService dependency for the feedback page.
     */
    public void setFeedbackService(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Sets the AdminService dependency for the admin page.
     */
    public void setAdminService(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Sets the KnowledgeBaseService dependency for the skill wizard.
     */
    public void setKnowledgeBaseService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public void setPersonalSkillService(PersonalSkillService personalSkillService) {
        this.personalSkillService = personalSkillService;
    }

    public void setWorkFollowupService(WorkFollowupService workFollowupService) {
        this.workFollowupService = workFollowupService;
        startTodoReminderPolling();
    }

    public void setHrCaseService(HrCaseService hrCaseService) {
        this.hrCaseService = hrCaseService;
    }

    /**
     * Sets the SkillKnowledgeBindingRepository dependency for the knowledge page.
     */
    public void setBindingRepository(SkillKnowledgeBindingRepository bindingRepository) {
        this.bindingRepository = bindingRepository;
    }

    /**
     * Sets the SettingsRepository dependency for the settings page.
     */
    public void setSettingsRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /**
     * Sets the current user ID for favorites and usage tracking.
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Sets the current username for feedback submission.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    /**
     * Sets the user created time for settings display.
     */
    public void setUserCreatedAt(long createdAt) {
        this.userCreatedAt = createdAt;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
        
        // Legacy layout
        if (navAdmin != null) {
            navAdmin.setManaged(isAdmin);
            navAdmin.setVisible(isAdmin);
        }
        
        // WeChat layout
        if (iconAdmin != null) {
            iconAdmin.setManaged(isAdmin);
            iconAdmin.setVisible(isAdmin);
        }
        
        // If user lost admin privilege and is currently on admin page, go back to skills
        if (!isAdmin) {
            if (activeNavButton == navAdmin || (iconAdmin != null && iconAdmin.getStyleClass().contains("icon-bar-btn-active"))) {
                onNavSkills();
            }
        }
    }

    /**
     * Sets the callback for when a Skill card is clicked (navigate to chat).
     */
    public void setOnSkillClick(SkillController.SkillClickCallback callback) {
        this.onSkillClick = callback;
    }

    /**
     * Sets the callback for when the user logs out.
     */
    public void setOnLogout(Runnable logoutCallback) {
        this.logoutCallback = logoutCallback;
    }

    // ---- Navigation handlers ----

    @FXML
    public void onNavSkills() {
        if (navSkills != null) {
            // Legacy layout
            setActiveNav(navSkills);
            showPage("skills");
        } else if (iconSkills != null) {
            // WeChat layout
            onIconSkills();
        }
    }

    @FXML
    public void onNavMarket() {
        setActiveNav(navMarket);
        showPage("market");
    }

    @FXML
    public void onNavHistory() {
        setActiveNav(navHistory);
        showPage("history");
    }

    @FXML
    public void onNavFavorites() {
        setActiveNav(navFavorites);
        showPage("favorites");
    }

    @FXML
    public void onNavPersonalSkills() {
        setActiveNav(navPersonalSkills);
        showPage("personal-skill");
    }

    @FXML
    public void onNavFeedback() {
        setActiveNav(navFeedback);
        showPage("feedback");
    }

    @FXML
    public void onNavSettings() {
        setActiveNav(navSettings);
        showPage("settings");
    }

    @FXML
    public void onNavAdmin() {
        if (!isAdmin) {
            DialogHelper.showInformation("", "当前账号暂无管理员权限");
            return;
        }
        setActiveNav(navAdmin);
        showPage("admin");
    }

    @FXML
    public void onNavLogout() {
        DialogHelper.showConfirmation("", "确定要退出当前账号吗？")
            .ifPresent(response -> {
                if (response == ButtonType.OK && logoutCallback != null) {
                    logoutCallback.run();
                }
            });
    }
    
    // ---- WeChat-style icon bar handlers ----
    
    @FXML
    public void onIconSkills() {
        normalizeIconBarStructure();
        ensureIconButtonsVisible();
        setSkillSidebarVisible(true);
        currentSkillViewType = SkillViewType.ALL;
        if (skillSidebarTitle != null) {
            skillSidebarTitle.setText("Skills");
            loadSkillListToSidebar();
        }
        setActiveIconButton(iconSkills);
        syncChatInputLockByViewType();
    }
    
    @FXML
    public void onIconMarket() {
        normalizeIconBarStructure();
        ensureIconButtonsVisible();
        setSkillSidebarVisible(true);
        currentSkillViewType = SkillViewType.MARKETPLACE;
        if (skillSidebarTitle != null) {
            skillSidebarTitle.setText("技能市场");
            loadSkillListToSidebar();
        }
        setActiveIconButton(iconMarket);
        syncChatInputLockByViewType();
    }
    
    @FXML
    public void onIconFavorites() {
        normalizeIconBarStructure();
        ensureIconButtonsVisible();
        setSkillSidebarVisible(true);
        currentSkillViewType = SkillViewType.FAVORITES;
        if (skillSidebarTitle != null) {
            skillSidebarTitle.setText("我的收藏");
            loadSkillListToSidebar();
        }
        setActiveIconButton(iconFavorites);
        syncChatInputLockByViewType();
    }

    @FXML
    public void onIconTodos() {
        resetIconBarForPageSwitch();
        setSkillSidebarVisible(false);
        loadTodoPanelPage();
        setupIconGraphics();
        setActiveIconButton(iconTodos);
        Platform.runLater(() -> {
            setupIconGraphics();
            stabilizeIconBar();
        });
    }
    
    @FXML
    public void onIconPersonal() {
        normalizeIconBarStructure();
        ensureIconButtonsVisible();
        setSkillSidebarVisible(true);
        if (skillSidebarTitle != null) {
            skillSidebarTitle.setText("个人 Skill");
            skillListContainer.getChildren().clear();
        }
        setActiveIconButton(iconPersonal);
    }
    
    @FXML
    public void onIconSettings() {
        resetIconBarForPageSwitch();
        setSkillSidebarVisible(false);
        loadSettingsPage();
        setupIconGraphics();
        setActiveIconButton(iconSettings);
        Platform.runLater(() -> {
            setupIconGraphics();
            stabilizeIconBar();
        });
    }
    
    @FXML
    public void onIconAdmin() {
        resetIconBarForPageSwitch();
        if (!isAdmin) {
            DialogHelper.showInformation("", "当前账号暂无管理员权限");
            return;
        }
        setSkillSidebarVisible(false);
        loadAdminPage();
        setupIconGraphics();
        setActiveIconButton(iconAdmin);
        Platform.runLater(() -> {
            setupIconGraphics();
            stabilizeIconBar();
        });
    }
    
    /**
     * Shows or hides the skill sidebar (middle column).
     * Uses managed/visible to remove it from layout when hidden.
     */
    private void setSkillSidebarVisible(boolean visible) {
        if (skillSidebar == null) return;
        // Keep managed=true to avoid abrupt HBox relayout causing icon bar drift.
        skillSidebar.setManaged(true);
        if (visible) {
            skillSidebar.setVisible(true);
            skillSidebar.setMouseTransparent(false);
            skillSidebar.setMinWidth(SKILL_SIDEBAR_MIN_WIDTH);
            skillSidebar.setPrefWidth(SKILL_SIDEBAR_PREF_WIDTH);
            skillSidebar.setMaxWidth(SKILL_SIDEBAR_MAX_WIDTH);
        } else {
            skillSidebar.setVisible(false);
            skillSidebar.setMouseTransparent(true);
            skillSidebar.setMinWidth(0);
            skillSidebar.setPrefWidth(0);
            skillSidebar.setMaxWidth(0);
        }
        stabilizeIconBar();
    }
    
    @FXML
    public void onIconLogout() {
        DialogHelper.showConfirmation("", "确定要退出当前账号吗？")
            .ifPresent(response -> {
                if (response == ButtonType.OK && logoutCallback != null) {
                    logoutCallback.run();
                }
            });
    }

    @FXML
    public void onRecommendSearchInfo() {
        openRecommendedAction(
                RecommendActionType.SEARCH_INFO,
                "帮我查一下最近一周 Java Stream 常见坑，并给出可验证来源与下一步建议。"
        );
    }

    @FXML
    public void onRecommendReadFiles() {
        openRecommendedAction(
                RecommendActionType.READ_FILES,
                "我会上传文件，请帮我提取关键字段、识别异常，并给我一份可执行的下一步清单。"
        );
    }

    @FXML
    public void onRecommendCreateTodo() {
        openRecommendedAction(
                RecommendActionType.CREATE_TODO,
                "帮我把今天要推进的 3 件事拆成待办，按优先级排序并给出默认截止时间。"
        );
    }

    @FXML
    public void onRecommendSetReminder() {
        openRecommendedAction(
                RecommendActionType.SET_REMINDER,
                "提醒我明天上午 10 点跟进本周未完成事项，并给我一段复盘模板。"
        );
    }

    @FXML
    public void onRecommendHrExec() {
        openRecommendedAction(
                RecommendActionType.HR_EXEC,
                "请作为 HR 执行助手，帮我推进这个候选人流程，并输出“我将帮你做什么/请确认/结果”。"
        );
    }

    @FXML
    public void onTodoReminderLater() {
        if (workFollowupService == null || activeReminderTodo == null || activeReminderTodo.id() == null) {
            return;
        }
        workFollowupService.postponeTodo(activeReminderTodo.id(), 2);
        refreshTodoReminder();
    }

    @FXML
    public void onTodoReminderDone() {
        if (workFollowupService == null || activeReminderTodo == null || activeReminderTodo.id() == null) {
            return;
        }
        workFollowupService.completeTodo(activeReminderTodo.id(), "");
        refreshTodoReminder();
    }
    
    private void setActiveIconButton(Button button) {
        if (iconBar == null) {
            return;
        }
        
        // Reset all buttons to inactive state
        iconBar.getChildren().forEach(node -> {
            if (node instanceof Button btn) {
                btn.getStyleClass().remove("icon-bar-btn-active");
                // Reset icon color to inactive (direct inline style)
                if (btn.getGraphic() instanceof Region icon) {
                    icon.setStyle("-fx-background-color: " + ICON_COLOR_INACTIVE + ";");
                }
            }
        });
        
        // Set the active button
        if (button != null) {
            button.getStyleClass().add("icon-bar-btn-active");
            // Set icon color to active white (direct inline style)
            if (button.getGraphic() instanceof Region icon) {
                icon.setStyle("-fx-background-color: " + ICON_COLOR_ACTIVE + ";");
            }
        }
        stabilizeIconBar();
    }

    /**
     * Re-assert icon bar geometry and recover missing graphics after layout/theme refreshes.
     */
    private void stabilizeIconBar() {
        if (iconBar == null) {
            return;
        }
        normalizeIconBarStructure();
        iconBar.setManaged(true);
        iconBar.setVisible(true);
        HBox.setHgrow(iconBar, Priority.NEVER);
        iconBar.setMinWidth(ICON_BAR_FIXED_WIDTH);
        iconBar.setPrefWidth(ICON_BAR_FIXED_WIDTH);
        iconBar.setMaxWidth(ICON_BAR_FIXED_WIDTH);

        boolean needsRebuild = false;
        for (javafx.scene.Node node : iconBar.getChildren()) {
            if (node instanceof Button btn && btn.getGraphic() == null) {
                needsRebuild = true;
                break;
            }
        }
        if (needsRebuild) {
            setupIconGraphics();
        }
    }

    /**
     * Keeps first-column icon bar children/order stable to prevent collapse after page switches.
     */
    private void normalizeIconBarStructure() {
        if (iconBar == null) {
            return;
        }
        if (iconBarSpacer == null) {
            for (Node n : iconBar.getChildren()) {
                if (n instanceof Region r && VBox.getVgrow(r) == Priority.ALWAYS) {
                    iconBarSpacer = r;
                    break;
                }
            }
            if (iconBarSpacer == null) {
                iconBarSpacer = new Region();
                VBox.setVgrow(iconBarSpacer, Priority.ALWAYS);
            }
        }

        List<Node> ordered = new java.util.ArrayList<>();
        if (windowBar != null) ordered.add(windowBar);
        if (iconSkills != null) ordered.add(iconSkills);
        if (iconMarket != null) ordered.add(iconMarket);
        if (iconFavorites != null) ordered.add(iconFavorites);
        if (iconTodos != null) ordered.add(iconTodos);
        if (iconPersonal != null) ordered.add(iconPersonal);
        ordered.add(iconBarSpacer);
        if (iconSettings != null) ordered.add(iconSettings);
        if (iconAdmin != null) ordered.add(iconAdmin);
        if (iconLogout != null) ordered.add(iconLogout);

        iconBar.getChildren().setAll(ordered);
        iconBar.requestLayout();
    }

    private void ensureIconButtonsVisible() {
        if (iconSkills != null) {
            iconSkills.setManaged(true);
            iconSkills.setVisible(true);
        }
        if (iconMarket != null) {
            iconMarket.setManaged(true);
            iconMarket.setVisible(true);
        }
        if (iconFavorites != null) {
            iconFavorites.setManaged(true);
            iconFavorites.setVisible(true);
        }
        if (iconTodos != null) {
            iconTodos.setManaged(true);
            iconTodos.setVisible(true);
        }
        if (iconPersonal != null) {
            iconPersonal.setManaged(true);
            iconPersonal.setVisible(true);
        }
        if (iconSettings != null) {
            iconSettings.setManaged(true);
            iconSettings.setVisible(true);
        }
        if (iconLogout != null) {
            iconLogout.setManaged(true);
            iconLogout.setVisible(true);
        }
        if (iconAdmin != null) {
            iconAdmin.setManaged(isAdmin);
            iconAdmin.setVisible(isAdmin);
        }
        stabilizeIconBar();
    }

    private void resetIconBarForPageSwitch() {
        normalizeIconBarStructure();
        ensureIconButtonsVisible();
        setupIconGraphics();
        stabilizeIconBar();
    }

    // ---- Theme toggling ----

    /**
     * Toggles between light and dark themes.
     * Call this from the settings page or any toggle control.
     */
    public void toggleTheme() {
        Scene scene = contentArea.getScene();
        if (scene == null) {
            return;
        }

        darkTheme = !darkTheme;
        applyTheme(scene);
    }

    /**
     * Sets the theme explicitly.
     *
     * @param dark true for dark theme, false for light theme
     */
    public void setTheme(boolean dark) {
        this.darkTheme = dark;
        Scene scene = contentArea.getScene();
        if (scene != null) {
            applyTheme(scene);
        }
    }

    /**
     * Returns whether the dark theme is currently active.
     */
    public boolean isDarkTheme() {
        return darkTheme;
    }

    /**
     * Returns the content area StackPane for loading child pages.
     */
    public StackPane getContentArea() {
        return contentArea;
    }

    /**
     * Returns the currently active navigation page name.
     */
    public String getActivePageName() {
        if (activeNavButton == navSkills) return "skills";
        if (activeNavButton == navMarket) return "market";
        if (activeNavButton == navHistory) return "history";
        if (activeNavButton == navFavorites) return "favorites";
        if (activeNavButton == navPersonalSkills) return "personal-skill";
        if (activeNavButton == navFeedback) return "feedback";
        if (activeNavButton == navSettings) return "settings";
        if (activeNavButton == navAdmin) return "admin";
        return "skills";
    }

    // ---- Internal helpers ----

    private void setActiveNav(Button button) {
        if (button == null) {
            return; // New layout may not have legacy nav buttons
        }
        
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove(NAV_ITEM_ACTIVE);
        }
        activeNavButton = button;
        if (!button.getStyleClass().contains(NAV_ITEM_ACTIVE)) {
            button.getStyleClass().add(NAV_ITEM_ACTIVE);
        }
    }

    private void showPage(String pageName) {
        // Ensure theme stylesheet is applied (prevents style loss)
        Scene scene = contentArea.getScene();
        if (scene != null) {
            String lightCss = getClass().getResource(LIGHT_THEME).toExternalForm();
            String darkCss = getClass().getResource(DARK_THEME).toExternalForm();
            if (!scene.getStylesheets().contains(lightCss) && !scene.getStylesheets().contains(darkCss)) {
                applyTheme(scene);
            }
        }
        contentArea.getChildren().clear();

        if ("skills".equals(pageName) || "favorites".equals(pageName)) {
            loadSkillListPage("favorites".equals(pageName));
        } else if ("market".equals(pageName)) {
            loadMarketPage();
        } else if ("history".equals(pageName)) {
            loadHistoryPage();
        } else if ("personal-skill".equals(pageName)) {
            loadPersonalSkillPage();
        } else if ("feedback".equals(pageName)) {
            loadFeedbackPage();
        } else if ("settings".equals(pageName)) {
            loadSettingsPage();
        } else if ("admin".equals(pageName)) {
            loadAdminPage();
        }
    }

    /**
     * Loads the chat page for the given Skill into the content area.
     * Called when a user clicks on a Skill card.
     *
     * @param skill the Skill to start a chat with
     */
    public void loadChatPage(Skill skill) {
        if (contentArea == null) {
            LOGGER.warning("Content area is null, cannot load chat page");
            return;
        }

        // Leaving previous chat: remove marketplace pin if it was never used.
        cleanupMarketplacePinnedSkillOnLeave(skill);

        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Node chatPage = loader.load();
            ChatController controller = loader.getController();
            controller.setChatService(chatService);
            controller.setWorkFollowupService(workFollowupService);
            controller.setCurrentUserId(userId);
            controller.setSkill(skill);
            controller.setOnHistoryClearedCallback(() -> {
                // If user cleared history, this skill should disappear from "used" list,
                // and also from marketplace temporary pins.
                if (skill != null && skill.id() != null) {
                    marketplacePinnedSkillIds.remove(skill.id());
                }
                loadSkillListToSidebar();
            });
            currentChatController = controller;
            
            // Try to load recent chat history for this skill
            if (chatService != null && userId != null) {
                String recentSessionId = chatService.getRecentSessionForSkill(skill.id(), userId);
                if (recentSessionId != null) {
                    controller.loadHistory(recentSessionId);
                }
            }
            
            // Back callback: for WeChat layout, just clear content area; for legacy, go to skills page
            controller.setOnBackCallback(() -> {
                cleanupMarketplacePinnedSkillOnLeave(null);
                if (skillSidebar != null) {
                    // WeChat layout: just show welcome screen or do nothing
                    contentArea.getChildren().clear();
                } else {
                    // Legacy layout
                    onNavSkills();
                }
            });
            
            setContent(chatPage);
            syncChatInputLockByViewType();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load chat page", e);
        }
    }

    private void loadMarketSkillPreviewPage(Skill skill) {
        if (contentArea == null || skill == null) {
            return;
        }
        cleanupMarketplacePinnedSkillOnLeave(skill);
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Node chatPage = loader.load();
            ChatController controller = loader.getController();
            controller.setChatService(chatService);
            controller.setWorkFollowupService(workFollowupService);
            controller.setCurrentUserId(userId);
            controller.setSkill(skill);
            controller.setMarketplacePreviewMode(true);
            controller.setInputLocked(true, "请点击右下角按钮进入聊天");
            controller.setOnUseSkillCallback(() -> openSkillFromMarketplacePreview(skill));
            currentChatController = controller;
            setContent(chatPage);
            syncChatInputLockByViewType();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load market preview page", e);
        }
    }

    /**
     * Loads the chat page for an existing history session.
     * Creates a minimal Skill object from the session data and loads the conversation.
     *
     * @param session the chat session to load
     */
    public void loadChatFromHistory(ChatSession session) {
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Node chatPage = loader.load();
            ChatController controller = loader.getController();
            controller.setChatService(chatService);
            controller.setWorkFollowupService(workFollowupService);
            controller.setCurrentUserId(userId);

            // Create a minimal Skill from session data for display purposes
            Skill skill = new Skill(
                    session.skillId(), session.skillName(), "", "", "",
                    true, null, List.of(),
                    null, "history", "zh", "basic",
                    SkillType.GENERAL, SkillKind.PROMPT_ONLY, null, SkillExecutionMode.SINGLE);
            controller.setSkill(skill);
            controller.setOnBackCallback(this::onNavHistory);
            controller.setOnHistoryClearedCallback(this::loadSkillListToSidebar);
            currentChatController = controller;
            controller.loadHistory(session.id());

            setContent(chatPage);
            syncChatInputLockByViewType();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load chat page from history", e);
        }
    }

    /**
     * Loads the history page into the content area.
     */
    private void loadHistoryPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            Node historyPage = loader.load();
            HistoryController controller = loader.getController();
            controller.setChatService(chatService);
            controller.setUserId(userId);
            controller.setOnSessionClick(this::loadChatFromHistory);
            setContent(historyPage);
            controller.loadSessions();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load history page", e);
        }
    }

    /**
     * Loads the Skill list page into the content area.
     */
    private void loadSkillListPage(boolean favoritesOnly) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/skill-list.fxml"));
            Node skillListPage = loader.load();
            SkillController controller = loader.getController();
            controller.setSkillService(skillService);
            controller.setUserId(userId);
            controller.setFavoritesOnly(favoritesOnly);
            controller.setOnSkillClick(onSkillClick);
            setContent(skillListPage);
            controller.loadSkills();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load skill list page", e);
        }
    }

    private void loadPersonalSkillPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/personal-skill.fxml"));
            Node page = loader.load();
            PersonalSkillController controller = loader.getController();
            controller.setPersonalSkillService(personalSkillService);
            controller.setUserId(userId);
            setContent(page);
            controller.loadSkills();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load personal skill page", e);
        }
    }

    private void loadTodoPanelPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/todo-panel.fxml"));
            Node page = loader.load();
            TodoPanelController controller = loader.getController();
            controller.setWorkFollowupService(workFollowupService);
            controller.setUserEmail(userEmail);
            controller.setOnOpenCliSchedule(this::loadCliSchedulePage);
            controller.setOnOpenHrWorkbench(this::loadHrWorkbenchPage);
            setContent(page);
            controller.loadTodos();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load todo panel page", e);
        }
    }

    private void loadHrWorkbenchPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/hr-workbench.fxml"));
            Node page = loader.load();
            HrWorkbenchController controller = loader.getController();
            controller.setWorkFollowupService(workFollowupService);
            controller.setHrCaseService(hrCaseService);
            controller.setOnBack(this::loadTodoPanelPage);
            controller.setOnSendToChat(prompt -> openRecommendedAction(RecommendActionType.HR_EXEC, prompt));
            setContent(page);
            controller.loadCases();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load HR workbench page", e);
        }
    }

    private void loadCliSchedulePage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/cli-schedule-panel.fxml"));
            Node page = loader.load();
            CliScheduleController controller = loader.getController();
            controller.setOnBackCallback(this::loadTodoPanelPage);
            setContent(page);
            controller.loadJobs();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load CLI schedule page", e);
        }
    }

    private void startTodoReminderPolling() {
        if (todoReminderTimeline != null) {
            todoReminderTimeline.stop();
        }
        if (workFollowupService == null) {
            hideTodoReminder();
            return;
        }
        todoReminderTimeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> refreshTodoReminder()),
                new KeyFrame(Duration.minutes(30), e -> refreshTodoReminder())
        );
        todoReminderTimeline.setCycleCount(Timeline.INDEFINITE);
        todoReminderTimeline.play();
        refreshTodoReminder();
    }

    private void refreshTodoReminder() {
        if (workFollowupService == null || todoReminderBubble == null || todoReminderLabel == null) {
            return;
        }
        List<WorkTodo> todos = workFollowupService.listTodos("todo", "due");
        if (todos == null || todos.isEmpty()) {
            hideTodoReminder();
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime within = now.plusHours(24);
        WorkTodo nearest = null;
        LocalDateTime nearestDue = null;
        for (WorkTodo todo : todos) {
            if (todo == null || todo.id() == null || todo.dueAt() == null || todo.dueAt().isBlank()) {
                continue;
            }
            LocalDateTime due = parseTodoDueAt(todo.dueAt());
            if (due == null) {
                continue;
            }
            if (due.isBefore(now) || due.isAfter(within)) {
                continue;
            }
            if (nearestDue == null || due.isBefore(nearestDue)) {
                nearest = todo;
                nearestDue = due;
            }
        }
        if (nearest == null || nearestDue == null) {
            hideTodoReminder();
            return;
        }
        activeReminderTodo = nearest;
        String title = nearest.title() == null || nearest.title().isBlank() ? "未命名待办" : nearest.title();
        todoReminderLabel.setText("待办即将到期："
                + title
                + "\n截止时间："
                + nearestDue.format(TODO_DUE_FORMATTER));
        todoReminderBubble.setManaged(true);
        todoReminderBubble.setVisible(true);
    }

    private LocalDateTime parseTodoDueAt(String dueAt) {
        if (dueAt == null || dueAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dueAt.trim(), TODO_DUE_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void hideTodoReminder() {
        activeReminderTodo = null;
        if (todoReminderBubble != null) {
            todoReminderBubble.setManaged(false);
            todoReminderBubble.setVisible(false);
        }
        if (todoReminderLabel != null) {
            todoReminderLabel.setText("");
        }
    }

    private void loadMarketPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/market-skill.fxml"));
            Node page = loader.load();
            MarketSkillController controller = loader.getController();
            controller.setSkillService(skillService);
            controller.setPersonalSkillService(personalSkillService);
            controller.setUserId(userId);
            controller.setOnSkillClick(this::openChatFromMarket);
            setContent(page);
            controller.loadSkills();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load market page", e);
        }
    }

    private void openRecommendedAction(RecommendActionType actionType, String prefill) {
        Skill target = resolveRecommendedSkill(actionType);
        if (target == null) {
            DialogHelper.showInformation("", "暂未找到可用技能，请先到技能市场添加对应技能。");
            return;
        }
        if (iconBar != null && iconSkills != null) {
            setActiveIconButton(iconSkills);
        } else if (navSkills != null) {
            setActiveNav(navSkills);
        }
        currentSkillViewType = SkillViewType.ALL;
        if (skillSidebarTitle != null) {
            skillSidebarTitle.setText("Skills");
        }
        loadSkillListToSidebar();
        loadChatPage(target);
        if (currentChatController != null && prefill != null && !prefill.isBlank()) {
            currentChatController.prefillMessage(prefill);
        }
    }

    private Skill resolveRecommendedSkill(RecommendActionType actionType) {
        if (skillService == null) {
            return null;
        }
        List<Skill> skills;
        try {
            skills = skillService.fetchSkills();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch skills for recommended action", e);
            return null;
        }
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        List<Skill> enabledSkills = skills.stream()
                .filter(s -> s != null && s.enabled())
                .toList();
        if (enabledSkills.isEmpty()) {
            return null;
        }
        List<String> keywords = switch (actionType) {
            case SEARCH_INFO -> List.of("信息检索", "检索", "research", "搜索", "资料", "web");
            case READ_FILES -> List.of("文件", "表格", "截图", "解析", "分析", "doc", "excel");
            case CREATE_TODO, SET_REMINDER -> List.of("跟进", "待办", "提醒", "todo", "followup", "秘书");
            case HR_EXEC -> List.of("hr", "招聘", "候选人", "面试", "offer", "入职", "人事", "跟进");
        };
        Skill matched = findSkillByKeywords(enabledSkills, keywords);
        return matched != null ? matched : enabledSkills.get(0);
    }

    private Skill findSkillByKeywords(List<Skill> skills, List<String> keywords) {
        if (skills == null || skills.isEmpty() || keywords == null || keywords.isEmpty()) {
            return null;
        }
        for (Skill skill : skills) {
            String text = collectSkillSearchText(skill);
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase())) {
                    return skill;
                }
            }
        }
        return null;
    }

    private String collectSkillSearchText(Skill skill) {
        if (skill == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (skill.name() != null) {
            sb.append(skill.name()).append(' ');
        }
        if (skill.description() != null) {
            sb.append(skill.description()).append(' ');
        }
        SkillI18nZhCn zh = SkillI18nUtil.zhCn(skill);
        if (zh != null) {
            if (zh.displayName() != null) {
                sb.append(zh.displayName()).append(' ');
            }
            if (zh.oneLine() != null) {
                sb.append(zh.oneLine()).append(' ');
            }
        }
        return sb.toString().toLowerCase();
    }

    private void openChatFromMarket(Skill skill) {
        if (skill == null) {
            return;
        }
        // Marketplace should show preview-only; enter chat via explicit button.
        loadMarketSkillPreviewPage(skill);
    }

    private void openSkillFromMarketplacePreview(Skill skill) {
        if (skill == null) {
            return;
        }
        if (skill.id() != null && !skill.id().isBlank()) {
            marketplacePinnedSkillIds.add(skill.id());
        }
        if (iconBar != null && iconSkills != null) {
            setActiveIconButton(iconSkills);
        } else if (navSkills != null) {
            setActiveNav(navSkills);
        }
        currentSkillViewType = SkillViewType.ALL;
        if (skillSidebarTitle != null) {
            skillSidebarTitle.setText("Skills");
        }
        loadSkillListToSidebar();
        loadChatPage(skill);
    }

    private void cleanupMarketplacePinnedSkillOnLeave(Skill nextSkill) {
        if (currentChatController == null) {
            return;
        }
        Skill current = currentChatController.getCurrentSkill();
        if (current == null || current.id() == null) {
            return;
        }
        // If we are switching from preview -> chat for the SAME skill, do not cleanup.
        if (nextSkill != null && nextSkill.id() != null && nextSkill.id().equals(current.id())) {
            return;
        }
        String id = current.id();
        if (!marketplacePinnedSkillIds.contains(id)) {
            return;
        }
        // Remove the temporary pin when leaving this skill (whether used or not).
        marketplacePinnedSkillIds.remove(id);
    }

    private void syncChatInputLockByViewType() {
        if (currentChatController == null) {
            return;
        }
        boolean shouldLock = currentSkillViewType == SkillViewType.MARKETPLACE;
        currentChatController.setMarketplacePreviewMode(shouldLock);
        currentChatController.setInputLocked(
                shouldLock,
                shouldLock ? "技能市场模式下输入已禁用，请先切换到 Skills" : null
        );
    }

    /**
     * Loads the feedback page into the content area.
     */
    private void loadFeedbackPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/feedback.fxml"));
            Node feedbackPage = loader.load();
            FeedbackController controller = loader.getController();
            controller.setFeedbackService(feedbackService);
            controller.setUsername(username);
            controller.setUserId(userId);
            setContent(feedbackPage);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load feedback page", e);
        }
    }

    /**
     * Loads the settings page into the content area.
     */
    private void loadSettingsPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            Node settingsPage = loader.load();
            SettingsController controller = loader.getController();
            controller.setSettingsRepository(settingsRepository);
            controller.setDarkTheme(darkTheme);
            controller.setUserId(userId);  // Pass user ID
            controller.setUsername(username);  // Pass username
            controller.setCreatedAt(userCreatedAt);
            controller.setOnThemeToggle(this::setTheme);
            controller.setOnLogout(logoutCallback);
            setContent(settingsPage);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load settings page", e);
        }
    }

    /**
     * Loads the admin management page into the content area.
     */
    private void loadAdminPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin.fxml"));
            Node adminPage = loader.load();
            AdminController controller = loader.getController();
            controller.setAdminService(adminService);
            controller.setPersonalSkillService(personalSkillService);
            controller.setOnCreateSkillCallback(this::loadSkillWizardPage);
            controller.setOnManageKnowledgeCallback(this::loadKnowledgePage);
            controller.setOnUseSkillCallback(this::openSkillFromAdminById);
            setContent(adminPage);
            controller.loadData();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load admin page", e);
        }
    }

    /**
     * Loads the Skill creation wizard page into the content area.
     */
    private void loadSkillWizardPage() {
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/skill-wizard.fxml"));
            Node wizardPage = loader.load();
            SkillWizardController controller = loader.getController();
            controller.setAdminService(adminService);
            controller.setKnowledgeBaseService(knowledgeBaseService);
            controller.setOnBackCallback(this::onNavAdmin);
            controller.setOnSkillCreatedCallback(this::openSkillAfterCreated);
            setContent(wizardPage);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load skill wizard page", e);
        }
    }

    private void openSkillFromAdminById(String skillId) {
        openSkillByIdAndEnterChat(skillId);
    }

    private void openSkillAfterCreated(String skillId) {
        // After creation, directly enter Skills and open chat.
        openSkillByIdAndEnterChat(skillId);
    }

    private void openSkillByIdAndEnterChat(String skillId) {
        if (skillId == null || skillId.isBlank() || skillService == null) {
            return;
        }
        Skill target = null;
        try {
            List<Skill> all = skillService.fetchSkills();
            if (all != null) {
                for (Skill s : all) {
                    if (s != null && skillId.equals(s.id())) {
                        target = s;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch skills for opening chat", e);
        }

        // Pin into Skills list for this session (so it is visible immediately).
        marketplacePinnedSkillIds.add(skillId);

        if (iconBar != null && iconSkills != null) {
            setActiveIconButton(iconSkills);
        } else if (navSkills != null) {
            setActiveNav(navSkills);
        }
        currentSkillViewType = SkillViewType.ALL;
        if (skillSidebarTitle != null) {
            skillSidebarTitle.setText("Skills");
        }
        loadSkillListToSidebar();

        if (target != null) {
            loadChatPage(target);
        }
    }

    /**
     * Loads the knowledge base management page into the content area.
     */
    private void loadKnowledgePage() {
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/knowledge.fxml"));
            Node knowledgePage = loader.load();
            KnowledgeController controller = loader.getController();
            controller.setKnowledgeBaseService(knowledgeBaseService);
            controller.setBindingRepository(bindingRepository);
            controller.setAdminService(adminService);
            controller.setOnBackCallback(this::onNavAdmin);
            setContent(knowledgePage);
            controller.loadData();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load knowledge page", e);
        }
    }

    private void setContent(Node page) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(page);
        configureScrollPanes(page);
    }

    private void configureScrollPanes(Node node) {
        if (node instanceof ScrollPane scrollPane) {
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setPannable(true);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                configureScrollPanes(child);
            }
        }
    }

    private void applyTheme(Scene scene) {
        String lightCss = getClass().getResource(LIGHT_THEME).toExternalForm();
        String darkCss = getClass().getResource(DARK_THEME).toExternalForm();
        String themeCss = darkTheme ? darkCss : lightCss;
        String otherCss = darkTheme ? lightCss : darkCss;

        // 如果正确的主题已经应用，且不需要的主题不在，就不要动 stylesheet
        // 避免 remove+add 同一个 stylesheet 触发全局 CSS 重渲染导致 SVG 图标丢失
        if (scene.getStylesheets().contains(themeCss) && !scene.getStylesheets().contains(otherCss)) {
            return;
        }

        scene.getStylesheets().remove(lightCss);
        scene.getStylesheets().remove(darkCss);

        scene.getStylesheets().add(themeCss);
    }

    /**
     * Returns all navigation buttons for testing/inspection.
     */
    List<Button> getNavButtons() {
        return Arrays.asList(navSkills, navMarket, navHistory, navFavorites, navPersonalSkills, navFeedback, navSettings, navAdmin);
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

    /**
     * Sets up macOS-style hover symbols on the window control buttons.
     * Symbols (×, −, expand) appear on ALL buttons when the group is hovered.
     */
    private void setupWindowButtonSymbols() {
        if (windowBtnGroup == null || windowCloseBtn == null || windowMinBtn == null || windowMaxBtn == null) {
            return;
        }
        
        // Close symbol: × (two crossing lines)
        SVGPath closeSymbol = new SVGPath();
        closeSymbol.setContent("M3.5 3.5 L8.5 8.5 M8.5 3.5 L3.5 8.5");
        closeSymbol.setStroke(Color.web("#4D0000", 0.85));
        closeSymbol.setStrokeWidth(1.2);
        closeSymbol.setStrokeLineCap(StrokeLineCap.ROUND);
        closeSymbol.setFill(Color.TRANSPARENT);
        closeSymbol.setMouseTransparent(true);
        closeSymbol.setVisible(false);
        windowCloseBtn.setGraphic(closeSymbol);
        
        // Minimize symbol: − (horizontal dash)
        SVGPath minSymbol = new SVGPath();
        minSymbol.setContent("M3 6 L9 6");
        minSymbol.setStroke(Color.web("#995700", 0.85));
        minSymbol.setStrokeWidth(1.5);
        minSymbol.setStrokeLineCap(StrokeLineCap.ROUND);
        minSymbol.setFill(Color.TRANSPARENT);
        minSymbol.setMouseTransparent(true);
        minSymbol.setVisible(false);
        windowMinBtn.setGraphic(minSymbol);
        
        // Maximize symbol: two small triangles at opposite corners (expand)
        SVGPath maxSymbol = new SVGPath();
        maxSymbol.setContent("M7.5 2.5 L10 2.5 L10 5 Z M2.5 7.5 L5 10 L2.5 10 Z");
        maxSymbol.setFill(Color.web("#006500", 0.85));
        maxSymbol.setStroke(null);
        maxSymbol.setMouseTransparent(true);
        maxSymbol.setVisible(false);
        windowMaxBtn.setGraphic(maxSymbol);
        
        // macOS behavior: hover over the GROUP shows symbols on ALL buttons
        windowBtnGroup.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            closeSymbol.setVisible(true);
            minSymbol.setVisible(true);
            maxSymbol.setVisible(true);
        });
        windowBtnGroup.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            closeSymbol.setVisible(false);
            minSymbol.setVisible(false);
            maxSymbol.setVisible(false);
        });
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
     * Extends draggable zones to match product UX:
     * - whole left icon column background
     * - middle top header area (Skills + search strip)
     * while preserving control click/focus behaviors.
     */
    private void setupExtendedWindowDragZones() {
        installDragBehavior(iconBar);
        installDragBehavior(skillSidebarHeader);
    }

    private void installDragBehavior(Node region) {
        if (region == null) {
            return;
        }
        region.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            region.setCursor(isDragBlockedTarget(e.getTarget()) ? Cursor.DEFAULT : Cursor.OPEN_HAND);
        });
        region.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (stage == null || !e.isPrimaryButtonDown() || isDragBlockedTarget(e.getTarget())) {
                return;
            }
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
            region.setCursor(Cursor.CLOSED_HAND);
        });
        region.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (stage == null || isDragBlockedTarget(e.getTarget())) {
                return;
            }
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });
        region.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            region.setCursor(isDragBlockedTarget(e.getTarget()) ? Cursor.DEFAULT : Cursor.OPEN_HAND);
        });
    }

    private boolean isDragBlockedTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node cur = node;
        while (cur != null) {
            if (cur instanceof Button
                    || cur instanceof TextField
                    || cur instanceof TextArea
                    || cur instanceof ChoiceBox<?>
                    || cur instanceof ComboBox<?>
                    || cur instanceof Hyperlink
                    || cur instanceof ScrollBar
                    || cur instanceof Slider) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }
}
