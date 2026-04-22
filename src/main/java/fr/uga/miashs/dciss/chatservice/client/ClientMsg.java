/*
 * Copyright (c) 2024.  Jerome David. Univ. Grenoble Alpes.
 * This file is part of DcissChatService.
 *
 * DcissChatService is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * DcissChatService is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */

package fr.uga.miashs.dciss.chatservice.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
	private final Map<Integer, String> nicknames = new ConcurrentHashMap<>();

	/**
	 * Create a client with an existing id, that will connect to the server at the
	 * given address and port
	 * 
	 * @param id      The client id
	 * @param address The server address or hostname
	 * @param port    The port number
	 */
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

	/**
	 * Create a client without id, the server will provide an id during the the
	 * session start
	 * 
	 * @param address The server address or hostname
	 * @param port    The port number
	 */
	public ClientMsg(String address, int port) {
		this(0, address, port);
	}

	/**
	 * Register a MessageListener to the client. It will be notified each time a
	 * message is received.
	 * 
	 * @param l
	 */
	public void addMessageListener(MessageListener l) {
		if (l != null)
			mListeners.add(l);
	}

	protected void notifyMessageListeners(Packet p) {
		mListeners.forEach(x -> x.messageReceived(p));
	}

	/**
	 * Register a ConnectionListener to the client. It will be notified if the
	 * connection start or ends.
	 * 
	 * @param l
	 */
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

	/**
	 * Method to be called to establish the connection.
	 * 
	 * @throws UnknownHostException
	 * @throws IOException
	 */
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

	/**
	 * Send a packet to the specified destination (etiher a userId or groupId)
	 * 
	 * @param destId the destinatiion id
	 * @param data   the data to be sent
	 */
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

	// 客户端向服务器发送一个“创建群组”的请求
	public void requestCreateGroup(String groupName, int[] memberIds) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		// 在内存里准备一个字节缓冲区，把你要发送的数据先装进去
		DataOutputStream dos = new DataOutputStream(bos);
		// 方便按类型往 bos 里写数据，比如写 byte、写 int

		dos.writeByte(CREATE_GROUP);// 命令的 type
		// On envoie d'abord le nom du groupe
		byte[] nameBytes = groupName.getBytes(StandardCharsets.UTF_8);
		dos.writeInt(nameBytes.length);
		dos.write(nameBytes);

		// Puis la liste des membres
		dos.writeInt(memberIds.length);
		for (int id : memberIds) {
			dos.writeInt(id);
		}
		// [type=1][nb=2][1][3]

		dos.flush();// 把 DataOutputStream 里还没真正写进 bos 的内容全部推过去
		sendPacket(0, bos.toByteArray());
		// bos.toByteArray()把前面写进去的所有数据取出来，变成一个byte[] 就是最后的data
		// 0 表示发给server
	}

	public void requestDeleteGroup(int groupId) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(SUPPRIME_GROUP);
		dos.writeInt(groupId);
		dos.flush();
		sendPacket(0, bos.toByteArray());
	}

	public void requestAddMember(int groupId, int userId) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(AJOUT_MEMBRE);
		dos.writeInt(groupId);
		dos.writeInt(userId);
		dos.flush();
		sendPacket(0, bos.toByteArray());
	}

	public void requestRemoveMember(int groupId, int userId) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(SUPPRIME_MEMBRE);
		dos.writeInt(groupId);
		dos.writeInt(userId);
		dos.flush();
		sendPacket(0, bos.toByteArray());
	}

	public void requestLeaveGroup(int groupId) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(LEAVE_GROUP);
		dos.writeInt(groupId);
		dos.flush();
		sendPacket(0, bos.toByteArray());
	}

	public void requestSetNickname(String nickname) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		byte[] nameBytes = nickname.getBytes(StandardCharsets.UTF_8);
		dos.writeByte(SET_NICKNAME);
		dos.writeInt(nameBytes.length);
		dos.write(nameBytes);
		dos.flush();
		sendPacket(0, bos.toByteArray());
	}

	public String displayName(int id) {
		return nicknames.getOrDefault(id, "User" + id);
	}
	
	public Map<Integer, String> getNicknamesMap() {
		return nicknames;
	}

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		// ClientMsg c = new ClientMsg("localhost", 1666);

		// MISE EN PLACE D'UN SERVEUR POUR TESTER LE CLIENT
		String host = args.length > 0 ? args[0] : "localhost";
		int id = args.length > 1 ? Integer.parseInt(args[1]) : 0; // 0 = nouvel utilisateur

		ClientMsg c = new ClientMsg(id, host, 1666);

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

					case NOTIF_NICKNAME_CHANGED: {
						int uid = buf.getInt();
						int len = buf.getInt();
						byte[] name = new byte[len];
						buf.get(name);
						String newName = new String(name, StandardCharsets.UTF_8);
						c.nicknames.put(uid, newName);
						System.out.println("✓ User " + uid + " s'appelle désormais '" + newName + "'");
						break;
					}
					default:
						System.out.println("Notification inconnue: type=" + type);
				}
			} else {
				// Message normal (privé ou groupe)
				String from = c.displayName(p.srcId);
				String to = (p.destId < 0) ? ("groupe " + p.destId) : c.displayName(p.destId);
				System.out.println(from + " says to " + to + ": " + new String(p.data));
			}
		});

		// add a connection listener that exit application when connection closed
		c.addConnectionListener(active -> {
			if (!active)
				System.exit(0);
		});

		c.startSession();
		System.out.println("Vous êtes : " + c.getIdentifier());

		// Thread.sleep(5000);

		// l'utilisateur avec id 4 crée un grp avec 1 et 3 dedans (et lui meme)
		/*
		 * if (c.getIdentifier() == 4) {
		 * ByteArrayOutputStream bos = new ByteArrayOutputStream();
		 * DataOutputStream dos = new DataOutputStream(bos);
		 * 
		 * // byte 1 : create group on server
		 * dos.writeByte(1);
		 * 
		 * // nb members
		 * dos.writeInt(2);
		 * // list members
		 * dos.writeInt(1);
		 * dos.writeInt(3);
		 * dos.flush();
		 * 
		 * c.sendPacket(0, bos.toByteArray());
		 * 
		 * }
		 */

		Scanner sc = new Scanner(System.in);
		String lu = null;
		while (!"\\quit".equals(lu)) {
			try {
				System.out.println("A qui voulez vous écrire ? (ou /new, /add, /rm, /del, /leave, /nick, /quit)");
				String ligne = sc.nextLine().trim();// sc.nextLine() lis toute la ligne, trim() enlève les spaces
				if (ligne.isEmpty())
					continue;
				if (ligne.startsWith("/")) { // 如果/开始就是一个命令
					String[] parts = ligne.split("\\s+");// split多个空白格拆分字符串
					/*
					 * parts[0] = "/add"
					 * parts[1] = "-1"
					 * parts[2] = "5"
					 */
					String cmd = parts[0];
					switch (cmd) {
						case "/new": {
							// Syntaxe: /new 2,3,4 (liste des membres séparée par virgules,
							// sans id de groupe : le serveur attribue automatiquement un id négatif)
							if (parts.length < 3) {
								System.out.println("Usage: /new <Nom> <id1,id2,...>   ex: /new LesBaddies 2,3,4");
								break;
							}
							String groupName = parts[1]; // Le premier argument après /new est le nom
							String[] ids = parts[2].split(","); // Le deuxième est la liste d'IDs

							int[] members = new int[ids.length];
							for (int i = 0; i < ids.length; i++) {
								members[i] = Integer.parseInt(ids[i].trim());
							}

							// On appelle la méthode avec les DEUX arguments : le nom et les membres
							c.requestCreateGroup(groupName, members);

							System.out.println("Demande: créer groupe '" + groupName + "' envoyée");
							break;
						}

						case "/del": {
							// /del -1 Supprime groupe -1
							int gid = Integer.parseInt(parts[1]);
							c.requestDeleteGroup(gid);
							System.out.println("Demande: supprimer le groupe " + gid + " envoyée");
							break;
						}

						case "/add": {
							// /add -1 5,6 ajoute pluseirus/une seul personne dans le groupe -1
							int gid = Integer.parseInt(parts[1]);
							// int uid = Integer.parseInt(parts[2]); suelment un ajout possible
							String[] uids = parts[2].split(",");
							for (String uidSr : uids) {
								int uid = Integer.parseInt(uidSr.trim());
								c.requestAddMember(gid, uid);
							}
							System.out.println("Demande d'ajout envoyée: " + parts[2] + " dans " + gid);
							break;
						}

						case "/rm": {
							// /rm -1 5 supprime 5 dans le groupe -1
							int gid = Integer.parseInt(parts[1]);
							int uid = Integer.parseInt(parts[2]);
							c.requestRemoveMember(gid, uid);
							System.out.println("Demande: retirer " + uid + " de " + gid + " envoyée");
							break;
						}

						case "/leave": {
							// leave -1
							int gid = Integer.parseInt(parts[1]);
							c.requestLeaveGroup(gid);
							System.out.println("Demande: quitter le groupe " + gid + " envoyée");
							break;

						}

						case "/quit": {
							lu = "\\quit";
							break;
						}

						case "/nick": {
							if (parts.length < 2) {
								System.out.println("Usage: /nick <nouveau_pseudo>");
								break;
							}

							StringBuilder sb = new StringBuilder();
							for (int i = 1; i < parts.length; i++) {
								if (i > 1)
									sb.append(" ");
								sb.append(parts[i]);
							}
							c.requestSetNickname(sb.toString());
							System.out.println("Demande: changer pseudo en '" + sb + "' envoyée");
							break;
						}

						default:
							System.out.println("Commande inconnue");
					}
					Thread.sleep(500);
				} else {
					// envoie de messages directs à un autre utilisateurs
					int dest = Integer.parseInt(ligne);
					System.out.println("Votre message ? ");
					lu = sc.nextLine();
					c.sendPacket(dest, lu.getBytes());
					// lu.getBytes() 把字符串String转成byte字节数组
				}

			} catch (Exception e) {
				System.out.println("Mauvais format: " + e.getMessage());
			}

			/*
			 * while (!"\\quit".equals(lu)) {
			 * try {
			 * System.out.println("A qui voulez vous écrire ? ");
			 * int dest = Integer.parseInt(sc.nextLine());
			 * 
			 * System.out.println("Votre message ? ");
			 * lu = sc.nextLine();
			 * c.sendPacket(dest, lu.getBytes());
			 * } catch (InputMismatchException | NumberFormatException e) {
			 * System.out.println("Mauvais format");
			 * }
			 */

		}

		/*
		 * int id =1+(c.getIdentifier()-1) % 2; System.out.println("send to "+id);
		 * c.sendPacket(id, "bonjour".getBytes());
		 * 
		 * 
		 * Thread.sleep(10000);
		 */

		c.closeSession();

	}

}
