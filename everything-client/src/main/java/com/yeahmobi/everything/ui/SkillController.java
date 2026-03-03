package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.common.NetworkException;
import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillService;
import com.yeahmobi.everything.skill.SkillType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Skill list page.
 * <p>
 * Displays all available Skills in a card grid layout with search,
 * category filtering, type filtering, favorites, and recently used sections.
 * </p>
 */
public class SkillController {

    private static final Logger LOGGER = Logger.getLogger(SkillController.class.getName());
    private static final int RECENT_LIMIT = 6;
    private static final double CARD_WIDTH = 260;
    private static final double CARD_HEIGHT = 180;

    @FXML private TextField searchField;
    @FXML private HBox typeFilterBar;
    @FXML private HBox categoryFilterBar;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private HBox errorBox;
    @FXML private Label errorLabel;
    @FXML private Button retryBtn;
    @FXML private VBox favoritesSection;
    @FXML private FlowPane favoritesPane;
    @FXML private VBox recentSection;
    @FXML private FlowPane recentPane;
    @FXML private Label allSkillsTitle;
    @FXML private FlowPane skillsPane;
    @FXML private VBox emptyStateBox;

    @FXML private Button typeAllBtn;
    @FXML private Button typeGeneralBtn;
    @FXML private Button typeInternalBtn;

    private SkillService skillService;
    private String userId;
    private List<Skill> allSkills = new ArrayList<>();
    private SkillType currentTypeFilter = null; // null = all
    private String currentCategoryFilter = null; // null = all
    private SkillClickCallback onSkillClick;
    private boolean favoritesOnly = false;

    /**
     * Callback invoked when a user clicks on a Skill card.
     */
    @FunctionalInterface
    public interface SkillClickCallback {
        void onSkillClicked(Skill skill);
    }

