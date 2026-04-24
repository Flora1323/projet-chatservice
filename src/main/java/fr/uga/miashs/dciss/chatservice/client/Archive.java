package fr.uga.miashs.dciss.chatservice.client;

public class Archive {
    public int senderId;
    public String content;

    public Archive(int senderId, String content) {
        this.senderId = senderId;
        this.content = content;
    }
}
