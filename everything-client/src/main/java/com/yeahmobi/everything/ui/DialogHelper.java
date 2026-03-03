package com.yeahmobi.everything.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.util.Optional;

/**
 * Helper class for creating styled dialogs and alerts.
 * Now uses CustomDialog for a completely decoration-free experience.
 */
public class DialogHelper {

    /**
     * Creates a styled information alert.
     * For compatibility with existing code that needs Alert instance.
     */
    public static Alert createInformation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        styleDialog(alert, "information");
        return alert;
    }

    /**
     * Creates a styled confirmation alert.
     * For compatibility with existing code that needs Alert instance.
     */
    public static Alert createConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        styleDialog(alert, "confirmation");
        return alert;
    }

    /**
     * Creates a styled warning alert.
     * For compatibility with existing code that needs Alert instance.
     */
    public static Alert createWarning(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        styleDialog(alert, "warning");
        return alert;
    }

    /**
     * Creates a styled error alert.
     * For compatibility with existing code that needs Alert instance.
     */
    public static Alert createError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        styleDialog(alert, "error");
        return alert;
    }

    /**
     * Shows a styled information dialog using custom decoration-free dialog.
     */
    public static void showInformation(String title, String content) {
        CustomDialog.showInformation(content);
    }

    /**
     * Shows a styled confirmation dialog using custom decoration-free dialog.
     */
    public static Optional<ButtonType> showConfirmation(String title, String content) {
        return CustomDialog.showConfirmation(content);
    }

    /**
     * Shows a styled warning dialog using custom decoration-free dialog.
     */
    public static void showWarning(String title, String content) {
        CustomDialog.showWarning(content);
    }

    /**
     * Shows a styled error dialog using custom decoration-free dialog.
     */
    public static void showError(String title, String content) {
        CustomDialog.showError(content);
    }

    /**
     * Applies custom styling to a dialog/alert.
     */
    private static void styleDialog(Alert alert, String styleClass) {
        DialogPane dialogPane = alert.getDialogPane();
        
        // Ensure the dialog pane has the correct style class
        if (!dialogPane.getStyleClass().contains(styleClass)) {
            dialogPane.getStyleClass().add(styleClass);
        }
        
        // Remove title, header, and graphic for minimalist design
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setGraphic(null);
        dialogPane.setGraphic(null);
        
        // Hide system window decorations (close button, etc.)
        if (alert.getDialogPane().getScene() != null && 
            alert.getDialogPane().getScene().getWindow() != null) {
            javafx.stage.Stage stage = (javafx.stage.Stage) alert.getDialogPane().getScene().getWindow();
            // Make it undecorated to remove system buttons
            // Note: This needs to be set before showing, but we can try styling
        }
    }
}
