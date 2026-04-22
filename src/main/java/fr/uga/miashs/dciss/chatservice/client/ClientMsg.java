package fr.uga.miashs.dciss.chatservice.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer; // Import issu de la branche main
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import fr.uga.miashs.dciss.chatservice.common.Packet;
import static fr.uga.miashs.dciss.chatservice.common.MessageType.*;

/**
 * Manages the connection to a ServerMsg. Method startSession() is used to
 * establish the connection. Then messages can be send by a call to sendPacket.
 * The reception is done asynchronously (internally by the method receiveLoop())
 * and the reception of a message is notified to MessagesListeners. To register
 * a MessageListener, the method addMessageListener has to be called. Session
 * are closed thanks to the method closeSession().
 */
public class ClientMsg {

	private String serverAddress;
	private int serverPort;

	private Socket s;
	private DataOutputStream dos;
	private DataInputStream dis;

	private int identifier;

	private List<MessageListener> mListeners;
	private List<ConnectionListener> cListeners;
	
	private LocalHistoryManager history = new LocalHistoryManager();

	public ClientMsg(int id, String address, int port) {
		if (id < 0)
			throw new IllegalArgumentException("id must not be less than 0");
		if (port <= 0)
			throw new IllegalArgumentException("Server port must be greater than 0");
		serverAddress = address;
		serverPort = port;
		identifier = id;
		mListeners = new ArrayList<>();
		cListeners = new ArrayList<>();
	}

	public ClientMsg(String address, int port) {
		this(0, address, port);
	}

	public void addMessageListener(MessageListener l) {
		if (l != null)
			mListeners.add(l);
	}

	protected void notifyMessageListeners(Packet p) {
		mListeners.forEach(x -> x.messageReceived(p));
	}

	public void addConnectionListener(ConnectionListener l) {
		if (l != null)
			cListeners.add(l);
	}

	protected void notifyConnectionListeners(boolean active) {
		cListeners.forEach(x -> x.connectionEvent(active));
	}

	public int getIdentifier() {
		return identifier;
	}

	public void startSession() throws UnknownHostException {
		if (s == null || s.isClosed()) {
			try {
				s = new Socket(serverAddress, serverPort);
				dos = new DataOutputStream(s.getOutputStream());
				dis = new DataInputStream(s.getInputStream());
				dos.writeInt(identifier);
				dos.flush();
				if (identifier == 0) {
					identifier = dis.readInt();
				}
				// start the receive loop
				new Thread(() -> receiveLoop()).start();
				notifyConnectionListeners(true);
			} catch (IOException e) {
				e.printStackTrace();
				// error, close session
				closeSession();
			}
		}
	}

	public void sendPacket(int destId, byte[] data) {
		try {
			synchronized (dos) {
				dos.writeInt(destId);
				dos.writeInt(data.length);
				dos.write(data);
				dos.flush();
			}
		} catch (IOException e) {
			// error, connection closed
			closeSession();
		}
	}

	/**
	 * Start the receive loop. Has to be called only once.
	 */
	private void receiveLoop() {
		try {
			while (s != null && !s.isClosed()) {

				int sender = dis.readInt();
				int dest = dis.readInt();
				int length = dis.readInt();
				byte[] data = new byte[length];
				dis.readFully(data);
				
				// ---interception pour la BDD ---
				// pour transformer les octets en string
				String texteRecu = new String(data, StandardCharsets.UTF_8);
				
				//on envoie le message dans la classe sauvegarde (de LocalHistoryManager) 
				if (history != null) {
					history.saveMessage(sender, dest, texteRecu);
				}
				
				notifyMessageListeners(new Packet(sender, dest, data));

			}
		} catch (IOException e) {
			// error, connection closed
		}
		closeSession();
	}

	public void closeSession() {
		try {
			if (s != null)
				s.close();
		} catch (IOException e) {
		}
		s = null;
		notifyConnectionListeners(false);
	}

	// --- Gestion des Groupes ---
	// 客户端向服务器发送一个“创建群组” de l'amie
	public void requestCreateGroup(int[] memberIds) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		//在内存里准备一个字节缓冲区，把你要发送的数据先装进去
		DataOutputStream dos = new DataOutputStream(bos);
		//方便按类型往 bos 里写数据，比如写 byte、写 int

		dos.writeByte(CREATE_GROUP);//命令的 type
		dos.writeInt(memberIds.length);//成员数量
		for (int id: memberIds) {
			dos.writeInt(id);
		}
		//[type=1][nb=2][1][3]

