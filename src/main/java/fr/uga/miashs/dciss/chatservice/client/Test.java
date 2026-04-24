package fr.uga.miashs.dciss.chatservice.client;
public class Test {
    public static void main(String[] args) throws Exception {

        InitDB.main(null); // 👈 crée les tables

        Message.insertMessage(1, 2, "Hello");
        Message.getMessages(2);
        Message.saveFile(1, "C:/files/test.txt");
    }
}