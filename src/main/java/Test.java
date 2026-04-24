import java.sql.Connection;
import java.sql.Statement;

public class Test {
    public static void main(String[] args) throws Exception {

        InitDB.main(null);

        
        Connection conn = DB.connect();
        Statement stmt = conn.createStatement();

        stmt.execute("DELETE FROM contacts");
        stmt.execute("DELETE FROM groups");
        stmt.execute("DELETE FROM group_members");
        stmt.execute("DELETE FROM messages");
        stmt.execute("DELETE FROM files");

        stmt.close();
        conn.close();

        // Contacts
        Contact.addContact(1, "Alice");
        Contact.addContact(2, "Bob");
        Contact.getContacts();

        // Messages
        Message.insertMessage(1, 2, "Hello");
        Message.insertMessage(2, 1, "Salut");
        Message.getConversation(1, 2);

        // Groupes
        Groupe.createGroup(-1, "Groupe1");
        Groupe.addUserToGroup(-1, 1);
        Groupe.addUserToGroup(-1, 2);
        Groupe.getGroupMembers(-1);
    }
}