		dos.flush();//把 DataOutputStream 里还没真正写进 bos 的内容全部推过去
		sendPacket(0,bos.toByteArray());
		//bos.toByteArray()把前面写进去的所有数据取出来，变成一个byte[] 就是最后的data
		//0 表示发给server
	}

	public void requestDeleteGroup(int groupId) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(SUPPRIME_GROUP);
		dos.writeInt(groupId);
		dos.flush();
		sendPacket(0,bos.toByteArray());
	}

	public void requestAddMember(int groupId, int userId) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(AJOUT_MEMBRE);
		dos.writeInt(groupId);
		dos.writeInt(userId);
		dos.flush();
		sendPacket(0,bos.toByteArray());
	}
	
	public void requestRemoveMember(int groupId, int userId) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(SUPPRIME_MEMBRE);
		dos.writeInt(groupId);
		dos.writeInt(userId);
		dos.flush();
		sendPacket(0,bos.toByteArray());
	}

	public void requestLeaveGroup(int groupId) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(LEAVE_GROUP);
		dos.writeInt(groupId);
		dos.flush();
		sendPacket(0,bos.toByteArray());
	}

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		
		// MISE EN PLACE D'UN SERVEUR POUR TESTER LE CLIENT
		String host = args.length > 0 ? args[0] : "localhost";
		int id = args.length > 1 ? Integer.parseInt(args[1]) : 0; // 0 = nouvel utilisateur

		ClientMsg c = new ClientMsg(id, host,1666);
		
		// ---on allume la bdd ---
		c.history.connect();

		// add a dummy listener that print the content of message as a string
		c.addMessageListener(p -> {
			// 如果 srcId == 0 -> 这是服务器发来的通知(Actions serveur -> client)
			if (p.srcId == 0 && p.data != null && p.data.length >= 1) {
				ByteBuffer buf = ByteBuffer.wrap(p.data);
				byte type = buf.get();
				switch (type) {
					case NOTIF_GROUP_CREATED: {
						int gid = buf.getInt();
						System.out.println("✓ Groupe " + gid + " créé avec succès");
						break;
					}
					case NOTIF_MEMBER_ADDED: {
						int gid = buf.getInt();
						int uid = buf.getInt();
						System.out.println("✓ User " + uid + " ajouté au groupe " + gid);
						break;
					}
					case NOTIF_MEMBER_REMOVED: {
						int gid = buf.getInt();
						int uid = buf.getInt();
						System.out.println("✓ User " + uid + " retiré du groupe " + gid);
						break;
					}
					case NOTIF_GROUP_DELETED: {
						int gid = buf.getInt();
						System.out.println("✓ Groupe " + gid + " supprimé");
						break;
					}
					case NOTIF_ERROR: {
						int len = buf.getInt();
						byte[] msg = new byte[len];
						buf.get(msg);
						System.out.println("✗ ERREUR: " + new String(msg, StandardCharsets.UTF_8));
						break;
					}
					default:
						System.out.println("Notification inconnue: type=" + type);
				}
			} else {
				// Message normal (privé ou groupe)
				System.out.println(p.srcId + " says to " + p.destId + ": " + new String(p.data));
			}
		});

		// add a connection listener that exit application when connection closed
		c.addConnectionListener(active -> {
			if (!active)
				System.exit(0);
		});

		c.startSession();
		System.out.println("Vous êtes : " + c.getIdentifier());

		Scanner sc = new Scanner(System.in);
		String lu = null;
		
		while(!"\\quit".equals(lu)){
			try{
				System.out.println("A qui voulez vous écrire ? (ou commandes: /history, /new, /add, /rm, /del, /leave, /quit)");
				String ligne = sc.nextLine().trim(); //sc.nextLine() lis toute la ligne, trim() enlève les spaces
				if (ligne.isEmpty()) continue;
				
				if(ligne.startsWith("/")) { // si ça commence par / c'est une commande
					String[] parts = ligne.split("\\s+"); //split par espaces
					String cmd = parts[0];
					
					switch(cmd) {
						// --- FUSION : Ajout de la commande /history ---
						case "/history":
							c.history.afficherHistorique(c.getIdentifier());
							break;

						case "/new": {
							// Syntaxe: /new 2,3,4 (liste des membres séparée par virgules)
							if (parts.length < 2) {
								System.out.println("Usage: /new <id1,id2,...> ex: /new 2,3,4");
								break;
							}
							StringBuilder sb = new StringBuilder();
							for (int i = 1; i < parts.length; i++) {
								if (i > 1) sb.append(",");
								sb.append(parts[i]);
							}
							String [] ids = sb.toString().split(",");
							int[] members = new int [ids.length];
							for(int i = 0; i<ids.length;i++) 
								members [i] = Integer.parseInt(ids[i].trim());
							c.requestCreateGroup(members);
							System.out.println("Demande: créer groupe envoyée");
							break;
						}

						case "/del" : {
							int gid = Integer.parseInt(parts[1]);
							c.requestDeleteGroup(gid);
							System.out.println("Demande: supprimer le groupe " + gid + " envoyée");
							break;
						}

						case "/add" : {
							int gid = Integer.parseInt(parts[1]);
							String[] uids = parts[2].split(",");
							for(String uidSr: uids){
								int uid = Integer.parseInt(uidSr.trim());
								c.requestAddMember(gid,uid);
							}
							System.out.println("Demande d'ajout envoyée");
							break;
						}

						case "/rm" : {
							int gid = Integer.parseInt(parts[1]);
							int uid = Integer.parseInt(parts[2]);
							c.requestRemoveMember(gid,uid);
							System.out.println("Demande: retirer " + uid + " envoyée");
							break;
						}

						case "/leave" : {
							int gid = Integer.parseInt(parts[1]);
							c.requestLeaveGroup(gid);
							System.out.println("Demande: quitter le groupe envoyée");
							break;
						}

						case "/quit": {
							lu = "\\quit";
							break;
						}

						default : 
							System.out.println ("Commande inconnue");
					 } 
					 Thread.sleep(500); 
					} else if (ligne.equals("\\quit")) {
						lu = "\\quit";
					} else {
						// envoi de messages directs (code de groupe fusionné avec code bdd /history)
						int dest = Integer.parseInt(ligne);
						System.out.println("Votre message ? ");
						lu = sc.nextLine();
						if (!"\\quit".equals(lu)) {
							c.sendPacket(dest, lu.getBytes(StandardCharsets.UTF_8));
						}
					}
				} catch (Exception e) {
					System.out.println("Mauvais format: " + e.getMessage());
				}
		}
		c.closeSession();
	}
}
