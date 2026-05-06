package key.emr.ittia.service;

import key.emr.ittia.model.Model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class DescriptionTest {
    @Test
    void testUpdatedDescriptions() {
        GeminiService service = new GeminiService();
        String json = "{\"models\": [" +
                "{\"name\": \"models/gemini-3.1-pro-preview\", \"supportedGenerationMethods\": [\"generateContent\"]}," +
                "{\"name\": \"models/deep-research-max-preview-04-2026\", \"supportedGenerationMethods\": [\"generateContent\"]}," +
                "{\"name\": \"models/gemma-4-31b-it\", \"supportedGenerationMethods\": [\"generateContent\"]}," +
                "{\"name\": \"models/gemini-3.1-flash-lite-preview\", \"supportedGenerationMethods\": [\"generateContent\"]}" +
                "]}";
        
        List<Model> models = service.parseModelsResponse(json);
        
        assertEquals("3.0의 최적화 버전: 고도의 추론 능력과 대규모 컨텍스트 처리 특화", models.get(0).getDescription());
        assertEquals("연구/분석 특화: 수천 페이지 분량의 자료 분석 및 고도의 논리", models.get(1).getDescription());
        assertEquals("고성능 지시어 이행 모델: 오픈 모델 중 최상위권의 대화 및 코딩 능력", models.get(2).getDescription());
        assertEquals("Open Model", models.get(2).getCategory());
        assertEquals("3.0의 최적화 버전: 초경량 및 초고속 응답 속도", models.get(3).getDescription());
    }
}
