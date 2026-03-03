package com.yeahmobi.everything.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;

/**
 * Custom dialog without system decorations.
 * Uses a transparent Stage + StackPane wrapper to achieve true rounded corners with shadow.
 */
public class CustomDialog {

    public enum DialogType {
        INFORMATION, CONFIRMATION, WARNING, ERROR
    }

    private final Stage stage;
    private final VBox card;
    private final HBox buttonBar;
    private ButtonType result = ButtonType.CANCEL;

    private CustomDialog(DialogType type, String content) {
        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);

        // Inner card - 白色圆角卡片
        card = new VBox();
        card.setAlignment(Pos.CENTER);
        card.setSpacing(0);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: rgba(0,0,0,0.06);" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 0.5;"
        );

        // 阴影直接加在card上
        DropShadow shadow = new DropShadow();
        shadow.setRadius(20);
        shadow.setOffsetY(4);
        shadow.setColor(Color.rgb(0, 0, 0, 0.18));
        card.setEffect(shadow);

        // Content
        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(320);
        contentLabel.setStyle(
            "-fx-font-size: 14px;" +
            "-fx-font-weight: 400;" +
            "-fx-text-fill: #1F2937;" +
            "-fx-line-spacing: 3;" +
            "-fx-font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Segoe UI', sans-serif;"
        );
        VBox.setMargin(contentLabel, new Insets(24, 24, 16, 24));

        // Button bar
        buttonBar = new HBox(8);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(buttonBar, new Insets(0, 20, 18, 20));

        card.getChildren().addAll(contentLabel, buttonBar);

        // Outer wrapper - 完全透明，提供阴影空间
        StackPane wrapper = new StackPane(card);
        wrapper.setStyle("-fx-background-color: transparent;");
        wrapper.setPadding(new Insets(24)); // 给阴影留空间

        Scene scene = new Scene(wrapper);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
    }

    public static void showInformation(String content) {
        CustomDialog dialog = new CustomDialog(DialogType.INFORMATION, content);
        dialog.addOkButton();
        dialog.show();
    }

    public static Optional<ButtonType> showConfirmation(String content) {
        CustomDialog dialog = new CustomDialog(DialogType.CONFIRMATION, content);
        dialog.addConfirmButtons();
        return dialog.showAndWait();
    }

    public static void showWarning(String content) {
        CustomDialog dialog = new CustomDialog(DialogType.WARNING, content);
        dialog.addOkButton();
        dialog.show();
    }

    public static void showError(String content) {
        CustomDialog dialog = new CustomDialog(DialogType.ERROR, content);
        dialog.addOkButton();
        dialog.show();
    }

    private void addOkButton() {
        Button okButton = createButton("确定", true);
        okButton.setOnAction(e -> { result = ButtonType.OK; stage.close(); });
        buttonBar.getChildren().add(okButton);
    }

    private void addConfirmButtons() {
        Button cancelButton = createButton("取消", false);
        cancelButton.setOnAction(e -> { result = ButtonType.CANCEL; stage.close(); });

        Button okButton = createButton("确定", true);
        okButton.setOnAction(e -> { result = ButtonType.OK; stage.close(); });

        buttonBar.getChildren().addAll(cancelButton, okButton);
    }

    private Button createButton(String text, boolean primary) {
        Button btn = new Button(text);
        String baseStyle =
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 16;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: 400;" +
            "-fx-cursor: hand;" +
            "-fx-min-width: 64;" +
            "-fx-font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Segoe UI', sans-serif;";

        if (primary) {
            btn.setStyle(baseStyle +
                "-fx-background-color: #07C160;" +
                "-fx-text-fill: white;" +
                "-fx-border-color: transparent;");
            btn.setOnMouseEntered(e -> btn.setStyle(baseStyle +
                "-fx-background-color: #06AD56;" +
                "-fx-text-fill: white;" +
                "-fx-border-color: transparent;"));
            btn.setOnMouseExited(e -> btn.setStyle(baseStyle +
                "-fx-background-color: #07C160;" +
                "-fx-text-fill: white;" +
                "-fx-border-color: transparent;"));
        } else {
            btn.setStyle(baseStyle +
                "-fx-background-color: #F5F5F5;" +
                "-fx-text-fill: #666666;" +
                "-fx-border-color: #E0E0E0;" +
                "-fx-border-width: 0.5;");
            btn.setOnMouseEntered(e -> btn.setStyle(baseStyle +
                "-fx-background-color: #EBEBEB;" +
                "-fx-text-fill: #333333;" +
                "-fx-border-color: #D0D0D0;" +
                "-fx-border-width: 0.5;"));
            btn.setOnMouseExited(e -> btn.setStyle(baseStyle +
                "-fx-background-color: #F5F5F5;" +
                "-fx-text-fill: #666666;" +
                "-fx-border-color: #E0E0E0;" +
                "-fx-border-width: 0.5;"));
        }
        return btn;
    }

    private void show() {
        stage.showAndWait();
    }

    private Optional<ButtonType> showAndWait() {
        stage.showAndWait();
        return Optional.of(result);
    }
}
