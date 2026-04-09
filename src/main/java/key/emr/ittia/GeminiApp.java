package key.emr.ittia;

import key.emr.ittia.data.DatabaseManager;
import key.emr.ittia.model.Model;
import key.emr.ittia.model.Prompt;
import key.emr.ittia.service.AiService;
import key.emr.ittia.service.GeminiService;
import key.emr.ittia.service.ImagePathService;
import key.emr.ittia.service.OpenAiService;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeminiApp extends Application {

    private String modelName = "gemini-2.5-flash"; // Default model

    private final DatabaseManager dbManager = new DatabaseManager();
    private final ImagePathService imagePathService = new ImagePathService();
    private final GeminiService geminiService = new GeminiService(imagePathService);
    private final OpenAiService openAiService = new OpenAiService(imagePathService);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dbManager.createTable();
        primaryStage.setTitle("AI API Client");

        BorderPane borderPane = new BorderPane();
        borderPane.setPadding(new Insets(18));
        borderPane.setId("app-root");

        // Center UI
        TextArea promptArea = new TextArea();
        promptArea.setPromptText("Enter your prompt here...");
        promptArea.setWrapText(true);
        promptArea.setPrefHeight(160);
        promptArea.setPrefWidth(520);

        Button sendButton = new Button("Send");
        Button refreshButton = new Button("Refresh");
        Button quitButton = new Button("Quit");
        Button copyButton = new Button("Copy");

        HBox buttonBox = new HBox(12, sendButton, refreshButton, copyButton, quitButton);
        buttonBox.getStyleClass().add("button-bar");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);

        TextArea responseArea = new TextArea();
        responseArea.setEditable(false);
        responseArea.setWrapText(true);
        responseArea.setPrefHeight(360);
        responseArea.setPrefWidth(520);

        TextField imagePathField = new TextField();
        imagePathField.setPromptText("Image paths or folders (separate with ; )");
        imagePathField.setPrefWidth(360);
        Button browseImagesButton = new Button("Browse");
        Button browseFolderButton = new Button("Folder");
        HBox imagePickerBox = new HBox(10, imagePathField, browseImagesButton, browseFolderButton);
        imagePickerBox.getStyleClass().add("button-bar");

        VBox centerBox = new VBox(15,
                labeled("Images:"),
                imagePickerBox,
                labeled("Prompt:"),
                promptArea,
                buttonBox,
                progressIndicator,
                labeled("Response:"),
                responseArea);
        centerBox.getStyleClass().add("section-card");
        borderPane.setCenter(centerBox);

        // Left UI (Pre-typed Prompts)
        TableView<Prompt> promptTable = new TableView<>();
        TableColumn<Prompt, String> promptColumn = new TableColumn<>("Prompt");
        promptColumn.setCellValueFactory(new PropertyValueFactory<>("promptText"));
        promptColumn.setPrefWidth(260);
        TableColumn<Prompt, String> promptCategoryColumn = new TableColumn<>("Category");
        promptCategoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        promptCategoryColumn.setPrefWidth(140);
        promptTable.getColumns().addAll(promptColumn, promptCategoryColumn);
        promptTable.setPrefWidth(420);
        promptTable.setPrefHeight(520);


        Button addButton = new Button("Add");
        Button deleteButton = new Button("Delete");
        Button editButton = new Button("Edit");
        Button findButton = new Button("Find");
        HBox promptButtonBox = new HBox(12, addButton, deleteButton, editButton, findButton);
        promptButtonBox.getStyleClass().add("button-bar");

        VBox leftBox = new VBox(15, promptTable, promptButtonBox);
        leftBox.getStyleClass().add("section-card");
        leftBox.setPrefWidth(450);
        borderPane.setLeft(leftBox);

        // Right UI (Model List)
        ComboBox<String> providerComboBox = new ComboBox<>();
        providerComboBox.setItems(FXCollections.observableArrayList(
                geminiService.getProviderName(),
                openAiService.getProviderName()
        ));
        providerComboBox.setValue(geminiService.getProviderName());

        Label currentProviderLabel = labeled("Current Provider: " + providerComboBox.getValue());
        Label currentModelLabel = labeled("Current Model: " + modelName);
        TableView<Model> modelTable = new TableView<>();
        TableColumn<Model, String> modelColumn = new TableColumn<>("Model");
        modelColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        modelColumn.setPrefWidth(180);

        TableColumn<Model, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryColumn.setPrefWidth(120);

        TableColumn<Model, String> providerColumn = new TableColumn<>("Provider");
        providerColumn.setCellValueFactory(new PropertyValueFactory<>("provider"));
        providerColumn.setPrefWidth(110);

        TableColumn<Model, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionColumn.setPrefWidth(380);

        modelTable.getColumns().addAll(modelColumn, categoryColumn, providerColumn, descriptionColumn);
        modelTable.setPrefHeight(560);

        VBox rightBox = new VBox(15, labeled("Provider:"), providerComboBox, currentProviderLabel, currentModelLabel, modelTable);
        rightBox.getStyleClass().add("section-card");
        rightBox.setPrefWidth(820);
        borderPane.setRight(rightBox);

        loadModels(providerComboBox.getValue(), modelTable, responseArea, currentProviderLabel, currentModelLabel);

        modelTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                modelName = newSelection.getName();
                currentModelLabel.setText("Current Model: " + modelName);
                currentProviderLabel.setText("Current Provider: " + newSelection.getProvider());
                providerComboBox.setValue(newSelection.getProvider());
            }
        });

        providerComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(oldValue)) {
                return;
            }
            currentProviderLabel.setText("Current Provider: " + newValue);
            loadModels(newValue, modelTable, responseArea, currentProviderLabel, currentModelLabel);
        });

        promptTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                promptArea.setText(newSelection.getPromptText());
            }
        });

        promptTable.setItems(FXCollections.observableArrayList(dbManager.loadPrompts()));

        addButton.setOnAction(event -> {
            PromptEditDialog dialog = new PromptEditDialog(null); // No initial prompt for adding
            dialog.getDialogPane().setPrefWidth(500); // Set preferred width
            Optional<Prompt> result = dialog.showAndWait();
            result.ifPresent(newPrompt -> {
                dbManager.addPrompt(newPrompt);
                promptTable.getItems().add(newPrompt);
            });
        });

        deleteButton.setOnAction(event -> {
            Prompt selectedPrompt = promptTable.getSelectionModel().getSelectedItem();
            if (selectedPrompt != null) {
                dbManager.deletePrompt(selectedPrompt);
                promptTable.getItems().remove(selectedPrompt);
            }
        });

        editButton.setOnAction(event -> {
            Prompt selectedPrompt = promptTable.getSelectionModel().getSelectedItem();
            if (selectedPrompt != null) {
                PromptEditDialog dialog = new PromptEditDialog(selectedPrompt); // Pass existing prompt for editing
                dialog.getDialogPane().setPrefWidth(500); // Set preferred width
                Optional<Prompt> result = dialog.showAndWait();
                result.ifPresent(updatedPrompt -> {
                    dbManager.updatePrompt(updatedPrompt);
                    promptTable.refresh();
                });
            }
        });

        findButton.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Find Prompt");
            dialog.setHeaderText("Enter text to find in prompts:");
            dialog.setContentText("Find:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(searchText -> {
                for (Prompt prompt : promptTable.getItems()) {
                    if (prompt.getPromptText().toLowerCase().contains(searchText.toLowerCase())) {
                        promptTable.getSelectionModel().select(prompt);
                        promptTable.scrollTo(prompt);
                        break;
                    }
                }
            });
        });


        browseImagesButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Image Files");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"));
            List<java.io.File> files = chooser.showOpenMultipleDialog(primaryStage);
            if (files != null && !files.isEmpty()) {
                String joined = files.stream()
                        .map(java.io.File::getAbsolutePath)
                        .collect(Collectors.joining("; "));
                imagePathField.setText(joined);
            }
        });

        browseFolderButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Image Folder");
            java.io.File folder = chooser.showDialog(primaryStage);
            if (folder != null) {
                imagePathField.setText(folder.getAbsolutePath());
            }
        });

        sendButton.setOnAction(event -> {
            String prompt = promptArea.getText();
            if (prompt.isEmpty()) {
                return;
            }

            Task<String> apiCallTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    AiService service = getService(providerComboBox.getValue());
                    List<Path> imagePaths = imagePathService.parseImagePaths(imagePathField.getText());
                    return service.generateResponse(modelName, prompt, imagePaths);
                }
            };

            apiCallTask.setOnSucceeded(e -> {
                progressIndicator.setVisible(false);
                responseArea.setText(apiCallTask.getValue());
            });

            apiCallTask.setOnFailed(e -> {
                progressIndicator.setVisible(false);
                responseArea.setText("Error: " + apiCallTask.getException().getMessage());
            });

            progressIndicator.setVisible(true);
            new Thread(apiCallTask).start();
        });

        refreshButton.setOnAction(event -> {
            promptArea.clear();
            responseArea.clear();
        });

        copyButton.setOnAction(event -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(responseArea.getText());
            clipboard.setContent(content);
        });

        quitButton.setOnAction(event -> {
            primaryStage.close();
        });

        Scene scene = new Scene(borderPane, 1850, 820);
        scene.getStylesheets().add(
                getClass().getResource("/key/emr/ittia/renoir.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Label labeled(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private void loadModels(
            String provider,
            TableView<Model> modelTable,
            TextArea responseArea,
            Label currentProviderLabel,
            Label currentModelLabel
    ) {
        Task<List<Model>> listModelsTask = new Task<>() {
            @Override
            protected List<Model> call() throws Exception {
                return getService(provider).listModels();
            }
        };

        listModelsTask.setOnSucceeded(e -> {
            List<Model> models = listModelsTask.getValue();
            modelTable.setItems(FXCollections.observableArrayList(models));
            if (!models.isEmpty()) {
                Model firstModel = models.get(0);
                modelTable.getSelectionModel().select(firstModel);
                modelName = firstModel.getName();
                currentProviderLabel.setText("Current Provider: " + firstModel.getProvider());
                currentModelLabel.setText("Current Model: " + modelName);
            }
        });

        listModelsTask.setOnFailed(e -> {
            modelTable.setItems(FXCollections.observableArrayList());
            responseArea.setText("Error loading models for " + provider + ": "
                    + listModelsTask.getException().getMessage());
        });

        new Thread(listModelsTask).start();
    }

    private AiService getService(String provider) {
        if (openAiService.getProviderName().equals(provider)) {
            return openAiService;
        }
        return geminiService;
    }
}
