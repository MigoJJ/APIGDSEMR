package key.emr.ittia.data;

import key.emr.ittia.model.Prompt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DEFAULT_URL = "jdbc:sqlite:prompts.db";
    private final String url;

    public DatabaseManager() {
        this(DEFAULT_URL);
    }

    public DatabaseManager(String url) {
        this.url = url;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS prompts (\n" +
                " id integer PRIMARY KEY,\n" +
                " prompt text NOT NULL,\n" +
                " category text\n" +
                ");";

        try (Connection conn = this.connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Error creating prompts table", e);
        }
    }

    public List<Prompt> loadPrompts() {
        String sql = "SELECT id, prompt, category FROM prompts";
        List<Prompt> prompts = new ArrayList<>();

        try (Connection conn = this.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                prompts.add(new Prompt(
                        rs.getInt("id"),
                        rs.getString("prompt"),
                        rs.getString("category")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error loading prompts", e);
        }
        return prompts;
    }

    public void addPrompt(Prompt prompt) {
        String sql = "INSERT INTO prompts(prompt, category) VALUES(?,?)";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, prompt.getPromptText());
            pstmt.setString(2, prompt.getCategory());
            pstmt.executeUpdate();

            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                prompt.setId(generatedKeys.getInt(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error adding prompt", e);
        }
    }

    public void deletePrompt(Prompt prompt) {
        String sql = "DELETE FROM prompts WHERE id = ?";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, prompt.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error deleting prompt", e);
        }
    }

    public void updatePrompt(Prompt prompt) {
        String sql = "UPDATE prompts SET prompt = ? , " +
                "category = ? " +
                "WHERE id = ?";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prompt.getPromptText());
            pstmt.setString(2, prompt.getCategory());
            pstmt.setInt(3, prompt.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error updating prompt", e);
        }
    }
}
