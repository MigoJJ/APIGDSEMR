package key.emr.ittia.model;

public class Prompt {
    private int id;
    private String promptText;
    private String category;

    public Prompt(int id, String promptText, String category) {
        this.id = id;
        this.promptText = promptText;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setId(int id) {
        this.id = id;
    }
}
