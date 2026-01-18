package key.emr.ittia.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GeminiServiceTest {

    private final GeminiService geminiService = new GeminiService();

    @Test
    void parseResponse_ValidJson_ReturnsText() {
        String json = "{\n" +
                "  \"candidates\": [\n" +
                "    {\n" +
                "      \"content\": {\n" +
                "        \"parts\": [\n" +
                "          {\n" +
                "            \"text\": \"Hello world\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        String result = geminiService.parseResponse(json);
        Assertions.assertEquals("Hello world", result);
    }

    @Test
    void parseResponse_InvalidJson_ReturnsOriginalOrError() {
        String invalidJson = "{ invalid }";
        String result = geminiService.parseResponse(invalidJson);
        Assertions.assertTrue(result.startsWith("Error parsing response"));
    }

    @Test
    void parseImagePaths_ValidPaths_ReturnsPaths(@TempDir Path tempDir) throws Exception {
        Path img1 = tempDir.resolve("image1.png");
        Files.createFile(img1);
        Path img2 = tempDir.resolve("image2.jpg");
        Files.createFile(img2);

        String input = img1.toAbsolutePath() + "; " + img2.toAbsolutePath();
        List<Path> paths = geminiService.parseImagePaths(input);

        Assertions.assertEquals(2, paths.size());
        Assertions.assertTrue(paths.contains(img1));
        Assertions.assertTrue(paths.contains(img2));
    }
    
    @Test
    void parseImagePaths_Directory_ReturnsImagesInDirectory(@TempDir Path tempDir) throws Exception {
        Path img1 = tempDir.resolve("image1.png");
        Files.createFile(img1);
        Path txt1 = tempDir.resolve("text.txt");
        Files.createFile(txt1);

        String input = tempDir.toAbsolutePath().toString();
        List<Path> paths = geminiService.parseImagePaths(input);

        Assertions.assertEquals(1, paths.size());
        Assertions.assertEquals(img1, paths.get(0));
    }

    @Test
    void parseImagePaths_NonExistentFile_ThrowsException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            geminiService.parseImagePaths("non_existent_file.png");
        });
    }
}
