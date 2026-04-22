
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.DriverManager;

public class DB {
    private static final String URL = "jdbc:sqlite:client.db";

    public static Connection connect() throws SQLException {
        try {
            // Cette ligne force Java à charger le pilote SQLite
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Le pilote SQLite est introuvable dans le classpath.");
            e.printStackTrace();
        }
        return DriverManager.getConnection(URL);
    }

}