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

package fr.uga.miashs.dciss.chatservice.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.HashMap;

import fr.uga.miashs.dciss.chatservice.common.Packet;

import java.util.*;

public class ServerMsg implements Serializable {
	private static final long serialVersionUID = 1L; // implementation de Serializable pour permettre la sérialisation
														// de l'état du serveur

	private final static Logger LOG = Logger.getLogger(ServerMsg.class.getName());
	/*
	 * LOG.info("Server started");
	 * LOG.warning("Unknown client id");
	 * LOG.severe("Socket error");
	 */
	public final static int SERVER_CLIENTID = 0;// 服务器的 id 永远是 0

	private transient ServerSocket serverSock;// ServerSocket是服务器端用来监听客户端连接的 socket。
	// Socket client = serverSock.accept();接收一个客户端连接。
	private transient boolean started;// 服务器是否启动
	private transient ExecutorService executor;// Java 并发编程里用来管理线程的接口
	private transient ServerPacketProcessor sp;// 负责处理收到的 Packet

	// maps pour associer les id aux users et groupes
	private Map<Integer, UserMsg> users;
	private Map<Integer, GroupMsg> groups;

	// séquences pour générer les identifiant d'utilisateurs et de groupe
	private AtomicInteger nextUserId;
	/*
	 * 那第一个新用户连接时，服务器分配：userId = 1
	 * 然后它自动变成：nextUserId = 2
	 * 再来一个新用户： userId = 2
	 */
	// AtomicInteger是一个线程安全的整数
	private AtomicInteger nextGroupId;

	public ServerMsg(int port) throws IOException {
		serverSock = new ServerSocket(port);
		started = false;
		users = new ConcurrentHashMap<>();
		groups = new ConcurrentHashMap<>();
		nextUserId = new AtomicInteger(1);
		nextGroupId = new AtomicInteger(-1);
		sp = new ServerPacketProcessor(this);
		executor = Executors.newCachedThreadPool();
	}

	public GroupMsg createGroup(int ownerId) {
		UserMsg owner = users.get(ownerId);
		if (owner == null)
			throw new ServerException("User with id=" + ownerId + " unknown. Group creation failed.");
		int id = nextGroupId.getAndDecrement();
		GroupMsg res = new GroupMsg(id, owner);
		groups.put(id, res);
		LOG.info("Group " + res.getId() + " created");
		return res;
	}

	public boolean removeGroup(int groupId) {
		GroupMsg g = groups.remove(groupId);
		if (g == null)
			return false;
		g.beforeDelete();
		return true;
	}

	public boolean removeUser(int userId) {
		UserMsg u = users.remove(userId);
		if (u == null)
			return false;
		u.beforeDelete();
		return true;
	}

	public UserMsg getUser(int userId) {
		return users.get(userId);
	}

	// Methode utilisée pour savoir quoi faire d'un paquet
	// reçu par le serveur
	public void processPacket(Packet p) {
		PacketProcessor pp = null;
		if (p.destId < 0) { // message de groupe
			// can be send only if sender is member
			UserMsg sender = users.get(p.srcId);
			GroupMsg g = groups.get(p.destId);
			if (g.getMembers().contains(sender))
				pp = g;
		} else if (p.destId > 0) { // message entre utilisateurs
			pp = users.get(p.destId);
		} else { // message de gestion pour le serveur
			pp = sp;
		}

		if (pp != null) {
			pp.process(p);
		}
	}

	public void start() {
		started = true;
		while (started) {
			try {
				// le serveur attend une connexion d'un client
				Socket s = serverSock.accept();

				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				// lit l'identifiant du client
				int userId = dis.readInt();
				// si 0 alors il faut créer un nouvel utilisateur et
				// envoyer l'identifiant au client
				if (userId == 0) {
					userId = nextUserId.getAndIncrement();
					dos.writeInt(userId);
					dos.flush();
					users.put(userId, new UserMsg(userId, this));
				}
				// si l'identifiant existe ou est nouveau alors
				// deux "taches"/boucles sont lancées en parralèle
				// une pour recevoir les messages du client,
				// une pour envoyer des messages au client
				// les deux boucles sont gérées au niveau de la classe UserMsg
				UserMsg x = users.get(userId);
				if (x != null && x.open(s)) {
					LOG.info(userId + " connected");
					// lancement boucle de reception
					executor.submit(() -> x.receiveLoop());
					// lancement boucle d'envoi
					executor.submit(() -> x.sendLoop());
				} else { // si l'idenfiant est inconnu, on ferme la connexion
					s.close();
				}

			} catch (IOException e) {
				LOG.info("Close server");
				e.printStackTrace();
			}
		}
	}

	public void stop() {
		started = false;
		try {
			serverSock.close();
			users.values().forEach(s -> s.close());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public GroupMsg getGroup(int id) {
		return groups.get(id);
	}

	public void save(String filePath) {
		try (ObjectOutputStream oos = new ObjectOutputStream(
				new FileOutputStream(filePath))) {
			// On écrit les users dans le fichier
			oos.writeObject(users);
			// On écrit les groupes dans le fichier
			oos.writeObject(groups);
			// On sauvegarde les compteurs d'ID
			// pour ne pas réattribuer un ID déjà utilisé
			oos.writeInt(nextUserId.get());
			oos.writeInt(nextGroupId.get());
		} catch (IOException e) {
			LOG.warning("Erreur sauvegarde : " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	public void load(String filePath) {
		try (ObjectInputStream ois = new ObjectInputStream(
				new FileInputStream(filePath))) {
			// On relit les users depuis le fichier
			// dans le même ordre que la sauvegarde !
			users = (Map<Integer, UserMsg>) ois.readObject();
			// On relit les groupes
			groups = (Map<Integer, GroupMsg>) ois.readObject();
			// On restaure les compteurs d'ID
			nextUserId.set(ois.readInt());
			nextGroupId.set(ois.readInt());
		} catch (IOException | ClassNotFoundException e) {
			LOG.warning("Erreur chargement : " + e.getMessage());
		}
	}

	public Map<Integer, String> getAllNicknames() {
		Map<Integer, String> result = new HashMap<>();
		for (Map.Entry<Integer, UserMsg> entry : users.entrySet()) {
			result.put(entry.getKey(), entry.getValue().getNickname());
		}
		return result;
	}

	public static void main(String[] args) throws IOException {
		ServerMsg s = new ServerMsg(1666);

		// On vérifie si un fichier de sauvegarde existe
		// Si oui, on recharge l'état précédent du serveur
		File f = new File("server_state.ser");
		if (f.exists()) {
			s.load("server_state.ser");
		}

		// addShutdownHook = code exécuté automatiquement
		// quand le serveur est arrêté (Ctrl+C par exemple)
		// Ça permet de sauvegarder juste avant de s'éteindre
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			s.save("server_state.ser");
		}));

		s.start();
	}

}
