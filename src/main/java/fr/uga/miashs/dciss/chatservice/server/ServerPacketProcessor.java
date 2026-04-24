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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import fr.uga.miashs.dciss.chatservice.common.Packet;
import static fr.uga.miashs.dciss.chatservice.common.MessageType.*;

public class ServerPacketProcessor implements PacketProcessor {
	private final static Logger LOG = Logger.getLogger(ServerPacketProcessor.class.getName());
	private ServerMsg server;

	public ServerPacketProcessor(ServerMsg s) {
		this.server = s;
	}

	@Override
	public void process(Packet p) {
		if (p.data == null || p.data.length == 0) {
			LOG.warning("Packet vide reçu de " + p.srcId);
			return;
		}
		// ByteBufferVersion. On aurait pu utiliser un ByteArrayInputStream +
		// DataInputStream à la place
		ByteBuffer buf = ByteBuffer.wrap(p.data);
		byte type = buf.get();

		switch (type) {
			case CREATE_GROUP:
				createGroup(p.srcId, buf);
				break;
			case SUPPRIME_GROUP:
				deleteGroup(p.srcId, buf);
				break;
			case AJOUT_MEMBRE:
				addMember(p.srcId, buf);
				break;

			case SUPPRIME_MEMBRE:
				removeMember(p.srcId, buf);
				break;

			case LEAVE_GROUP:
				leaveGroup(p.srcId, buf);
				break;

			case SET_NICKNAME:
				setNickname(p.srcId, buf);
				break;

			case QUERY_PRESENCE:
				queryPresence(p.srcId);
				break;

			default:
				LOG.warning("Type de message inconnu : " + type + " pour srcId=" + p.srcId);
				break;

		}
	}

	private void createGroup(int ownerId, ByteBuffer data) {
		// Lire le nom du groupe
		int nameLen = data.getInt();
		byte[] nameBytes = new byte[nameLen];
		data.get(nameBytes);
		String groupName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);

		// Lire les membres
		int nb = data.getInt();
		GroupMsg g = server.createGroup(ownerId);
		g.setName(groupName); // il faudra ajouter setName dans GroupMsg

