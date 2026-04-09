package key.emr.ittia;

import key.emr.ittia.model.Model;
import key.emr.ittia.service.AiService;
import key.emr.ittia.service.GeminiService;
import key.emr.ittia.service.OpenAiService;

import java.util.List;

public final class GeminiHealthCheck {

    private GeminiHealthCheck() {
    }

    public static void main(String[] args) {
        List<AiService> services = List.of(new GeminiService(), new OpenAiService());
        boolean anyFailure = false;

        for (AiService service : services) {
            try {
                List<Model> models = service.listModels();
                System.out.println(service.getProviderName() + " API health check: OK");
                System.out.println("Models available: " + models.size());
                if (!models.isEmpty()) {
                    System.out.println("First model: " + models.get(0).getName());
                }
            } catch (Exception e) {
                anyFailure = true;
                System.err.println(service.getProviderName() + " API health check: FAILED");
                System.err.println(e.getMessage());
            }
        }

        if (anyFailure) {
            System.exit(1);
        }
    }
}
