package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.chat.ChatService;
import com.yeahmobi.everything.chat.ChatSession;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the history page.
 * <p>
 * Displays all chat sessions in a ListView, supports search,
 * click-to-load conversation, and delete session.
 * </p>
 */
public class HistoryController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private TextField searchField;
    @FXML private Label emptyLabel;
    @FXML private ListView<ChatSession> sessionListView;

    private ChatService chatService;
    private String userId;
    private List<ChatSession> allSessions = new ArrayList<>();

    /** Callback for when a session is clicked to load the full conversation. */
    private SessionClickCallback onSessionClick;

    /**
     * Functional interface for session click navigation.
     */
    public interface SessionClickCallback {
        void onSessionClicked(ChatSession session);
    }

    @FXML
    public void initialize() {
        sessionListView.setCellFactory(listView -> new SessionListCell());
        sessionListView.setOnMouseClicked(e -> {
            ChatSession selected = sessionListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleSessionClick(selected);
            }
        });
    }

    public void setChatService(ChatService chatService) {
        this.chatService = chatService;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setOnSessionClick(SessionClickCallback callback) {
        this.onSessionClick = callback;
    }

    /**
     * Loads all sessions from the ChatService and displays them.
     */
    public void loadSessions() {
        if (chatService == null || userId == null) {
            return;
        }
        allSessions = chatService.getAllSessions(userId);
        refreshList(allSessions);
    }

    @FXML
    public void onSearchChanged() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.isBlank()) {
            refreshList(allSessions);
        } else {
            List<ChatSession> results = chatService.searchSessions(keyword.trim(), userId);
            refreshList(results);
        }
    }

    private void refreshList(List<ChatSession> sessions) {
        sessionListView.getItems().setAll(sessions);
        boolean empty = sessions.isEmpty();
        emptyLabel.setManaged(empty);
        emptyLabel.setVisible(empty);
    }

    private void handleSessionClick(ChatSession session) {
        if (onSessionClick != null) {
            onSessionClick.onSessionClicked(session);
        }
    }

    private void handleDeleteSession(ChatSession session) {
        if (chatService == null) {
            return;
        }
        chatService.deleteSession(session.id());
        allSessions.removeIf(s -> s.id().equals(session.id()));
        // Re-apply current search filter
        onSearchChanged();
    }

    static String formatTimestamp(long epochMillis) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return dateTime.format(TIME_FORMAT);
    }

    static String truncateMessage(String message, int maxLength) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + "...";
    }

    // ---- Package-private accessors for testing ----

    TextField getSearchField() { return searchField; }
    Label getEmptyLabel() { return emptyLabel; }
    ListView<ChatSession> getSessionListView() { return sessionListView; }
    List<ChatSession> getAllSessions() { return allSessions; }

    /**
     * Custom ListCell that renders each ChatSession as a row with
     * skill name, last message summary, timestamp, and a delete button.
     */
    private class SessionListCell extends ListCell<ChatSession> {

        private final VBox content;
        private final HBox topRow;
        private final Label skillNameLabel;
        private final Label timeLabel;
        private final Label messageLabel;
        private final Button deleteBtn;

        SessionListCell() {
            content = new VBox(4);
            topRow = new HBox(8);
            topRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            skillNameLabel = new Label();
            skillNameLabel.getStyleClass().add("card-title");
            skillNameLabel.setStyle("-fx-font-size: 14px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            timeLabel = new Label();
            timeLabel.getStyleClass().add("section-subtitle");

            deleteBtn = new Button("🗑");
            deleteBtn.getStyleClass().add("btn-secondary");
            deleteBtn.setStyle("-fx-padding: 2 8 2 8; -fx-font-size: 12px;");

            topRow.getChildren().addAll(skillNameLabel, spacer, timeLabel, deleteBtn);

            messageLabel = new Label();
            messageLabel.getStyleClass().add("card-description");
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(Double.MAX_VALUE);

            content.getChildren().addAll(topRow, messageLabel);
            content.setStyle("-fx-padding: 8 4 8 4;");

            setGraphic(null);
            setText(null);
        }

        @Override
        protected void updateItem(ChatSession session, boolean empty) {
            super.updateItem(session, empty);
            if (empty || session == null) {
                setGraphic(null);
                setText(null);
            } else {
                skillNameLabel.setText(session.skillName());
                timeLabel.setText(formatTimestamp(session.lastTimestamp()));
                messageLabel.setText(truncateMessage(session.lastMessage(), 80));

                deleteBtn.setOnAction(e -> {
                    e.consume();
                    handleDeleteSession(session);
                });

                content.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 1) {
                        handleSessionClick(session);
                    }
                });

                setGraphic(content);
                setText(null);
            }
        }
    }
}
