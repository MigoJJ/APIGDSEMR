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
import java.util.Comparator;
import java.util.List;

public class OpenAiService implements AiService {

    private static final String API_KEY_ENV = "OPENAI_API_KEY";
    private static final String PROVIDER_NAME = "OpenAI";

    private final Gson gson = new Gson();
    private final HttpClient client;
    private final ImagePathService imagePathService;

    public OpenAiService() {
        this(new ImagePathService());
    }

    public OpenAiService(ImagePathService imagePathService) {
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
        HttpRequest request = baseRequest("https://api.openai.com/v1/models")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API Error (" + response.statusCode() + "): "
                    + extractErrorMessage(response.body()));
        }

        return parseModelsResponse(response.body());
    }

    @Override
    public String generateResponse(String modelName, String text, List<Path> imagePaths) throws Exception {
        HttpRequest request = baseRequest("https://api.openai.com/v1/responses")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createJsonBody(modelName, text, imagePaths)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API Error (" + response.statusCode() + "): "
                    + extractErrorMessage(response.body()));
        }

        return parseResponse(response.body());
    }

    public String parseResponse(String jsonResponse) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);

            if (root.has("error")) {
                return "API Error: " + extractErrorMessage(jsonResponse);
            }
            if (root.has("output_text") && !root.get("output_text").isJsonNull()) {
                return root.get("output_text").getAsString();
            }
            if (root.has("output")) {
                JsonArray output = root.getAsJsonArray("output");
                for (JsonElement outputItem : output) {
                    JsonObject message = outputItem.getAsJsonObject();
                    if (!message.has("content")) {
                        continue;
                    }
                    JsonArray content = message.getAsJsonArray("content");
                    for (JsonElement contentItem : content) {
                        JsonObject part = contentItem.getAsJsonObject();
                        if ("output_text".equals(part.get("type").getAsString()) && part.has("text")) {
                            return part.get("text").getAsString();
                        }
                    }
                }
            }
            return jsonResponse;
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage() + "\nRaw response:\n" + jsonResponse;
        }
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + requireApiKey());
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

    private String createJsonBody(String modelName, String text, List<Path> imagePaths) throws Exception {
        JsonArray content = new JsonArray();

        JsonObject textItem = new JsonObject();
        textItem.addProperty("type", "input_text");
        textItem.addProperty("text", text);
        content.add(textItem);

        for (Path imagePath : imagePaths) {
            JsonObject imageItem = new JsonObject();
            imageItem.addProperty("type", "input_image");
            imageItem.addProperty(
                    "image_url",
                    "data:" + imagePathService.detectMimeType(imagePath)
                            + ";base64," + imagePathService.encodeBase64(imagePath)
            );
            content.add(imageItem);
        }

        JsonObject input = new JsonObject();
        input.addProperty("role", "user");
        input.add("content", content);

        JsonArray inputArray = new JsonArray();
        inputArray.add(input);

        JsonObject root = new JsonObject();
        root.addProperty("model", modelName);
        root.add("input", inputArray);

        return gson.toJson(root);
    }

    private String extractErrorMessage(String jsonResponse) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            if (root.has("error")) {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return "Unknown error: " + jsonResponse;
    }

    private List<Model> parseModelsResponse(String jsonResponse) {
        JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
        JsonArray data = root.getAsJsonArray("data");
        List<Model> models = new ArrayList<>();
        for (JsonElement element : data) {
            JsonObject modelObject = element.getAsJsonObject();
            String modelId = modelObject.get("id").getAsString();
            models.add(new Model(
                    modelId,
                    describeModel(modelId),
                    categorizeModel(modelId),
                    PROVIDER_NAME
            ));
        }
        models.sort(Comparator.comparing(Model::getName));
        return models;
    }

    private String categorizeModel(String modelId) {
        if (modelId.contains("embedding")) {
            return "Embedding";
        }
        if (modelId.contains("image") || modelId.contains("dall")) {
            return "Image";
        }
        if (modelId.contains("tts") || modelId.contains("audio")) {
            return "Audio";
        }
        return "LLM";
    }

    private String describeModel(String modelId) {
        if (modelId.startsWith("gpt-5")) {
            return "OpenAI의 최신 범용 추론 계열 모델입니다.";
        }
        if (modelId.startsWith("o")) {
            return "복잡한 추론과 분석에 강한 OpenAI reasoning 모델입니다.";
        }
        if (modelId.contains("mini")) {
            return "가볍고 빠른 작업에 적합한 OpenAI 경량 모델입니다.";
        }
        if (modelId.contains("image") || modelId.contains("dall")) {
            return "이미지 생성 및 시각 작업에 적합한 모델입니다.";
        }
        if (modelId.contains("embedding")) {
            return "텍스트나 멀티모달 데이터를 벡터화하는 임베딩 모델입니다.";
        }
        return "OpenAI API에서 호출 가능한 모델입니다.";
    }
}
