package fr.uga.miashs.dciss.chatservice.client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class LocalHistoryManager {

	private Connection conn = null;

    public void connect() {
        try {
            // "Trouve le SQlite JDBC driver et établit une connexion à la base de données chat_history.db"
            // si ça n'existe pas, il sera créé automatiquement
            String url = "jdbc:sqlite:chat_history.db";
            
            conn = DriverManager.getConnection(url);
            System.out.println("Connection to SQLite has been established.");
            
        } catch (SQLException e) {
            System.out.println("ERROR connecting to database: " + e.getMessage());
        }
    }
    //méthode temporaire pour simuler la sauvegarde d'un message dans la base de données
    public void saveMessage(int fromId, int toId, String text) {
        //juste un sop pour montrer que ça marche
        System.out.println("[DB-DEBUG] Saving to file: From " + fromId + " says: " + text);
        
        // doit être modifié par du SQL INSERT INTO messages (from_id, to_id, text) VALUES (?, ?, ?)
    }
}

