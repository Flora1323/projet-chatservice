
import java.sql.*;

public class Contact {

    public static void addContact(int id, String nickname) {
        try {
            Connection conn = DB.connect();

            String sql = "INSERT INTO contacts(id, nickname) VALUES(?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ps.setString(2, nickname);

            ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void getContacts() {
        try {
            Connection conn = DB.connect();

            String sql = "SELECT * FROM contacts";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                System.out.println(
                    rs.getInt("id") + " - " +
                    rs.getString("nickname")
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