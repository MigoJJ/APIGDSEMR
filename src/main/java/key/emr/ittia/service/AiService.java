package key.emr.ittia.service;

import key.emr.ittia.model.Model;

import java.nio.file.Path;
import java.util.List;

public interface AiService {

    String getProviderName();

    List<Model> listModels() throws Exception;

    String generateResponse(String modelName, String text, List<Path> imagePaths) throws Exception;
}
