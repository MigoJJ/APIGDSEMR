package key.emr.ittia.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import key.emr.ittia.model.Model;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeminiService {

    private static final String API_KEY_ENV = "GEMINI_API_KEY";
    private final Gson gson = new Gson();
    private final HttpClient client;

    public GeminiService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Model> listModels() throws Exception {
        String apiKey = requireApiKey();
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
        
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

    public String callGeminiApi(String modelName, String text, List<Path> imagePaths) throws Exception {
        String apiKey = requireApiKey();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
            + modelName + ":generateContent?key=" + apiKey;
        String jsonBody = createJsonBody(text, imagePaths);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    private String requireApiKey() {
        String apiKey = System.getenv(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Missing API key. Set " + API_KEY_ENV + " in your environment."
            );
        }
        return apiKey;
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

    public List<Path> parseImagePaths(String rawPaths) throws Exception {
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

    public String parseResponse(String jsonResponse) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            if (root.has("candidates")) {
                JsonArray candidates = root.getAsJsonArray("candidates");
                if (candidates.size() > 0) {
                     return candidates
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();
                }
            }
            return jsonResponse;
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage() + "\nRaw response:\n" + jsonResponse;
        }
    }

    private List<Model> parseModelsResponse(String jsonResponse) {
        List<Model> models = new ArrayList<>();
        JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
        JsonArray modelsArray = root.getAsJsonArray("models");
        for (JsonElement modelElement : modelsArray) {
            JsonObject modelObject = modelElement.getAsJsonObject();
            String name = modelObject.get("name").getAsString().replace("models/", "");
            
            String category = "LLM";
            String description = "구글의 대규모 언어 모델(LLM)입니다.";

            if (name.contains("flash")) {
                description = "빠르고 효율적인 처리를 위해 최적화된 경량 모델입니다.";
            } else if (name.contains("pro")) {
                description = "복잡한 추론과 다양한 작업에 적합한 고성능 모델입니다.";
            } else if (name.contains("ultra")) {
                description = "가장 복잡한 작업을 처리할 수 있는 최고 성능의 모델입니다.";
            } else if (name.contains("vision")) {
                description = "이미지 인식 및 처리에 특화된 모델입니다.";
            } else if (name.contains("embedding")) {
                category = "Embedding";
                description = "텍스트를 벡터로 변환하는 임베딩 모델입니다.";
            }

            models.add(new Model(name, description, category));
        }
        return models;
    }
}
