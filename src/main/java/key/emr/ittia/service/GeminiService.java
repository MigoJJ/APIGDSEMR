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
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
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
                .timeout(REQUEST_TIMEOUT)
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

        int maxRetries = 3;
        int retryDelayMs = 2000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                return parseResponse(response.body());
            }

            String errorMsg = extractErrorMessage(response.body());
            if ((statusCode == 429 || statusCode == 503) && attempt < maxRetries) {
                Thread.sleep(retryDelayMs);
                retryDelayMs *= 2; // Exponential backoff
                continue;
            }

            throw new RuntimeException("Gemini API Error (" + statusCode + "): " + errorMsg);
        }
        throw new RuntimeException("Max retries exceeded for Gemini API");
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

    List<Model> parseModelsResponse(String jsonResponse) {
        List<Model> models = new ArrayList<>();
        JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
        JsonArray modelsArray = root.getAsJsonArray("models");
        for (JsonElement modelElement : modelsArray) {
            JsonObject modelObject = modelElement.getAsJsonObject();
            if (!supportsGenerateContent(modelObject)) {
                continue;
            }
            String name = modelObject.get("name").getAsString().replace("models/", "");
            
            String category = "LLM";
            String description = "구글의 대규모 언어 모델(LLM)입니다.";

            if (name.contains("deep-research")) {
                description = "연구/분석 특화: 수천 페이지 분량의 자료 분석 및 고도의 논리";
            } else if (name.contains("robotics-er")) {
                description = "물리적 추론/로봇 제어: 하드웨어 제어 및 물리 법칙 이해도 높음";
            } else if (name.contains("computer-use")) {
                description = "GUI 상호작용: 화면을 보고 마우스/키보드 직접 조작 가능";
            } else if (name.contains("lyria")) {
                description = "오디오/음악 생성: 전문적인 작곡 및 오디오 편집 기능";
            } else if (name.contains("nano-banana")) {
                description = "고효율 온디바이스 최적화: 기기 내에서 강력한 성능 발휘";
            } else if (name.contains("gemini-3.1-flash-lite")) {
                description = "3.0의 최적화 버전: 초경량 및 초고속 응답 속도";
            } else if (name.contains("gemini-3.1")) {
                description = "3.0의 최적화 버전: 고도의 추론 능력과 대규모 컨텍스트 처리 특화";
            } else if (name.contains("gemini-3")) {
                description = "차세대 주력 모델: 이전 세대 대비 압도적인 추론 속도 및 정확도";
            } else if (name.contains("gemini-2.5")) {
                description = "현시점 표준 모델: 가장 균형 잡힌 성능과 안정적인 API 지원";
            } else if (name.contains("gemini-2.0")) {
                description = "레거시 안정화 모델: 매우 저렴한 토큰 비용 및 빠른 처리";
            } else if (name.contains("gemma-4-31b")) {
                description = "고성능 지시어 이행 모델: 오픈 모델 중 최상위권의 대화 및 코딩 능력";
                category = "Open Model";
            } else if (name.contains("gemma-3")) {
                description = "다양한 체급의 오픈 모델: 커스텀 튜닝 가능, 데이터 보안 유지 용이";
                category = "Open Model";
            } else if (name.contains("tts")) {
                description = "음성 특화: 인간에 가까운 음성 합성";
            } else if (name.contains("image")) {
                description = "시각 특화: 이미지 분석 및 처리";
            } else if (name.endsWith("-latest")) {
                description = "최신 모델 자동 연결: 코드 수정 없이 항상 최신 최적화 모델 사용";
            } else if (name.contains("embedding")) {
                category = "Embedding";
                description = "텍스트를 벡터로 변환하는 임베딩 모델입니다.";
            } else if (name.contains("flash")) {
                description = "빠르고 효율적인 처리를 위해 최적화된 경량 모델입니다.";
            } else if (name.contains("pro")) {
                description = "복잡한 추론과 다양한 작업에 적합한 고성능 모델입니다.";
            }

            models.add(new Model(name, description, category, PROVIDER_NAME));
        }
        return models;
    }

    private boolean supportsGenerateContent(JsonObject modelObject) {
        if (!modelObject.has("supportedGenerationMethods")) {
            return true;
        }
        JsonArray methods = modelObject.getAsJsonArray("supportedGenerationMethods");
        for (JsonElement method : methods) {
            if ("generateContent".equals(method.getAsString())) {
                return true;
            }
        }
        return false;
    }
}
