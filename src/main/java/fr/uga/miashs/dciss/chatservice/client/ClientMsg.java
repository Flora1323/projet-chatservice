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
import java.nio.file.Files;
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

	private LocalHistoryManager history = new LocalHistoryManager();
	private final Map<Integer, String> nicknames = new ConcurrentHashMap<>();


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
				if (history != null && sender != 0) { // on n'enregistre pas les notifications du serveur (sender=0)
					history.saveMessage(sender, dest, texteRecu);
				}

				notifyMessageListeners(new Packet(sender, dest, data));

			}
		} catch (IOException e) {
			// error, connection closed
			closeSession();
		}

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
	
	//fichiers 
		public void envoyerFichier(int destId, File file) {
			try {
				   //Lire le fichier en bytes
				    byte[] fileBytes = Files.readAllBytes(file.toPath());
			        byte[] nameBytes = file.getName().getBytes();


			        ByteArrayOutputStream bos = new ByteArrayOutputStream();
			        DataOutputStream dos = new DataOutputStream(bos);

			        //ecrire selon le protocole : type + taille nom + nom + taille fichier + contenu
			        dos.writeByte(2);       // type = ?
			        dos.writeInt(nameBytes.length);        // taille du nom
			        dos.write(nameBytes);           // le nom
			        dos.writeInt(fileBytes.length);        // taille du fichier
			        dos.write(fileBytes);           // le contenu
			        dos.flush( );

			        //Envoyer via sendPacket
			        sendPacket(destId, bos.toByteArray());

			    } catch (IOException e) {
			        e.printStackTrace();
			    }
			}


		public void recevoirFichier(Packet p) {
		    try {
		        //réparper les outils pour LIRE les bytes du paquet
		        ByteArrayInputStream bis = new ByteArrayInputStream(p.data);
		        DataInputStream dis = new DataInputStream(bis);

		        // Lire dans le même ordre qu'à l'envoi
		        byte type = dis.readByte() ;              // lis le type
		        int tailleNom = dis.readInt();          // lis la taille du nom

		        // creer un tableau vide de la bonne taille, puis le remplir
		        byte[] nameBytes = new byte[tailleNom];
		        dis.readFully(nameBytes);
		        String nomFichier = new String(nameBytes);

		        int tailleFichier = dis.readInt();      // lis la taille du fichier
		        byte[] fileBytes = new byte[tailleFichier];
		        dis.readFully(fileBytes);

		        // sauvegarder le fichier sur le disque
		        File dossier = new File("fichiers_recus");
		        if (!dossier.exists()) dossier.mkdirs();

		        File fichierRecu = new File(dossier, nomFichier);
		        Files.write(fichierRecu.toPath(), fileBytes);

		        System.out.println("Fichier reçu : " + nomFichier + " de la part de " + p.srcId);

		    } catch (IOException e) {
		        e.printStackTrace();
		    }
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

		// MISE EN PLACE D'UN SERVEUR POUR TESTER LE CLIENT
		String host = args.length > 0 ? args[0] : "localhost";
		int id = args.length > 1 ? Integer.parseInt(args[1]) : 0; // 0 = nouvel utilisateur

		ClientMsg c = new ClientMsg(id, host, 1666);

		// add a dummy listener that print the content of message as a string
		c.addMessageListener(p -> {

			// Si c'est un fichier (byte 2), on n'affiche PAS comme du texte
			if (p.data[0] == 2) {
				return; // Le listener fichier s'en occupe
			}

			// 如果 srcId == 0 -> 这是服务器发来的通知(Actions serveur -> client)
			if (p.srcId == 0 && p.data != null && p.data.length >= 1) {
				ByteBuffer buf = ByteBuffer.wrap(p.data);
				byte type = buf.get();
				switch (type) {
					case NOTIF_GROUP_CREATED: {
						int gid = buf.getInt();
						System.out.println("Groupe " + gid + " créé avec succès");
						break;
					}
					case NOTIF_MEMBER_ADDED: {
						int gid = buf.getInt();
						int uid = buf.getInt();
						System.out.println("User " + uid + " ajouté au groupe " + gid);
						break;
					}
					case NOTIF_MEMBER_REMOVED: {
						int gid = buf.getInt();
						int uid = buf.getInt();
						System.out.println("User " + uid + " retiré du groupe " + gid);
						break;
					}
					case NOTIF_GROUP_DELETED: {
						int gid = buf.getInt();
						System.out.println("Groupe " + gid + " supprimé");
						break;
					}
					case NOTIF_ERROR: {
						int len = buf.getInt();
						byte[] msg = new byte[len];
						buf.get(msg);
						System.out.println("ERREUR: " + new String(msg, StandardCharsets.UTF_8));
						break;
					}
					case NOTIF_NICKNAME_CHANGED: {
						int uid = buf.getInt();
						int len = buf.getInt();
						byte[] name = new byte[len];
						buf.get(name);
						String newName = new String(name, StandardCharsets.UTF_8);
						c.nicknames.put(uid, newName);
						System.out.println("User " + uid + " s'appelle désormais '" + newName + "'");
						break;
					}
					default:
						System.out.println("Notification inconnue: type=" + type);
				}
			} else {
				// Message normal (privé ou groupe) - avec affichage des nicknames
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

		// listener qui détecte et sauvegarde les fichiers reçus
		c.addMessageListener(p -> {
			if (p.data[0] == 2) {
				c.recevoirFichier(p);
			}
		});

		c.startSession();
		System.out.println("Vous êtes : " + c.getIdentifier());


		Scanner sc = new Scanner(System.in);
		String lu = null;

		while (!"\\quit".equals(lu)) {
			try {
				System.out.println("Tapez 'message', 'fichier', une commande (/history, /new, /add, /rm, /del, /leave, /nick), ou '\\quit' :");
				lu = sc.nextLine().trim();

				if (lu.isEmpty()) continue;

				if (lu.startsWith("/")) { //如果/开始就是一个命令
					String[] parts = lu.split("\\s+");
					String cmd = parts[0];
					switch (cmd) {
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
							String[] ids = sb.toString().split(",");
							int[] members = new int[ids.length];
							for (int i = 0; i < ids.length; i++)
								members[i] = Integer.parseInt(ids[i].trim());
							c.requestCreateGroup(members);
							System.out.println("Demande: créer groupe avec membres " + sb + " envoyée");
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

				} else if (lu.equals("message")) {
					System.out.println("A qui voulez vous écrire ? ");
					int dest = Integer.parseInt(sc.nextLine());

					System.out.println("Votre message ? ");
					lu = sc.nextLine();
					c.sendPacket(dest, lu.getBytes(StandardCharsets.UTF_8));
					// on sauvegarde dans la BDD
					if (c.history != null) {
						c.history.saveMessage(c.getIdentifier(), dest, lu);
					}

				} else if (lu.equals("fichier")) {
					System.out.println("A qui voulez vous écrire ? ");
					int dest = Integer.parseInt(sc.nextLine());

					System.out.println("Chemin du fichier ? ");
					String chemin = sc.nextLine();
					File fichier = new File(chemin);
					if (fichier.exists()) {
						c.envoyerFichier(dest, fichier);
					} else {
						System.out.println("Fichier introuvable !");
					}

				} else {
					// envoi de messages directs à un autre utilisateur
					try {
						int dest = Integer.parseInt(lu);
						System.out.println("Votre message ? ");
						lu = sc.nextLine();
						if (!"\\quit".equals(lu)) {
							c.sendPacket(dest, lu.getBytes(StandardCharsets.UTF_8));
							// on sauvegarde dans la BDD
							if (c.history != null) {
								c.history.saveMessage(c.getIdentifier(), dest, lu);
							}
						}
					} catch (NumberFormatException e) {
						System.out.println("Commande inconnue. Tapez 'message', 'fichier' ou /...");
					}
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

		c.closeSession();
	}
}
