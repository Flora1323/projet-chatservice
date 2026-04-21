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
		// ByteBufferVersion. On aurait pu utiliser un ByteArrayInputStream + DataInputStream à la place
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

			case LEAVE_GROUP : 
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
			if(u == null) {
				LOG.warning("Member " + memberId + " non trouvé");
				continue;
			}
			g.addMember(u);
		}
		LOG.info("Group " + g.getId() + " creé par" + ownerId);
	}

	private void deleteGroup(int srcId, ByteBuffer buf) {
		int groupId = buf.getInt();
		GroupMsg g = server.getGroup(groupId);//从服务器里去找群对象
		if(g==null){
			LOG.warning("Suppression impossible : groupe " + groupId + " introuvable.");
			return;
		}
		if(g.getOwner().getId() != srcId) {
			LOG.warning("Suppression refusée : l'utilisateur " + srcId
                + " n'est pas propriétaire du groupe " + groupId + ".");
			return;
		}
		server.removeGroup(groupId);
		LOG.info("Groupe " + groupId + " supprimé par son propriétaire " + srcId + ".");

	}

	private void addMember (int srcId, ByteBuffer buf) {
		int groupId = buf.getInt();
		//[int groupId][int userId]
		int newUserId = buf.getInt();

		GroupMsg g = server.getGroup(groupId);
		if(g==null){
			LOG.warning("Ajout impossible : groupe " + groupId + " introuvable.");
			return;
		}
		if(g.getOwner().getId() != srcId){ //从群 g 里取出“群主”这个对象再取他的id
			LOG.warning("Ajout  refusée : l'utilisateur " + srcId + " n'est pas propriétaire du groupe " + groupId + ".");
			return;
		}
		UserMsg u = server.getUser(newUserId);
		if(u==null){
			LOG.warning("Ajout impossible : User " + newUserId + " introuvable.");
			return;
		}
		g.addMember(u);
		LOG.info("User " + newUserId + " ajouté par la propriétaire " + srcId + " du group " + groupId );
	}

	private void removeMember (int srcId, ByteBuffer buf){
		int groupId = buf.getInt();
		int userId = buf.getInt();

		GroupMsg g = server.getGroup(groupId);
		if(g==null){
			LOG.warning("Suppression impossible : groupe " + groupId + " introuvable.");
			return;
		}
		if(g.getOwner().getId() != srcId){ //从群 g 里取出“群主”这个对象再取他的id
			LOG.warning("Suppression  refusée : l'utilisateur " + srcId + " n'est pas propriétaire du groupe " + groupId + ".");
			return;
		}
		UserMsg u = server.getUser(userId);
		if(u==null){
			LOG.warning("Suppression impossible : User " + userId + " introuvable.");
			return;
		}
		g.removeMember(u);
		LOG.info("User " + userId + " supprimé par la propriétaire " + srcId + " du group " + groupId );

	}
	private void leaveGroup (int srcId, ByteBuffer buf) {
		int groupId = buf.getInt();
		GroupMsg g = server.getGroup(groupId);
		if (g==null) {
			LOG.warning("Quitte impossible : groupe " + groupId + " introuvable.");
			return;
		}
		UserMsg u = server.getUser(srcId);
		if (u==null) {
			LOG.warning("Suppression impossible : User " + srcId + " introuvable.");
			return;
		}
		if (g.getOwner().getId() == srcId) {
			LOG.warning("Le propriétaire ne peut pas quitter son propre groupe. Utiliser /del.");
			return;
		}
		if(g.removeMember(u)) {
			LOG.info("User " + srcId + " a quitté le groupe " + groupId);
		}else {
			LOG.warning("Quitter groupe " + groupId + " échoué : user " + srcId + " n'est pas membre.");
		}

	}

}
