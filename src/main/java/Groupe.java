import java.sql.*;

public class Groupe {

    // créer un groupe
    public static void createGroup(int id, String name) {
       try {
            Connection conn = DB.connect();

            String sql = "INSERT INTO groups(id, name) VALUES(?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ps.setString(2, name);

            ps.executeUpdate();

           ps.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	
	
	
    // ajouter un utilisateur dans un groupe
    public static void addUserToGroup(int groupId, int userId) {
        try {
           Connection conn = DB.connect();

           String sql = "INSERT INTO group_members(group_id, user_id) VALUES(?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, groupId);
            ps.setInt(2, userId);

           ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	
	

    // afficher les membres d’un groupe
    public static void getGroupMembers(int groupId) { 
        try {
            Connection conn = DB.connect();

            String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, groupId);

            ResultSet rs = ps.executeQuery();

            System.out.println("Membres du groupe " + groupId + " :");

            while (rs.next()) {
                System.out.println("User: " + rs.getInt("user_id"));
            }

            rs.close();
            ps.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
 // Afficher les groupes auxquels appartient un utilisateur 
    public static void getUserGroups(int userId) { 
        try {
            Connection conn = DB.connect();

            String sql = "SELECT group_id FROM group_members WHERE user_id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                System.out.println("Group: " + rs.getInt("group_id"));
            }

            rs.close();
            ps.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }





}