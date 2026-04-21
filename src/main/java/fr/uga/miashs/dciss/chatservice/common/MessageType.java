package fr.uga.miashs.dciss.chatservice.common;

public final class MessageType {
    private MessageType() {

    }
    public static final byte CREATE_GROUP = 1;
    public static final byte SUPPRIME_GROUP = 2;
    public static final byte AJOUT_MEMBRE = 3;
    public static final byte SUPPRIME_MEMBRE = 4;
    public static final byte LEAVE_GROUP = 5;
}
