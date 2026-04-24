package fr.uga.miashs.dciss.chatservice.client;

import java.sql.Connection;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class LocalHistoryManager {

    //méthode pour la sauvegarde d'un message dans la base de données
    public void saveMessage(int fromId, int toId, String text) {
    	// On enregistre le message "en vrai" dans le fichier .db
    	try {
    	    Message.insertMessage(fromId, toId, text);
    	} catch (Exception e) {
    	    System.err.println("Erreur lors de l'enregistrement en BDD : " + e.getMessage());
    	}
        
        // doit être modifié par du SQL INSERT INTO messages (from_id, to_id, text) VALUES (?, ?, ?)
    }
    
    //méthode  pour l'affichage de l'historique des messages depuis la base de données
    public void afficherHistorique(int monId) {
    	//if (input.startsWith("/history")) {
    	    System.out.println("--- Historique des messages ---");
    	    try {
    	        // On va chercher les messages dans la BDD pour cet utilisateur
    	        List<String> archives = Message.getMessages(monId);
    	        
    	        if (archives.isEmpty()) {
		            System.out.println("Aucun message trouvé dans l'historique.");
		        } else {
		            for (String s : archives) {
		                System.out.println("[HISTORIQUE] " + s);
		            }
		        }
    	    } catch (Exception e) {
    	        System.out.println("Impossible de charger l'historique : " + e.getMessage());
    	    }
    	//}
}
}