    /**
     * Sets the SkillService dependency.
     */
    public void setSkillService(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * Sets the current user ID.
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Shows only favorites when true.
     */
    public void setFavoritesOnly(boolean favoritesOnly) {
        this.favoritesOnly = favoritesOnly;
        updateSectionVisibility();
    }

    /**
     * Sets the callback for Skill card clicks (navigate to chat).
     */
    public void setOnSkillClick(SkillClickCallback callback) {
        this.onSkillClick = callback;
    }

    @FXML
    public void initialize() {
        // Initial state: loading hidden, error hidden
    }

    /**
     * Loads all skills from the service and refreshes the UI.
     * Should be called after dependencies are set.
     */
    public void loadSkills() {
        if (skillService == null) {
            setLoading(false);
            showError("技能服务不可用，请检查数据库连接或后端配置");
            showEmptyState(true);
            return;
        }
        setLoading(true);
        hideError();

        CompletableFuture.supplyAsync(() -> {
            try {
                return skillService.fetchSkills();
            } catch (NetworkException e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(skills -> {
            if (favoritesOnly && userId != null) {
                allSkills = new ArrayList<>(skillService.getFavorites(userId));
            } else {
                allSkills = new ArrayList<>(skills);
            }
            setLoading(false);
            buildCategoryFilters();
            refreshDisplay();
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                setLoading(false);
                showError("无法加载 Skill 列表，请检查网络连接");
            });
            LOGGER.log(Level.WARNING, "Failed to load skills", ex);
            return null;
        });
    }

    // ---- Search handler ----

    @FXML
    void onSearchChanged() {
        refreshDisplay();
    }

    // ---- Type filter handlers ----

    @FXML
    void onFilterTypeAll() {
        currentTypeFilter = null;
        updateTypeFilterStyles(typeAllBtn);
        refreshDisplay();
    }

    @FXML
    void onFilterTypeGeneral() {
        currentTypeFilter = SkillType.GENERAL;
        updateTypeFilterStyles(typeGeneralBtn);
        refreshDisplay();
    }

    @FXML
    void onFilterTypeInternal() {
        currentTypeFilter = SkillType.INTERNAL;
        updateTypeFilterStyles(typeInternalBtn);
        refreshDisplay();
    }

    // ---- Retry handler ----

    @FXML
    void onRetry() {
        loadSkills();
    }

    // ---- Display logic ----

    /**
     * Refreshes the entire display based on current filters.
     */
    void refreshDisplay() {
        String keyword = searchField.getText();
        List<Skill> filtered = applyFilters(allSkills, keyword, currentTypeFilter, currentCategoryFilter);

        // Update main skills grid
        skillsPane.getChildren().clear();
        for (Skill skill : filtered) {
            skillsPane.getChildren().add(createSkillCard(skill));
        }

        // Empty state
        showEmptyState(filtered.isEmpty());

        // Update sections based on mode
        if (!favoritesOnly) {
            refreshFavorites();
            refreshRecentlyUsed();
        }
    }

    /**
     * Applies search, type, and category filters to the skill list.
     */
    List<Skill> applyFilters(List<Skill> skills, String keyword, SkillType typeFilter, String categoryFilter) {
        List<Skill> result = new ArrayList<>(skills);

        // Apply search
        if (keyword != null && !keyword.isBlank()) {
            result = skillService.searchSkills(keyword, result);
        }

        // Apply type filter
        if (typeFilter != null) {
            result = skillService.filterByType(typeFilter, result);
        }

        // Apply category filter
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            result = skillService.filterByCategory(categoryFilter, result);
        }

        return result;
    }

    /**
     * Refreshes the favorites section.
     */
    private void refreshFavorites() {
        if (userId == null || skillService == null) {
            favoritesSection.setVisible(false);
            favoritesSection.setManaged(false);
            return;
        }

        List<Skill> favorites = skillService.getFavorites(userId);
        if (favorites.isEmpty()) {
            favoritesSection.setVisible(false);
            favoritesSection.setManaged(false);
        } else {
            favoritesSection.setVisible(true);
            favoritesSection.setManaged(true);
            favoritesPane.getChildren().clear();
            for (Skill skill : favorites) {
                favoritesPane.getChildren().add(createSkillCard(skill));
            }
        }
    }

    /**
     * Refreshes the recently used section.
     */
    private void refreshRecentlyUsed() {
        if (userId == null || skillService == null) {
            recentSection.setVisible(false);
            recentSection.setManaged(false);
            return;
        }

        List<Skill> recent = skillService.getRecentlyUsed(userId, RECENT_LIMIT);
        if (recent.isEmpty()) {
            recentSection.setVisible(false);
            recentSection.setManaged(false);
        } else {
            recentSection.setVisible(true);
            recentSection.setManaged(true);
            recentPane.getChildren().clear();
            for (Skill skill : recent) {
                recentPane.getChildren().add(createSkillCard(skill));
            }
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateBox == null) {
            return;
        }
        emptyStateBox.setVisible(show);
        emptyStateBox.setManaged(show);
    }

    private void updateSectionVisibility() {
        if (favoritesOnly) {
            if (favoritesSection != null) {
                favoritesSection.setVisible(false);
                favoritesSection.setManaged(false);
            }
            if (recentSection != null) {
                recentSection.setVisible(false);
                recentSection.setManaged(false);
            }
            if (allSkillsTitle != null) {
                allSkillsTitle.setText("我的收藏");
            }
        } else {
            if (allSkillsTitle != null) {
                allSkillsTitle.setText("全部技能");
            }
        }
    }

    // ---- Card creation ----

    /**
     * Creates a Skill card node for display in the FlowPane.
     */
    VBox createSkillCard(Skill skill) {
        VBox card = new VBox(8);
        card.getStyleClass().add("skill-card");
        card.setPrefWidth(CARD_WIDTH);
        card.setPrefHeight(CARD_HEIGHT);
        card.setMaxWidth(CARD_WIDTH);
        card.setPadding(new Insets(12));

        // Top row: icon + name + badges
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        SkillI18nZhCn zh = SkillI18nUtil.zhCn(skill);
        String displayName = (zh != null && zh.displayName() != null) ? zh.displayName() : skill.name();
        String oneLine = (zh != null && zh.oneLine() != null) ? zh.oneLine() : skill.description();

        String iconText = (displayName != null && !displayName.isBlank())
                ? displayName.substring(0, 1)
                : "S";
        Label iconLabel = new Label(iconText);
        iconLabel.getStyleClass().add("skill-icon");

        Label nameLabel = new Label(displayName != null ? displayName : "");
        nameLabel.getStyleClass().add("skill-title");
        nameLabel.setMaxWidth(160);

        topRow.getChildren().addAll(iconLabel, nameLabel);

        // Internal badge
        if (skill.type() == SkillType.INTERNAL) {
            Label badge = new Label("内部");
            badge.getStyleClass().add("skill-badge");
            topRow.getChildren().add(badge);
        }

        // Quality badge
        if (skill.qualityTier() != null && "verified".equalsIgnoreCase(skill.qualityTier().trim())) {
            Label badge = new Label("已验证");
            badge.getStyleClass().add("skill-badge");
            topRow.getChildren().add(badge);
        }

        // Category tag
        Label categoryLabel = new Label(skill.category() != null ? skill.category() : "");
        categoryLabel.getStyleClass().add("skill-category");

        // Description
        Label descLabel = new Label(oneLine != null ? oneLine : "");
        descLabel.getStyleClass().add("skill-desc");
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(40);

        // Bottom row: favorite button
        HBox bottomRow = new HBox();
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button favBtn = new Button(isFavorite(skill.id()) ? "★" : "☆");
        favBtn.getStyleClass().add("btn-icon");
        favBtn.setOnAction(e -> {
            handleToggleFavorite(skill.id(), favBtn);
            e.consume();
        });

        bottomRow.getChildren().addAll(spacer, favBtn);

        card.getChildren().addAll(topRow, categoryLabel, descLabel, bottomRow);

        // Click handler to enter chat
        card.setOnMouseClicked(e -> {
            if (onSkillClick != null && skillService != null && userId != null) {
                skillService.recordUsage(userId, skill.id());
            }
            if (onSkillClick != null) {
                onSkillClick.onSkillClicked(skill);
            }
        });

        return card;
    }

    // ---- Favorite toggle ----

    private void handleToggleFavorite(String skillId, Button favBtn) {
        if (skillService == null || userId == null) return;
        skillService.toggleFavorite(userId, skillId);
        boolean nowFav = isFavorite(skillId);
        favBtn.setText(nowFav ? "★" : "☆");
        refreshFavorites();
    }

    private boolean isFavorite(String skillId) {
        if (skillService == null || userId == null) return false;
        // Delegate to service which checks FavoriteRepository
        List<Skill> favs = skillService.getFavorites(userId);
        return favs.stream().anyMatch(s -> s.id().equals(skillId));
    }

    // ---- Category filter building ----

    /**
     * Builds category filter buttons from the loaded skills.
     */
    void buildCategoryFilters() {
        // Collect unique categories
        Set<String> categories = new TreeSet<>();
        for (Skill skill : allSkills) {
            if (skill.category() != null && !skill.category().isBlank()) {
                categories.add(skill.category().trim());
            }
        }

        categoryFilterBar.getChildren().clear();
        Label label = new Label("分类:");
        label.getStyleClass().add("section-subtitle");
        categoryFilterBar.getChildren().add(label);

        // "All" button
        Button allBtn = new Button("全部");
        allBtn.getStyleClass().add(currentCategoryFilter == null ? "filter-tag-active" : "filter-tag");
        allBtn.setOnAction(e -> {
            currentCategoryFilter = null;
            buildCategoryFilters();
            refreshDisplay();
        });
        categoryFilterBar.getChildren().add(allBtn);

        for (String cat : categories) {
            Button catBtn = new Button(cat);
            catBtn.getStyleClass().add(cat.equals(currentCategoryFilter) ? "filter-tag-active" : "filter-tag");
            catBtn.setOnAction(e -> {
                currentCategoryFilter = cat.trim();
                buildCategoryFilters();
                refreshDisplay();
            });
            categoryFilterBar.getChildren().add(catBtn);
        }
    }

    // ---- Type filter style update ----

    private void updateTypeFilterStyles(Button activeBtn) {
        for (var node : typeFilterBar.getChildren()) {
            if (node instanceof Button btn) {
                btn.getStyleClass().removeAll("filter-tag-active", "filter-tag");
                btn.getStyleClass().add(btn == activeBtn ? "filter-tag-active" : "filter-tag");
            }
        }
    }


    // ---- Loading / Error helpers ----

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorBox.setVisible(true);
        errorBox.setManaged(true);
    }

    private void hideError() {
        errorBox.setVisible(false);
        errorBox.setManaged(false);
    }

    // ---- Getters for testing ----

    TextField getSearchField() { return searchField; }
    FlowPane getSkillsPane() { return skillsPane; }
    FlowPane getFavoritesPane() { return favoritesPane; }
    FlowPane getRecentPane() { return recentPane; }
    VBox getFavoritesSection() { return favoritesSection; }
    VBox getRecentSection() { return recentSection; }
    HBox getTypeFilterBar() { return typeFilterBar; }
    HBox getCategoryFilterBar() { return categoryFilterBar; }
    ProgressIndicator getLoadingIndicator() { return loadingIndicator; }
    HBox getErrorBox() { return errorBox; }
    Label getErrorLabel() { return errorLabel; }
    List<Skill> getAllSkills() { return allSkills; }
    SkillType getCurrentTypeFilter() { return currentTypeFilter; }
    String getCurrentCategoryFilter() { return currentCategoryFilter; }
}
