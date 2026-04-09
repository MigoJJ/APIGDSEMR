package key.emr.ittia.model;

public class Model {
    private final String name;
    private final String description;
    private final String category;
    private final String provider;

    public Model(String name, String description, String category, String provider) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.provider = provider;
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

    public String getProvider() {
        return provider;
    }
}
