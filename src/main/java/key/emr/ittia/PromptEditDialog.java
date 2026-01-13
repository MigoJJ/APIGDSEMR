package key.emr.ittia;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;

public class PromptEditDialog extends Dialog<GeminiApp.Prompt> {

    private final TextField promptTextField;
    private final TextField categoryTextField;

    public PromptEditDialog(GeminiApp.Prompt prompt) {
        setTitle("Edit Prompt");
        setHeaderText("Edit the pre-typed prompt and its category:");
        getDialogPane().getStylesheets().add(
                getClass().getResource("/key/emr/ittia/renoir.css").toExternalForm());
        getDialogPane().getStyleClass().add("renoir-dialog");

        ButtonType saveButtonType = new ButtonType("Save");
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        promptTextField = new TextField(prompt != null ? prompt.getPromptText() : "");
        promptTextField.setPromptText("Prompt");
        categoryTextField = new TextField(prompt != null ? prompt.getCategory() : "");
        categoryTextField.setPromptText("Category");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(10, 0, 0, 0));
        grid.add(new Label("Prompt:"), 0, 0);
        grid.add(promptTextField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryTextField, 1, 1);
        getDialogPane().setContent(grid);

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                if (prompt != null) {
                    prompt.setPromptText(promptTextField.getText());
                    prompt.setCategory(categoryTextField.getText());
                    return prompt;
                } else {
                    return new GeminiApp.Prompt(0, promptTextField.getText(), categoryTextField.getText());
                }
            }
            return null;
        });
    }
}
