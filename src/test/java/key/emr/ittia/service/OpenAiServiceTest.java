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
}