		for (int i = 0; i < nb; i++) {
			int memberId = data.getInt();
			UserMsg u = server.getUser(memberId);
			if (u == null) {
				LOG.warning("Member " + memberId + " non trouvé");
				continue;
			}
			g.addMember(u);
			System.out.println("Notification MEMBER_ADDED envoyée à tous pour membre " + memberId);
			notifyMemberAdded(g, memberId);
		}
		LOG.info("Group " + g.getId() + " creé par " + ownerId + " avec nom : " + groupName);
		notifyGroupCreated(ownerId, g.getId(), groupName);
	}

	private void deleteGroup(int srcId, ByteBuffer buf) {
		int groupId = buf.getInt();
		GroupMsg g = server.getGroup(groupId);// 从服务器里去找群对象
		if (g == null) {
			LOG.warning("Suppression impossible : groupe " + groupId + " introuvable.");
			notifyError(srcId, "Groupe " + groupId + " introuvable.");
			return;
		}
		if (g.getOwner().getId() != srcId) {
			LOG.warning("Suppression refusée : l'utilisateur " + srcId
					+ " n'est pas propriétaire du groupe " + groupId + ".");
			notifyError(srcId, "Vous n'êtes pas propriétaire du groupe " + groupId + ".");
			return;
		}
		notifyGroupDeleted(g); // notifier les membres avant le supprime
		server.removeGroup(groupId);
		LOG.info("Groupe " + groupId + " supprimé par son propriétaire " + srcId + ".");

	}

	private void addMember(int srcId, ByteBuffer buf) {
		int groupId = buf.getInt();
		// [int groupId][int userId]
		int newUserId = buf.getInt();

		GroupMsg g = server.getGroup(groupId);
		if (g == null) {
			LOG.warning("Ajout impossible : groupe " + groupId + " introuvable.");
			notifyError(srcId, "Groupe " + groupId + " introuvable.");
			return;
		}
		if (g.getOwner().getId() != srcId) { // 从群 g 里取出“群主”这个对象再取他的id
			LOG.warning(
					"Ajout  refusée : l'utilisateur " + srcId + " n'est pas propriétaire du groupe " + groupId + ".");
			notifyError(srcId, "Vous n'êtes pas propriétaire du groupe " + groupId + ".");
			return;
		}
		UserMsg u = server.getUser(newUserId);
		if (u == null) {
			LOG.warning("Ajout impossible : User " + newUserId + " introuvable.");
			notifyError(srcId, "Utilisateur " + newUserId + " introuvable.");
			return;
		}
		g.addMember(u);
		LOG.info("User " + newUserId + " ajouté par la propriétaire " + srcId + " du group " + groupId);
		notifyMemberAdded(g, newUserId);
	}

	private void removeMember(int srcId, ByteBuffer buf) {
		int groupId = buf.getInt();
		int userId = buf.getInt();

		GroupMsg g = server.getGroup(groupId);
		if (g == null) {
			LOG.warning("Suppression impossible : groupe " + groupId + " introuvable.");
			notifyError(srcId, "Groupe " + groupId + " introuvable.");
			return;
		}
		if (g.getOwner().getId() != srcId) { // 从群 g 里取出“群主”这个对象再取他的id
			LOG.warning("Suppression  refusée : l'utilisateur " + srcId + " n'est pas propriétaire du groupe " + groupId
					+ ".");
			notifyError(srcId, "Vous n'êtes pas propriétaire du groupe " + groupId + ".");
			return;
		}
		UserMsg u = server.getUser(userId);
		if (u == null) {
			LOG.warning("Suppression impossible : User " + userId + " introuvable.");
			notifyError(srcId, "Utilisateur " + userId + " introuvable.");
			return;
		}
		notifyMemberRemoved(g, userId);
		g.removeMember(u);
		LOG.info("User " + userId + " supprimé par la propriétaire " + srcId + " du group " + groupId);

	}

	private void leaveGroup(int srcId, ByteBuffer buf) {
		int groupId = buf.getInt();
		GroupMsg g = server.getGroup(groupId);
		if (g == null) {
			LOG.warning("Quitte impossible : groupe " + groupId + " introuvable.");
			notifyError(srcId, "Groupe " + groupId + " introuvable.");
			return;
		}
		UserMsg u = server.getUser(srcId);
		if (u == null) {
			LOG.warning("Suppression impossible : User " + srcId + " introuvable.");
			notifyError(srcId, "Utilisateur " + srcId + " introuvable.");
			return;
		}
		if (g.getOwner().getId() == srcId) {
			LOG.warning("Le propriétaire ne peut pas quitter son propre groupe. Utiliser /del.");
			notifyError(srcId, "Le propriétaire ne peut pas quitter le groupe. Utilisez /del.");
			return;
		}
		// notifier AVANT removeMember pour que le partant reçoive aussi la notif
		if (g.getMembers().contains(u)) {
			notifyMemberRemoved(g, srcId);
		}
		if (g.removeMember(u)) {
			LOG.info("User " + srcId + " a quitté le groupe " + groupId);
		} else {
			LOG.warning("Quitter groupe " + groupId + " échoué : user " + srcId + " n'est pas membre.");
			notifyError(srcId, "Vous n'êtes pas membre du groupe " + groupId + ".");
		}

	}

	private void setNickname(int srcId, ByteBuffer buf) {
		int len = buf.getInt();
		byte[] nameBytes = new byte[len];
		buf.get(nameBytes);
		String newNick = new String(nameBytes, StandardCharsets.UTF_8).trim();

		if (newNick.isEmpty() || newNick.length() > 32) {
			notifyError(srcId, "Nickname invalide (1-32 caractères)");
			return;
		}

		UserMsg u = server.getUser(srcId);
		if (u == null) {
			notifyError(srcId, "Utilisateur introuvable");
			return;
		}

		u.setNickname(newNick);
		LOG.info("User " + srcId + " a changé son pseudo en '" + newNick + "'");
		notifyNicknameChanged(u);
	}

	private void notifyNicknameChanged(UserMsg u) {
		byte[] nameBytes = u.getNickname().getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 + nameBytes.length);
		buf.put(NOTIF_NICKNAME_CHANGED);
		buf.putInt(u.getId());
		buf.putInt(nameBytes.length);
		buf.put(nameBytes);
		byte[] data = buf.array();

		// 通知自己 + 同群的人（去重）
		java.util.Set<Integer> done = new java.util.HashSet<>();
		done.add(u.getId());
		sendNotification(u.getId(), data);
		for (GroupMsg g : u.getGroups()) {
			for (UserMsg m : g.getMembers()) {
				if (done.add(m.getId())) {
					sendNotification(m.getId(), data);
				}
			}
		}
	}

	// Notification en format byte[] à un utilisateur spécifique
	private void sendNotification(int userId, byte[] data) {
		UserMsg u = server.getUser(userId);
		if (u == null)
			return;
		// srcId=0 repérsente ça vient du serveur
		u.process(new Packet(0, userId, data));
	}

	// Notification en format byte[] aux members du group
	private void broadcastNotification(GroupMsg g, byte[] data) {
		for (UserMsg m : g.getMembers()) {
			sendNotification(m.getId(), data);
		}
	}

	private void notifyGroupCreated(int ownerId, int groupId, String groupName) {
		byte[] nameBytes = groupName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 + nameBytes.length);
		buf.put(NOTIF_GROUP_CREATED);
		buf.putInt(groupId);
		buf.putInt(nameBytes.length);
		buf.put(nameBytes);
		sendNotification(ownerId, buf.array());
	}

	private void notifyMemberAdded(GroupMsg g, int addedUserId) {
		byte[] nameBytes = g.getName() != null ? g.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8)
				: new byte[0];
		ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 + 4 + nameBytes.length);
		buf.put(NOTIF_MEMBER_ADDED);
		buf.putInt(g.getId());
		buf.putInt(addedUserId);
		buf.putInt(nameBytes.length);
		buf.put(nameBytes);
		broadcastNotification(g, buf.array());
	}

	/**
	 * [3][groupId][userId] — 必须在 g.removeMember() 之前调用，
	 * 这样被踢的人还是群员，broadcast 会覆盖到他。
	 */
	private void notifyMemberRemoved(GroupMsg g, int removedUserId) {
		ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4);
		buf.put(NOTIF_MEMBER_REMOVED);
		buf.putInt(g.getId());
		buf.putInt(removedUserId);
		broadcastNotification(g, buf.array());
	}

	/** [4][groupId] — 删除前通知所有成员 */
	private void notifyGroupDeleted(GroupMsg g) {
		ByteBuffer buf = ByteBuffer.allocate(1 + 4);
		buf.put(NOTIF_GROUP_DELETED);
		buf.putInt(g.getId());
		broadcastNotification(g, buf.array());
	}

	/** [5][len][msg] — 错误通知 */
	private void notifyError(int userId, String message) {
		byte[] msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(1 + 4 + msgBytes.length);
		buf.put(NOTIF_ERROR);
		buf.putInt(msgBytes.length);
		buf.put(msgBytes);
		sendNotification(userId, buf.array());
	}

	/* =====================  PRESENCE  ===================== */

	/**
	 * Appelé par UserMsg.open() quand un utilisateur vient de se connecter.
	 * 1) envoie au nouveau connecté la liste des contacts déjà en ligne (snapshot)
	 * 2) prévient les membres de ses groupes qu'il est maintenant en ligne
	 */
	public void onUserOnline(UserMsg u) {
		if (u == null)
			return;

		// 1) snapshot envoyé uniquement à l'utilisateur qui vient de se connecter
		byte[] snapshot = buildPresenceSnapshot(u);
		sendNotification(u.getId(), snapshot);

		// 2) notifier les membres des groupes de u (sauf u lui-même)
		byte[] onlineMsg = buildPresenceEvent(NOTIF_USER_ONLINE, u.getId());
		broadcastPresence(u, onlineMsg);
	}

	/**
	 * Appelé par UserMsg.close() quand un utilisateur se déconnecte.
	 * Prévient les membres de ses groupes qu'il est maintenant hors ligne.
	 */
	public void onUserOffline(UserMsg u) {
		if (u == null)
			return;
		byte[] offlineMsg = buildPresenceEvent(NOTIF_USER_OFFLINE, u.getId());
		broadcastPresence(u, offlineMsg);
	}

	/**
	 * Réponse à une commande /refresh côté client :
	 * reconstruit un snapshot de présence pour l'utilisateur srcId
	 * et le lui renvoie.
	 */
	private void queryPresence(int srcId) {
		UserMsg u = server.getUser(srcId);
		if (u == null) {
			notifyError(srcId, "Utilisateur introuvable");
			return;
		}
		byte[] snapshot = buildPresenceSnapshot(u);
		sendNotification(u.getId(), snapshot);
	}

	/**
	 * Construit : [type][userId] (9 octets) pour ONLINE / OFFLINE.
	 */
	private byte[] buildPresenceEvent(byte type, int userId) {
		ByteBuffer buf = ByteBuffer.allocate(1 + 4);
		buf.put(type);
		buf.putInt(userId);
		return buf.array();
	}

	/**
	 * Construit le snapshot envoyé à un utilisateur qui vient de se connecter :
	 * [NOTIF_PRESENCE_SNAPSHOT][nb][id1][id2]...
	 * Contient les ids des utilisateurs actuellement en ligne et partageant au
	 * moins un groupe avec u (u lui-même n'est pas inclus).
	 */
	private byte[] buildPresenceSnapshot(UserMsg u) {
		java.util.Set<Integer> onlineIds = new java.util.HashSet<>();
		for (GroupMsg g : u.getGroups()) {
			for (UserMsg m : g.getMembers()) {
				if (m.getId() != u.getId() && m.isConnected()) {
					onlineIds.add(m.getId());
				}
			}
		}
		ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 * onlineIds.size());
		buf.put(NOTIF_PRESENCE_SNAPSHOT);
		buf.putInt(onlineIds.size());
		for (int id : onlineIds) {
			buf.putInt(id);
		}
		return buf.array();
	}

	/**
	 * Envoie un événement de présence (ONLINE/OFFLINE) à tous les membres des
	 * groupes de u, sauf u lui-même. Déduplication via Set.
	 */
	private void broadcastPresence(UserMsg u, byte[] data) {
		java.util.Set<Integer> done = new java.util.HashSet<>();
		done.add(u.getId()); // ne pas s'envoyer à soi-même
		for (GroupMsg g : u.getGroups()) {
			for (UserMsg m : g.getMembers()) {
				if (done.add(m.getId())) {
					sendNotification(m.getId(), data);
				}
			}
		}
	}

}
