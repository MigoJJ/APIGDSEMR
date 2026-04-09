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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GeminiService implements AiService {

    private static final String API_KEY_ENV = "GEMINI_API_KEY";
    private static final String PROVIDER_NAME = "Gemini";
    private final Gson gson = new Gson();
    private final HttpClient client;
    private final ImagePathService imagePathService;

    public GeminiService() {
        this(new ImagePathService());
    }

    public GeminiService(ImagePathService imagePathService) {
        this.imagePathService = imagePathService;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<Model> listModels() throws Exception {
        String apiKey = requireApiKey();
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = extractErrorMessage(response.body());
            throw new RuntimeException("Gemini API Error (" + response.statusCode() + "): " + errorMsg);
        }

        return parseModelsResponse(response.body());
    }

    private String extractErrorMessage(String jsonResponse) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            if (root.has("error")) {
                JsonObject error = root.getAsJsonObject("error");
                return error.get("message").getAsString();
            }
        } catch (Exception ignored) { }
        return "Unknown error: " + jsonResponse;
    }

    @Override
    public String generateResponse(String modelName, String text, List<Path> imagePaths) throws Exception {
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

        if (response.statusCode() != 200) {
            String errorMsg = extractErrorMessage(response.body());
            throw new RuntimeException("Gemini API Error (" + response.statusCode() + "): " + errorMsg);
        }

        return parseResponse(response.body());
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
            inlineData.addProperty("mime_type", imagePathService.detectMimeType(imagePath));
            inlineData.addProperty("data", imagePathService.encodeBase64(imagePath));

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

    public String parseResponse(String jsonResponse) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            
            if (root.has("error")) {
                return "API Error: " + extractErrorMessage(jsonResponse);
            }

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

            models.add(new Model(name, description, category, PROVIDER_NAME));
        }
        return models;
    }
}
