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

    private static final String URL = "jdbc:sqlite:prompts.db";

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
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
            System.err.println("Error creating table: " + e.getMessage());
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
            System.out.println(e.getMessage());
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
            System.out.println(e.getMessage());
        }
    }

    public void deletePrompt(Prompt prompt) {
        String sql = "DELETE FROM prompts WHERE id = ?";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, prompt.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
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
            System.out.println(e.getMessage());
        }
    }
}
