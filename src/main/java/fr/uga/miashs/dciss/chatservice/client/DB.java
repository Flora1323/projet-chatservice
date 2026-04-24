package fr.uga.miashs.dciss.chatservice.client;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DB {
    private static final String URL = "jdbc:sqlite:client.db";

    public static Connection connect() throws Exception {
        Class.forName("org.sqlite.JDBC"); // ok si Maven bug
        return DriverManager.getConnection(URL);
    }
}