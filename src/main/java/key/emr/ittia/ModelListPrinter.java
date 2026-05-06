package key.emr.ittia;

import key.emr.ittia.model.Model;
import key.emr.ittia.service.AiService;
import key.emr.ittia.service.GeminiService;
import key.emr.ittia.service.OpenAiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModelListPrinter {

    private ModelListPrinter() {
    }

    public static void main(String[] args) {
        List<AiService> services = resolveServices(args);
        boolean anyFailure = false;
        boolean anyListed = false;

        for (AiService service : services) {
            try {
                List<Model> models = service.listModels();
                anyListed = true;
                System.out.println(service.getProviderName() + " available LLM models (" + models.size() + "):");
                for (Model model : models) {
                    System.out.println("- " + model.getName());
                }
                System.out.println();
            } catch (Exception e) {
                if (isMissingApiKeyError(e)) {
                    System.out.println(service.getProviderName() + " available LLM models: SKIPPED");
                    System.out.println(e.getMessage());
                    System.out.println();
                } else {
                    anyFailure = true;
                    System.err.println(service.getProviderName() + " model list: FAILED");
                    System.err.println(e.getMessage());
                }
            }
        }

        if (anyFailure) {
            System.exit(1);
        }

        if (!anyListed) {
            System.out.println("No model list was fetched because required API keys are not configured.");
        }
    }

    private static List<AiService> resolveServices(String[] args) {
        String requestedProvider = firstNonBlank(
                firstArg(args),
                System.getProperty("modelListProvider"),
                System.getenv("MODEL_LIST_PROVIDER"),
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
