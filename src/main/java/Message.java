import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Message {

    // Insérer un message dans la base
    public static void insertMessage(int senderId, int receiverId, String content) throws Exception {
        Connection conn = DB.connect();

        String sql = "INSERT INTO messages(sender_id, receiver_id, content) VALUES(?,?,?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, senderId);     // id expéditeur
        ps.setInt(2, receiverId);   // id destinataire
        ps.setString(3, content);   // contenu du message

        ps.executeUpdate();
        conn.close();
    }

    // Lire les messages pour un utilisateur ou groupe
    public static void getMessages(int receiverId) throws Exception {
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

        conn.close();
    }

    // Sauvegarder un fichier (juste le chemin)
    public static void saveFile(int messageId, String path) throws Exception {
        Connection conn = DB.connect();

        String sql = "INSERT INTO files(message_id, file_path) VALUES(?,?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, messageId);  // id du message lié
        ps.setString(2, path);    // chemin du fichier

        ps.executeUpdate();
        conn.close();
    }
}