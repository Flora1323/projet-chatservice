package fr.uga.miashs.dciss.chatservice.common;

public final class MessageType {
    private MessageType() {

    }
    public static final byte CREATE_GROUP = 1;
    public static final byte SUPPRIME_GROUP = 2;
    public static final byte AJOUT_MEMBRE = 3;
    public static final byte SUPPRIME_MEMBRE = 4;
    public static final byte LEAVE_GROUP = 5;

    // Serveur → Clients (Notification)
    public static final byte NOTIF_GROUP_CREATED  = 1;
    public static final byte NOTIF_MEMBER_ADDED   = 2;
    public static final byte NOTIF_MEMBER_REMOVED = 3;
    public static final byte NOTIF_GROUP_DELETED  = 4;
    public static final byte NOTIF_ERROR          = 5;

    //nickname
    public static final byte SET_NICKNAME = 6;
    public static final byte NOTIF_NICKNAME_CHANGED = 6;

    // indicateur de presence (en ligne / hors ligne)
    public static final byte NOTIF_USER_ONLINE = 7;
    public static final byte NOTIF_USER_OFFLINE = 8;
    public static final byte NOTIF_PRESENCE_SNAPSHOT = 9;

    // Client → Serveur : demande explicite d'un snapshot de présence
    // (réponse du serveur = NOTIF_PRESENCE_SNAPSHOT)
    public static final byte QUERY_PRESENCE = 10;
}
