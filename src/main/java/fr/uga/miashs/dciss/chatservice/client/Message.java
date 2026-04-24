package fr.uga.miashs.dciss.chatservice.client;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Message {

    // Insérer un message dans la base
    public static void insertMessage(int senderId, int receiverId, String content) throws Exception {
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
    public static void getMessages(int receiverId) throws Exception {
        try {
            Connection conn = DB.connect();

            String sql = "SELECT * FROM messages WHERE receiver_id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, receiverId);

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
}