package fr.uga.miashs.dciss.chatservice.client;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Contact {
	private int id;
    private String nickname;

    public Contact(int id, String nickname) {
        this.id = id;
        this.nickname = nickname;
    }

    public int getId() { return id; }
    public String getNickname() { return nickname; }


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
    
    public static List<Contact> getAllContacts() {
        List<Contact> liste = new ArrayList<>();
        // Adapte le nom de la table et des colonnes selon ce que tu as créé ce matin
        String query = "SELECT id, nickname FROM contacts"; 
        
        try (Connection conn = DB.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
             
            while (rs.next()) {
                liste.add(new Contact(rs.getInt("id"), rs.getString("nickname")));
            }
        } catch (Exception e) {
            System.out.println("Erreur chargement contacts : " + e.getMessage());
        }
        return liste;
    }
 
     // ####################################
        // SAUVEGARDER OU METTRE À JOUR UN CONTACT
        // ####################################
        public static void sauvegarderContact(int id, String nickname) {
            String sql = "INSERT OR REPLACE INTO contacts (id, nickname) VALUES (?, ?)";
            
            try (Connection conn = DB.connect();
                 java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                 
                pstmt.setInt(1, id);
                pstmt.setString(2, nickname);
                pstmt.executeUpdate();
                
            } catch (Exception e) {
                System.out.println("Erreur de sauvegarde du contact : " + e.getMessage());
            }
        }  
    
}