package fr.uga.miashs.dciss.chatservice.client;
import java.sql.Connection;
import java.sql.Statement;

public class InitDB {
    public static void main(String[] args) throws Exception {
        Connection conn = DB.connect();
        Statement stmt = conn.createStatement();

        // contacts
        stmt.execute("CREATE TABLE IF NOT EXISTS contacts (" +
                "id INTEGER PRIMARY KEY, " +
                "nickname TEXT);");

        // groupes
        stmt.execute("CREATE TABLE IF NOT EXISTS groups (" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT);");
        
        stmt.execute("CREATE TABLE IF NOT EXISTS group_members (" +
                "group_id INTEGER, " +
                "user_id INTEGER, " +
                "PRIMARY KEY(group_id, user_id));");

        // messages
        stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender_id INTEGER, " +
                "receiver_id INTEGER, " +
                "content TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);");

        // fichiers
        stmt.execute("CREATE TABLE IF NOT EXISTS files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "message_id INTEGER, " +
                "file_path TEXT);");

        conn.close();
    }
}

