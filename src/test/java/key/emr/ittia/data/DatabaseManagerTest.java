package key.emr.ittia.data;

import key.emr.ittia.model.Prompt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

class DatabaseManagerTest {

    private DatabaseManager dbManager;
    private final String DB_FILE = "prompts.db";

    @BeforeEach
    void setUp() {
        // Ensure we start with a fresh DB
        File db = new File(DB_FILE);
        if (db.exists()) {
            db.delete();
        }
        dbManager = new DatabaseManager();
        dbManager.createTable();
    }

    @AfterEach
    void tearDown() {
        // Clean up
        File db = new File(DB_FILE);
        if (db.exists()) {
            db.delete();
        }
    }

    @Test
    void addAndLoadPrompt() {
        Prompt p = new Prompt(0, "Test Prompt", "Test Category");
        dbManager.addPrompt(p);

        List<Prompt> prompts = dbManager.loadPrompts();
        Assertions.assertEquals(1, prompts.size());
        Assertions.assertEquals("Test Prompt", prompts.get(0).getPromptText());
        Assertions.assertEquals("Test Category", prompts.get(0).getCategory());
        // Verify ID was auto-generated
        Assertions.assertNotEquals(0, prompts.get(0).getId());
    }

    @Test
    void updatePrompt() {
        Prompt p = new Prompt(0, "Original Text", "Original Cat");
        dbManager.addPrompt(p);

        // p.id should be updated by addPrompt (if implemented correctly to set ID)
        // Check if ID is set in the object passed to addPrompt?
        // Looking at DatabaseManager code: yes, it sets prompt.setId(generatedKeys.getInt(1));

        p.setPromptText("Updated Text");
        dbManager.updatePrompt(p);

        List<Prompt> prompts = dbManager.loadPrompts();
        Assertions.assertEquals(1, prompts.size());
        Assertions.assertEquals("Updated Text", prompts.get(0).getPromptText());
    }

    @Test
    void deletePrompt() {
        Prompt p = new Prompt(0, " To Delete", "Cat");
        dbManager.addPrompt(p);

        List<Prompt> before = dbManager.loadPrompts();
        Assertions.assertEquals(1, before.size());

        dbManager.deletePrompt(p);

        List<Prompt> after = dbManager.loadPrompts();
        Assertions.assertEquals(0, after.size());
    }
}
