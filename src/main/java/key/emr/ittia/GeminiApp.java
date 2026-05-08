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
import javafx.scene.control.TableRow;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class GeminiApp extends Application {

    private String modelName = "gemini-1.5-flash"; // Default model

    private final DatabaseManager dbManager = new DatabaseManager();
    private final ImagePathService imagePathService = new ImagePathService();
    private final GeminiService geminiService = new GeminiService(imagePathService);
    private final OpenAiService openAiService = new OpenAiService(imagePathService);
    private final AtomicLong modelLoadSequence = new AtomicLong();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dbManager.createTable();
        primaryStage.setTitle("AI API Client");

        BorderPane borderPane = new BorderPane();
        borderPane.setPadding(new Insets(15));
        borderPane.setId("app-root");

        // --- Center Workspace (Prompt & Response) ---
        VBox centerBox = new VBox(12);
        centerBox.getStyleClass().add("section-card");
        
        TextField imagePathField = new TextField();
        imagePathField.setPromptText("Select image paths or folders...");
        HBox.setHgrow(imagePathField, javafx.scene.layout.Priority.ALWAYS);
        
        Button browseImagesButton = new Button("Files");
        Button browseFolderButton = new Button("Folder");
        HBox imagePickerBox = new HBox(8, labeled("Images:"), imagePathField, browseImagesButton, browseFolderButton);
        imagePickerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextArea promptArea = new TextArea();
        promptArea.setPromptText("Enter your prompt here...");
        promptArea.setWrapText(true);
        VBox.setVgrow(promptArea, javafx.scene.layout.Priority.SOMETIMES);
        promptArea.setPrefHeight(150);

        Button sendButton = new Button("Send Request");
        sendButton.setMinWidth(120);
        Button refreshButton = new Button("Clear");
        Button copyButton = new Button("Copy Output");
        Button quitButton = new Button("Quit");
        
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(24, 24);

        HBox mainButtonBar = new HBox(12, sendButton, refreshButton, copyButton, progressIndicator, new javafx.scene.layout.Region(), quitButton);
        HBox.setHgrow(mainButtonBar.getChildren().get(4), javafx.scene.layout.Priority.ALWAYS);
        mainButtonBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextArea responseArea = new TextArea();
        responseArea.setEditable(false);
        responseArea.setWrapText(true);
        responseArea.setPromptText("AI response will appear here...");
        VBox.setVgrow(responseArea, javafx.scene.layout.Priority.ALWAYS);

        responseArea.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                copyButton.fire();
            }
        });

        centerBox.getChildren().addAll(
                imagePickerBox,
                labeled("Prompt:"), promptArea,
                mainButtonBar,
                labeled("Response:"), responseArea
        );

        // --- Left Sidebar (Saved Prompts) ---
        VBox leftBox = new VBox(12);
        leftBox.getStyleClass().add("section-card");
        
        TableView<Prompt> promptTable = new TableView<>();
        TableColumn<Prompt, String> promptColumn = new TableColumn<>("Prompt Content");
        promptColumn.setCellValueFactory(new PropertyValueFactory<>("promptText"));
        TableColumn<Prompt, String> promptCategoryColumn = new TableColumn<>("Category");
        promptCategoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        promptTable.getColumns().addAll(promptColumn, promptCategoryColumn);
        promptTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(promptTable, javafx.scene.layout.Priority.ALWAYS);

        Button addButton = new Button("Add");
        Button deleteButton = new Button("Del");
        Button editButton = new Button("Edit");
        Button findButton = new Button("Find");
        HBox promptActions = new HBox(8, addButton, editButton, deleteButton, findButton);
        promptActions.setAlignment(javafx.geometry.Pos.CENTER);

        leftBox.getChildren().addAll(labeled("Saved Prompts"), promptTable, promptActions);

        // --- Right Sidebar (Model Settings) ---
        VBox rightBox = new VBox(12);
        rightBox.getStyleClass().add("section-card");

        ComboBox<String> providerComboBox = new ComboBox<>();
        providerComboBox.setMaxWidth(Double.MAX_VALUE);
        providerComboBox.setItems(FXCollections.observableArrayList(
                geminiService.getProviderName(),
                openAiService.getProviderName()
        ));
        providerComboBox.setValue(geminiService.getProviderName());

        Label currentModelLabel = new Label("Model: " + modelName);
        currentModelLabel.getStyleClass().add("section-title");
        currentModelLabel.setWrapText(true);

        TableView<Model> modelTable = new TableView<>();
        TableColumn<Model, String> modelNameCol = new TableColumn<>("Model Name");
        modelNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Model, String> modelCatCol = new TableColumn<>("Type");
        modelCatCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        modelTable.getColumns().addAll(modelNameCol, modelCatCol);
        modelTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(modelTable, javafx.scene.layout.Priority.ALWAYS);

        Label descriptionLabel = new Label("Description:");
        descriptionLabel.getStyleClass().add("section-title");
        TextArea modelDescArea = new TextArea();
        modelDescArea.setEditable(false);
        modelDescArea.setWrapText(true);
        modelDescArea.setPrefHeight(100);

        rightBox.getChildren().addAll(
                labeled("API Provider"), providerComboBox,
                currentModelLabel,
                labeled("Available Models"), modelTable,
                descriptionLabel, modelDescArea
        );

        // --- Main Layout with SplitPanes ---
        javafx.scene.control.SplitPane mainSplit = new javafx.scene.control.SplitPane();
        mainSplit.getItems().addAll(leftBox, centerBox, rightBox);
        mainSplit.setDividerPositions(0.25, 0.75);
        borderPane.setCenter(mainSplit);

        loadModels(providerComboBox.getValue(), modelTable, responseArea, null, currentModelLabel);

        modelTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                modelName = newSelection.getName();
                currentModelLabel.setText("Model: " + modelName);
                modelDescArea.setText(newSelection.getDescription());
            }
        });

        providerComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                loadModels(newValue, modelTable, responseArea, null, currentModelLabel);
            }
        });

        promptTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                promptArea.setText(newSelection.getPromptText());
            }
        });

        try {
            promptTable.setItems(FXCollections.observableArrayList(dbManager.loadPrompts()));
        } catch (RuntimeException e) {
            responseArea.setText("Error loading prompts: " + e.getMessage());
        }

        addButton.setOnAction(event -> {
            PromptEditDialog dialog = new PromptEditDialog(null); // No initial prompt for adding
            dialog.getDialogPane().setPrefWidth(500); // Set preferred width
            Optional<Prompt> result = dialog.showAndWait();
            result.ifPresent(newPrompt -> {
                try {
                    dbManager.addPrompt(newPrompt);
                    promptTable.getItems().add(newPrompt);
                } catch (RuntimeException e) {
                    responseArea.setText("Error adding prompt: " + e.getMessage());
                }
            });
        });

        deleteButton.setOnAction(event -> {
            Prompt selectedPrompt = promptTable.getSelectionModel().getSelectedItem();
            if (selectedPrompt != null) {
                try {
                    dbManager.deletePrompt(selectedPrompt);
                    promptTable.getItems().remove(selectedPrompt);
                } catch (RuntimeException e) {
                    responseArea.setText("Error deleting prompt: " + e.getMessage());
                }
            }
        });

        editButton.setOnAction(event -> {
            Prompt selectedPrompt = promptTable.getSelectionModel().getSelectedItem();
            if (selectedPrompt != null) {
                Prompt promptDraft = new Prompt(
                        selectedPrompt.getId(),
                        selectedPrompt.getPromptText(),
                        selectedPrompt.getCategory()
                );
                PromptEditDialog dialog = new PromptEditDialog(promptDraft); // Edit a draft until DB update succeeds.
                dialog.getDialogPane().setPrefWidth(500); // Set preferred width
                Optional<Prompt> result = dialog.showAndWait();
                result.ifPresent(updatedPrompt -> {
                    try {
                        dbManager.updatePrompt(updatedPrompt);
                        selectedPrompt.setPromptText(updatedPrompt.getPromptText());
                        selectedPrompt.setCategory(updatedPrompt.getCategory());
                        promptTable.refresh();
                    } catch (RuntimeException e) {
                        responseArea.setText("Error updating prompt: " + e.getMessage());
                    }
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

        promptTable.setRowFactory(tv -> {
            TableRow<Prompt> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    promptArea.setText(row.getItem().getPromptText());
                }
            });
            return row;
        });

        modelTable.setRowFactory(tv -> {
            TableRow<Model> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    promptArea.requestFocus();
                }
            });
            return row;
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
            Thread apiThread = new Thread(apiCallTask, "api-call");
            apiThread.setDaemon(true);
            apiThread.start();
        });

        refreshButton.setOnAction(event -> {
            promptArea.clear();
            responseArea.clear();
            imagePathField.clear();
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

        Scene scene = new Scene(borderPane, 1600, 900);
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
        long loadId = modelLoadSequence.incrementAndGet();
        Task<List<Model>> listModelsTask = new Task<>() {
            @Override
            protected List<Model> call() throws Exception {
                return getService(provider).listModels();
            }
        };

        listModelsTask.setOnSucceeded(e -> {
            if (loadId != modelLoadSequence.get()) {
                return;
            }
            List<Model> models = listModelsTask.getValue();
            modelTable.setItems(FXCollections.observableArrayList(models));
            if (!models.isEmpty()) {
                // Try to find the default model or a 'flash' model first
                Model selectedModel = models.stream()
                        .filter(m -> m.getName().equals(modelName))
                        .findFirst()
                        .orElseGet(() -> models.stream()
                                .filter(m -> m.getName().toLowerCase().contains("flash"))
                                .findFirst()
                                .orElse(models.get(0)));

                modelTable.getSelectionModel().select(selectedModel);
                modelName = selectedModel.getName();
                if (currentProviderLabel != null) {
                    currentProviderLabel.setText("Current Provider: " + selectedModel.getProvider());
                }
                currentModelLabel.setText("Current Model: " + modelName);
            }
        });

        listModelsTask.setOnFailed(e -> {
            if (loadId != modelLoadSequence.get()) {
                return;
            }
            modelTable.setItems(FXCollections.observableArrayList());
            responseArea.setText("Error loading models for " + provider + ": "
                    + listModelsTask.getException().getMessage());
        });

        Thread modelThread = new Thread(listModelsTask, "model-loader-" + loadId);
        modelThread.setDaemon(true);
        modelThread.start();
    }

    private AiService getService(String provider) {
        if (openAiService.getProviderName().equals(provider)) {
            return openAiService;
        }
        return geminiService;
    }
}
