package key.emr.ittia.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OpenAiServiceTest {

    private final OpenAiService openAiService = new OpenAiService();

    @Test
    void parseResponse_WithOutputText_ReturnsText() {
        String json = "{\n" +
                "  \"output_text\": \"OpenAI hello\"\n" +
                "}";

        String result = openAiService.parseResponse(json);
        Assertions.assertEquals("OpenAI hello", result);
    }

    @Test
    void parseResponse_WithMessageContent_ReturnsText() {
        String json = "{\n" +
                "  \"output\": [\n" +
                "    {\n" +
                "      \"type\": \"message\",\n" +
                "      \"content\": [\n" +
                "        {\n" +
                "          \"type\": \"output_text\",\n" +
                "          \"text\": \"Nested text\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        String result = openAiService.parseResponse(json);
        Assertions.assertEquals("Nested text", result);
    }

    @Test
    void parseModelsResponse_FiltersNonResponsesModels() {
        String json = "{\n" +
                "  \"data\": [\n" +
                "    {\"id\": \"gpt-5.1\"},\n" +
                "    {\"id\": \"text-embedding-3-small\"},\n" +
                "    {\"id\": \"gpt-image-1\"},\n" +
                "    {\"id\": \"tts-1\"},\n" +
                "    {\"id\": \"o4-mini\"}\n" +
                "  ]\n" +
                "}";

        java.util.List<key.emr.ittia.model.Model> models = openAiService.parseModelsResponse(json);

        Assertions.assertEquals(2, models.size());
        Assertions.assertEquals("gpt-5.1", models.get(0).getName());
        Assertions.assertEquals("o4-mini", models.get(1).getName());
    }
}
