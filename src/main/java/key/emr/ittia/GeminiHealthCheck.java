package key.emr.ittia;

import key.emr.ittia.model.Model;
import key.emr.ittia.service.AiService;
import key.emr.ittia.service.GeminiService;
import key.emr.ittia.service.OpenAiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GeminiHealthCheck {

    private GeminiHealthCheck() {
    }

    public static void main(String[] args) {
        List<AiService> services = resolveServices(args);
        boolean anyFailure = false;
        boolean anyChecked = false;

        for (AiService service : services) {
            try {
                List<Model> models = service.listModels();
                anyChecked = true;
                System.out.println(service.getProviderName() + " API health check: OK");
                System.out.println("Models available: " + models.size());
                if (!models.isEmpty()) {
                    System.out.println("First model: " + models.get(0).getName());
                }
            } catch (Exception e) {
                if (isMissingApiKeyError(e)) {
                    System.out.println(service.getProviderName() + " API health check: SKIPPED");
                    System.out.println(e.getMessage());
                } else {
                    anyFailure = true;
                    System.err.println(service.getProviderName() + " API health check: FAILED");
                    System.err.println(e.getMessage());
                }
            }
        }

        if (anyFailure) {
            System.exit(1);
        }

        if (!anyChecked) {
            System.out.println("No providers were checked because required API keys are not configured.");
        }
    }

    private static List<AiService> resolveServices(String[] args) {
        String requestedProvider = firstNonBlank(
                firstArg(args),
                System.getProperty("healthCheckProvider"),
                System.getenv("HEALTHCHECK_PROVIDER"),
                "all"
        ).toLowerCase(Locale.ROOT);

        List<AiService> services = new ArrayList<>();
        switch (requestedProvider) {
            case "gemini":
                services.add(new GeminiService());
                break;
            case "openai":
                services.add(new OpenAiService());
                break;
            case "all":
                services.add(new GeminiService());
                services.add(new OpenAiService());
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported provider: " + requestedProvider + ". Use gemini, openai, or all."
                );
        }
        return services;
    }

    private static String firstArg(String[] args) {
        return args.length > 0 ? args[0] : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static boolean isMissingApiKeyError(Exception e) {
        return e instanceof IllegalStateException
                && e.getMessage() != null
                && e.getMessage().startsWith("Missing API key. Set ");
    }
}
