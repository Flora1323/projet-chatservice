package fr.uga.miashs.dciss.chatservice.client;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Message {

    // Insérer un message dans la base
    public static void insertMessage(int senderId, int receiverId, String content) {
        try {
            Connection conn = DB.connect();

            String sql = "INSERT INTO messages(sender_id, receiver_id, content) VALUES(?,?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setString(3, content);

            ps.executeUpdate();
            ps.close(); 
            conn.close();
        } catch (Exception e) {
            e.printStackTrace(); 
        }
    }
    
    // Lire les messages pour un utilisateur ou groupe
 //MÉTHODE MODIFIÉE : On renvoie maintenant une List<String>
    // pour que celui qui appelle la méthode puisse faire ce qu'il veut avec (afficher, sauvegarder dans un fichier, etc.)
    public static List<Archive> getMessages(int userId) throws Exception {
        List<Archive> resultats = new ArrayList<>();
        try {
            Connection conn = DB.connect();

            // SQL Amélioré : On cherche les messages où l'utilisateur est l'expéditeur OU le destinataire
            String sql = "SELECT * FROM messages WHERE receiver_id = ? OR sender_id = ? ORDER BY id ASC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setInt(2, userId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                // On crée la ligne de texte
            	// On récupère les deux infos séparément
                int expediteur = rs.getInt("sender_id");
                String texte = rs.getString("content");
                
                // Au lieu de l'afficher, on l'ajoute à la liste
                resultats.add(new Archive(expediteur, texte));
            }

            rs.close();   
            ps.close();   
            conn.close();
        } catch (Exception e) {
            e.printStackTrace(); 
        }
        return resultats; // On renvoie la liste à celui qui a appelé la méthode
    }

    public static List<Archive> getMessagesSpecifiques(int monId, int destId) {
        List<Archive> messages = new ArrayList<>();
        String query;
        
        // Si destId < 0, c'est un groupe. Sinon, c'est du privé.
        if (destId < 0) {
            query = "SELECT sender_id, content FROM messages WHERE receiver_id = ? ORDER BY timestamp ASC";
        } else {
            query = "SELECT sender_id, content FROM messages " +
                    "WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                    "ORDER BY timestamp ASC";
        }

        try (Connection conn = DB.connect();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            if (destId < 0) {
                pstmt.setInt(1, destId);
            } else {
                pstmt.setInt(1, monId);
                pstmt.setInt(2, destId);
                pstmt.setInt(3, destId);
                pstmt.setInt(4, monId);
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
            	int expediteur = rs.getInt("sender_id");
            	String texte = rs.getString("content");

            	messages.add(new Archive(expediteur, texte));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return messages;
    }
    

    // Sauvegarder un fichier (juste le chemin)
    public static void saveFile(int messageId, String path) throws Exception {
        try {
            Connection conn = DB.connect();
            String sql = "INSERT INTO files(message_id, file_path) VALUES(?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, messageId);
            ps.setString(2, path);

            ps.executeUpdate();
            ps.close(); 
            conn.close();
        } catch (Exception e) {
            e.printStackTrace(); 
        }
    }


    public static void getConversation(int user1, int user2) {
        try {
            Connection conn = DB.connect();

            String sql = "SELECT * FROM messages WHERE " +
                    "(sender_id = ? AND receiver_id = ?) OR " +
                    "(sender_id = ? AND receiver_id = ?) " +
                    "ORDER BY timestamp";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, user1);
            ps.setInt(2, user2);
            ps.setInt(3, user2);
            ps.setInt(4, user1);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                System.out.println(
                    rs.getInt("sender_id") + " -> " +
                    rs.getInt("receiver_id") + " : " +
                    rs.getString("content")
                );
            }

            rs.close();
            ps.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}







