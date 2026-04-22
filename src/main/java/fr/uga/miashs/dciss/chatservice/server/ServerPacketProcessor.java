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

			default:
				LOG.warning("Type de message inconnu : " + type + " pour srcId=" + p.srcId);
				break;

		}
	}

	private void createGroup(int ownerId, ByteBuffer data) {
		int nb = data.getInt();
		GroupMsg g = server.createGroup(ownerId);
		for (int i = 0; i < nb; i++) {
			int memberId = data.getInt();
			UserMsg u = server.getUser(memberId);
			if (u == null) {
				LOG.warning("Member " + memberId + " non trouvé");
				continue;
			}
			g.addMember(u);
		}
		LOG.info("Group " + g.getId() + " creé par" + ownerId);
		notifyGroupCreated(ownerId, g.getId());
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

	private void notifyGroupCreated(int ownerId, int groupId) {
		ByteBuffer buf = ByteBuffer.allocate(1 + 4);
		buf.put(NOTIF_GROUP_CREATED);
		buf.putInt(groupId);
		sendNotification(ownerId, buf.array());
	}

	private void notifyMemberAdded(GroupMsg g, int addedUserId) {
		ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4);
		buf.put(NOTIF_MEMBER_ADDED);
		buf.putInt(g.getId());
		buf.putInt(addedUserId);
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

}
