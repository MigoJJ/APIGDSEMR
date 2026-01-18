package key.emr.ittia.model;

public class Model {
    private final String name;
    private final String description;
    private final String category;

    public Model(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }
}
