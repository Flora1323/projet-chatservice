import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DB {
    private static final String URL = "jdbc:sqlite:client.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}
