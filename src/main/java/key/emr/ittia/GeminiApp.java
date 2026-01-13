package key.emr.ittia;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import key.emr.ittia.PromptEditDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeminiApp extends Application {

    private static final String API_KEY = "AIzaSyDS2Ci7MqGffHQ4acidrXEL4IKOrFmMw1A";
    private String modelName = "gemini-2.5-flash"; // Default model

    private final Gson gson = new Gson();
    private final DatabaseManager dbManager = new DatabaseManager();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dbManager.createTable();
        primaryStage.setTitle("Gemini API Client");

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
        Label currentModelLabel = labeled("Current Model: " + modelName);
        TableView<Model> modelTable = new TableView<>();
        TableColumn<Model, String> modelColumn = new TableColumn<>("Model");
        modelColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        modelColumn.setPrefWidth(180);

        TableColumn<Model, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryColumn.setPrefWidth(120);

        TableColumn<Model, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionColumn.setPrefWidth(480);

        modelTable.getColumns().addAll(modelColumn, categoryColumn, descriptionColumn);
        modelTable.setPrefHeight(560);

        VBox rightBox = new VBox(15, currentModelLabel, modelTable);
        rightBox.getStyleClass().add("section-card");
        rightBox.setPrefWidth(820);
        borderPane.setRight(rightBox);

        // Load models in background
        Task<List<Model>> listModelsTask = new Task<>() {
            @Override
            protected List<Model> call() throws Exception {
                return listModels();
            }
        };

        listModelsTask.setOnSucceeded(e -> {
            modelTable.setItems(FXCollections.observableArrayList(listModelsTask.getValue()));
        });

        listModelsTask.setOnFailed(e -> {
            responseArea.setText("Error loading models: " + listModelsTask.getException().getMessage());
        });

        new Thread(listModelsTask).start();

        modelTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                modelName = newSelection.getName();
                currentModelLabel.setText("Current Model: " + modelName);
            }
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
                    List<Path> imagePaths = parseImagePaths(imagePathField.getText());
                    return callGeminiApi(prompt, imagePaths);
                }
            };

            apiCallTask.setOnSucceeded(e -> {
                progressIndicator.setVisible(false);
                responseArea.setText(parseResponse(apiCallTask.getValue()));
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
    
    private List<Model> listModels() throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + API_KEY;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to list models: " + response.body());
        }

        return parseModelsResponse(response.body());
    }

    private List<Model> parseModelsResponse(String jsonResponse) {
        List<Model> models = new ArrayList<>();
        JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
        JsonArray modelsArray = root.getAsJsonArray("models");
        for (JsonElement modelElement : modelsArray) {
            JsonObject modelObject = modelElement.getAsJsonObject();
            String name = modelObject.get("name").getAsString().replace("models/", "");
            String description = "";
            if (modelObject.has("description")) {
                JsonElement descriptionElement = modelObject.get("description");
                if (descriptionElement != null && !descriptionElement.isJsonNull()) {
                    description = descriptionElement.getAsString();
                }
            }
            String category = "Other";
            if (name.contains("flash")) {
                category = "Flash";
            } else if (name.contains("pro")) {
                category = "Pro";
            }
            models.add(new Model(name, description, category));
        }
        return models;
    }


    private String callGeminiApi(String text) throws Exception {
        return callGeminiApi(text, List.of());
    }

    private String callGeminiApi(String text, List<Path> imagePaths) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
            + modelName + ":generateContent?key=" + API_KEY;
        String jsonBody = createJsonBody(text, imagePaths);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Request failed! Status code: " + response.statusCode() + " " + response.body());
        }

        return response.body();
    }

    private String createJsonBody(String text, List<Path> imagePaths) throws Exception {
        JsonArray parts = new JsonArray();
        for (Path imagePath : imagePaths) {
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mime_type", detectMimeType(imagePath));
            inlineData.addProperty("data", encodeBase64(imagePath));

            JsonObject imagePart = new JsonObject();
            imagePart.add("inline_data", inlineData);
            parts.add(imagePart);
        }

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", text);
        parts.add(textPart);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject root = new JsonObject();
        root.add("contents", contents);

        return gson.toJson(root);
    }

    private List<Path> parseImagePaths(String rawPaths) throws Exception {
        if (rawPaths == null || rawPaths.trim().isEmpty()) {
            return List.of();
        }
        List<Path> results = new ArrayList<>();
        String[] entries = rawPaths.split("[;\\n]+");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path path = Paths.get(trimmed);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Image path not found: " + trimmed);
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    List<Path> images = stream
                            .filter(Files::isRegularFile)
                            .filter(this::isImageFile)
                            .sorted()
                            .collect(Collectors.toList());
                    results.addAll(images);
                }
            } else if (isImageFile(path)) {
                results.add(path);
            } else {
                throw new IllegalArgumentException("Unsupported image type: " + trimmed);
            }
        }
        return results;
    }

    private boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    private String detectMimeType(Path path) throws Exception {
        String contentType = Files.probeContentType(path);
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "application/octet-stream";
    }

    private String encodeBase64(Path path) throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
    }

    private String parseResponse(String jsonResponse) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            return root.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage() + "\nRaw response:\n" + jsonResponse;
        }
    }
    
    public static class Model {
        private final String name;
        private final String description;
        private final String category;

        public Model(String name, String description, String category) {
            this.name = name;
            this.description = description;
            this.category = category;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getCategory() {
            return category;
        }
    }

    public static class Prompt {
        private int id;
        private String promptText;
        private String category;

        public Prompt(int id, String promptText, String category) {
            this.id = id;
            this.promptText = promptText;
            this.category = category;
        }

        public int getId() {
            return id;
        }

        public String getPromptText() {
            return promptText;
        }

        public void setPromptText(String promptText) {
            this.promptText = promptText;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public void setId(int id) {
            this.id = id;
        }
    }
}
