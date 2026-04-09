package key.emr.ittia.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImagePathService {

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

    public String detectMimeType(Path path) throws Exception {
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

    public String encodeBase64(Path path) throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
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
}
