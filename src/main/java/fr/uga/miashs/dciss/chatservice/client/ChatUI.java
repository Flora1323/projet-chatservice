package fr.uga.miashs.dciss.chatservice.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*; // Utiliser .* importe tous les contrôles (Button, Label, etc.)
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;


import javafx.stage.FileChooser;
import java.io.File;

import static fr.uga.miashs.dciss.chatservice.common.MessageType.*;

public class ChatUI extends Application {

    private ClientMsg client;
    private VBox messagesBox;
    private ScrollPane scrollPane;
    private TextField inputField;
    private TextField destField;
    private Label statusLabel;
    private VBox groupList;
    private VBox contactList;

    // Une seule Map locale suffit pour faire le lien temporaire
    private Map<Integer, String> customGroupNames = new HashMap<>();
    private String lastCreatedGroupName;

    // Historique des messages
    private LocalHistoryManager history = new LocalHistoryManager();

    @Override
    public void start(Stage stage) {

        // ####################################
        // LE HEADER
        // ####################################

        HBox header = new HBox(); // aligne les éléments horizontalement
        header.setPadding(new Insets(15)); // espace intérieur
        header.setStyle("-fx-background-color: #3E2723;"); // Le titre
        Label titleLabel = new Label("💅 Baddies");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18)); // police en gras et taille 18
        titleLabel.setTextFill(Color.web("#FFDEE2")); // couleur rose

        // Après titleLabel dans le header

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS); // pousse le label à droite

        statusLabel = new Label("Connexion...");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        statusLabel.setTextFill(Color.WHITE);

        header.getChildren().addAll(titleLabel, spacer, statusLabel);

        BorderPane root = new BorderPane(); // layout principal avec haut/bas/gauche/droite/centre
        root.setTop(header); // on met le header en haut

        TextInputDialog nickDialog = new TextInputDialog("Slayyyy");
        nickDialog.setTitle("Configuration");
        nickDialog.setHeaderText("Bienvenue sur le chat ! 💅");
        nickDialog.setContentText("Choisis ton petit nom de Baddie :");

        nickDialog.showAndWait().ifPresentOrElse(nick -> {
            connectToServer();
            try {
                client.requestSetNickname(nick);
                stage.setTitle("Chat - " + nick);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, () -> {
            // On peut soit fermer, soit connecter avec un nom par défaut
            System.out.println("Connexion annulée");
        });

        // PANNEAU GAUCHE
        VBox leftPanel = new VBox(10);
        leftPanel.setPrefWidth(150);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setStyle("-fx-background-color: #3E2723;");

        Label groupTitle = new Label("Groupes");
        groupTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        groupTitle.setTextFill(Color.WHITE);
        
        Label contactTitle = new Label("Mes Contacts");
        contactTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        contactTitle.setTextFill(Color.WHITE);
        
        contactList = new VBox(5);
        
        // Ajoute-les au leftPanel (avant ou après les groupes, comme tu préfères !)
        leftPanel.getChildren().addAll(contactTitle, contactList);

        // BOUTON POUR CREER UN GROUPE
        Button createGroupBtn = new Button("+ Créer groupe");
        createGroupBtn.setStyle(
                "-fx-background-color: #F4C9D6; -fx-text-fill: #3E2723; " +
                        "-fx-background-radius: 10; -fx-font-weight: bold;");
        createGroupBtn.setMaxWidth(Double.MAX_VALUE);

        // Liste des groupes
        groupList = new VBox(5);
        leftPanel.getChildren().add(groupTitle);
        leftPanel.getChildren().add(createGroupBtn);
        leftPanel.getChildren().add(groupList);
        root.setLeft(leftPanel);

        // BOUTON POUR CREER UN GROUPE
        createGroupBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Format : NomDuGroupe : ID1,ID2");

            dialog.showAndWait().ifPresent(input -> {
                try {
                    String[] parts = input.split(":");
                    if (parts.length < 2)
                        throw new Exception("Format incorrect");

                    String gName = parts[0].trim();
                    String[] ids = parts[1].split(",");
                    int[] members = new int[ids.length];
                    for (int i = 0; i < ids.length; i++)
                        members[i] = Integer.parseInt(ids[i].trim());

                    // On utilise bien les deux arguments ici !
                    client.requestCreateGroup(gName, members);
                    this.lastCreatedGroupName = gName;

                } catch (Exception ex) {
                    System.out.println("Format invalide. Utilisez Nom : 1,2,3");
                }
            });
        });

        Scene scene = new Scene(root, 500, 700); // taille de la fenêtre : 500px de large et 700px de haut
        stage.setScene(scene); // on associe la scène à la fenêtre
        stage.setTitle("Chat"); // titre de la fenêtre
        stage.show(); // on affiche la fenêtre

        // ####################################
        // LE CORPS DE LA FENETRE
        // ####################################

        messagesBox = new VBox(8); // boite qui aligne les éléments verticalement + affichage des messages, 8 =
                                   // espace entre les messages
        messagesBox.setPadding(new Insets(15)); // espace intérieur
        messagesBox.setStyle("-fx-background-color: #FDF0F4;");

        HBox destBar = new HBox(10); // barre pour saisir l'id du destinataire, 10 = espace entre les éléments
        destBar.setPadding(new Insets(8, 15, 8, 15));// espace intérieur (haut, droite, bas, gauche)
        destBar.setStyle("-fx-background-color: #F4C9D6;");
        destBar.setAlignment(Pos.CENTER_LEFT); // aligne les éléments à gauche

        Label destLabel = new Label("À :"); // label pour indiquer que c'est le champ du destinataire
        destLabel.setTextFill(Color.web("#3E2723")); // couleur du texte en blanc
        destLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13)); // police en gras et taille 13

        destField = new TextField(); // champ de saisie pour l'id du destinataire
        destField.setPromptText("ID destinataire (ex: 2)"); // texte grisé qui disparaît quand on commence à écrire
        destField.setPrefWidth(200); // largeur préférée du champ
        destField.setStyle("-fx-background-radius: 20; -fx-padding: 5 10 5 10;"); // arrondit les bords et ajoute du
                                                                                  // padding (haut, droite, bas, gauche)

        destBar.getChildren().addAll(destLabel, destField); // on ajoute le label et le champ à la barre du destinataire

        // Ajoute une VBox pour empiler header + destBar en haut
        VBox top = new VBox(header, destBar);
        root.setTop(top);

        scrollPane = new ScrollPane(messagesBox); // permet de scroller quand il y a beaucoup de messages
        scrollPane.setFitToWidth(true); // fait en sorte que les messages prennent toute la largeur disponible
        scrollPane.setStyle("-fx-background-color: #FDF0F4; -fx-background: #FDF0F4;");
        // blanches

        root.setCenter(scrollPane); // on met la zone messages au centre

        // --- BOUTON FICHIER ---
        Button fileButton = new Button("📎");
        fileButton.setStyle(
                "-fx-background-color: #3E2723; -fx-text-fill: white; " +
                        "-fx-background-radius: 20; -fx-font-size: 16; -fx-padding: 5 12 5 12;");

        fileButton.setOnAction(e -> {
            String destText = destField.getText().trim();
            if (destText.isEmpty()) {
                addMessage("⚠️ Choisis un destinataire avant d'envoyer un fichier !", false);
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choisir un fichier à envoyer 💅");
            File selectedFile = fileChooser.showOpenDialog(stage);

            if (selectedFile != null) {
                try {
                    int destId = Integer.parseInt(destText);

                    // 1. Envoi réel via le réseau
                    client.envoyerFichier(destId, selectedFile);

                    // 2. Affichage visuel dans TON chat (bulle rose à droite)
                    afficherFichierEnvoye(selectedFile, selectedFile.getName());

                } catch (NumberFormatException ex) {
                    addMessage("✗ ID destinataire invalide", false);
                } catch (Exception ex) {
                    addMessage("✗ Erreur lors de l'envoi du fichier", false);
                    ex.printStackTrace();
                }
            }
        });

        // ####################################
        // LE BAS DE LA FENETRE
        // ####################################

        HBox bottomBar = new HBox(8); // aligne le champ et le bouton horizontalement, 8 = espace entre les éléments
        bottomBar.setPadding(new Insets(10)); // espace intérieur
        bottomBar.setStyle("-fx-background-color: #F4C9D6;");
        bottomBar.setAlignment(Pos.CENTER_LEFT); // aligne les éléments à gauche

        // Champ de saisie
        inputField = new TextField();
        inputField.setPromptText("Votre message..."); // texte grisé qui disparaît quand on commence à écrire
        inputField.setStyle("-fx-background-radius: 20; -fx-padding: 8 15 8 15;"); // arrondit les bords et ajoute du
                                                                                   // padding
        HBox.setHgrow(inputField, Priority.ALWAYS); // prend tout l'espace disponible

        // Bouton envoyer
        Button sendButton = new Button("➤");
        sendButton.setStyle(
                "-fx-background-color: #3E2723; -fx-text-fill: white; "
                        + "-fx-background-radius: 20; -fx-font-size: 16; -fx-padding: 5 15 5 15;"); // bleu azur, texte

        // --- LES LIGNES MANQUANTES ---
        bottomBar.getChildren().addAll(fileButton, inputField, sendButton); // On assemble tout
        root.setBottom(bottomBar); // On attache la barre au bas de la fenêtre

        // ####################################
        // Action du bouton envoyer
        // ####################################

        sendButton.setOnAction(e -> { // quand on clique sur envoyer
            String msg = inputField.getText().trim(); // on récupère le message et on enlève les espaces au début et à
                                                      // la fin
            String destText = destField.getText().trim(); // on récupère le texte du champ destinataire et on enlève les
                                                          // espaces

            if (msg.isEmpty() || destText.isEmpty()) // si le message ou le destinataire est vide, on ne fait rien
                return;

            try {
                int destId = Integer.parseInt(destText);
                // envoie le message via ClientMsg
                client.sendPacket(destId, msg.getBytes()); // on envoie le message au serveur pour qu'il le redirige au
                                                           // destinataire
                // sauvegarde le message envoyé dans la BDD
                Message.insertMessage(client.getIdentifier(), destId, msg);
                addMessage(msg, true); // on affiche le message dans la zone de messages (true
                                       // = c'est un message envoyé par moi)
                inputField.clear();
            } catch (NumberFormatException ex) {
                System.out.println("ID invalide");
            }
        });

        // Envoyer aussi avec la touche Entrée
        inputField.setOnAction(e -> {
            String msg = inputField.getText().trim();
            String destText = destField.getText().trim();
            if (msg.isEmpty() || destText.isEmpty())
                return;
            try {
                int destId = Integer.parseInt(destText);
                client.sendPacket(destId, msg.getBytes());
                // sauvegarde le message envoyé dans la BDD
                Message.insertMessage(client.getIdentifier(), destId, msg);
                addMessage(msg, true);
                inputField.clear();
            } catch (NumberFormatException ex) {
                System.out.println("ID invalide");
            }
        });
    }

    private Button createGroupButton(int gid) {
        String name = client.getNicknamesMap().getOrDefault(gid, "Groupe " + gid);
        Button groupBtn = new Button(name);
        groupBtn.setId("btn-group-" + gid); // On donne un ID pour le retrouver facilement plus tard
        groupBtn.setStyle("-fx-background-color: #F4C9D6; -fx-text-fill: #3E2723; -fx-background-radius: 10;");
        groupBtn.setMaxWidth(Double.MAX_VALUE);

        groupBtn.setOnAction(ev -> destField.setText(String.valueOf(gid)));

        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();

        javafx.scene.control.MenuItem addMember = new javafx.scene.control.MenuItem("➕ Ajouter un membre");
        javafx.scene.control.MenuItem removeMember = new javafx.scene.control.MenuItem("➖ Exclure un membre");
        javafx.scene.control.MenuItem deleteGroup = new javafx.scene.control.MenuItem("🗑 Supprimer le groupe");
        javafx.scene.control.MenuItem leaveGroup = new javafx.scene.control.MenuItem("🚪 Quitter le groupe");

        // Action : Ajouter
        addMember.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Ajouter un membre au groupe " + gid);
            dialog.showAndWait().ifPresent(uid -> {
                try {
                    client.requestAddMember(gid, Integer.parseInt(uid.trim()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });

        // Action : Exclure
        removeMember.setOnAction(e -> {
            String gName = client.displayName(gid);
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Exclure un membre du groupe " + gName);
            dialog.showAndWait().ifPresent(uid -> {
                try {
                    client.requestRemoveMember(gid, Integer.parseInt(uid.trim()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });

        // Action : Supprimer
        deleteGroup.setOnAction(e -> {
            try {
                client.requestDeleteGroup(gid);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // Action : Quitter
        leaveGroup.setOnAction(e -> {
            try {
                client.requestLeaveGroup(gid);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        menu.getItems().addAll(addMember, removeMember, deleteGroup, leaveGroup);
        groupBtn.setContextMenu(menu);

        return groupBtn;
    }

    // ####################################
    // FONCTION POUR SE CONNECTER AU SERVEUR ET RECEVOIR LES MESSAGES
    // ####################################

    private void connectToServer() {
        try {
            client = new ClientMsg("localhost", 1666);

            // quand on reçoit un message, on l'affiche à gauche
            client.addMessageListener(p -> {
                // Si c'est un fichier (byte 2)
                if (p.data != null && p.data.length > 0 && p.data[0] == 2) {
                    recevoirFichierUI(p);
                    return;
                }
                if (p.srcId == 0 && p.data != null && p.data.length >= 1) {
                    ByteBuffer buf = ByteBuffer.wrap(p.data);
                    byte type = buf.get();
                    switch (type) {
                        case NOTIF_GROUP_CREATED: {
                            int gid = buf.getInt();
                            // Lire le nom envoyé par le serveur
                            int nameLen = buf.getInt();
                            byte[] nameBytes = new byte[nameLen];
                            buf.get(nameBytes);
                            String gName = new String(nameBytes, StandardCharsets.UTF_8);

                            // on enregistre le nom du groupe dans la map des pseudos pour l'afficher
                            // correctement
                            client.getNicknamesMap().put(gid, gName);

                            Platform.runLater(() -> {
                                groupList.getChildren().add(createGroupButton(gid));
                                addMessage("✓ Vous avez rejoint le groupe : " + gName, false);
                            });
                            break;
                        }
                        case NOTIF_MEMBER_ADDED: {
                            int gid = buf.getInt();
                            int uid = buf.getInt();
                            int nameLen = buf.getInt();
                            byte[] nameBytes = new byte[nameLen];
                            buf.get(nameBytes);
                            String gName = new String(nameBytes, StandardCharsets.UTF_8);

                            // On récupère le nom de l'utilisateur ajouté
                            String uName = client.displayName(uid);

                            Platform.runLater(() -> {
                                addMessage("✓ " + uName + " ajouté au groupe " + gName, false);
                                if (uid == client.getIdentifier()) {
                                    client.getNicknamesMap().put(gid, gName);
                                    groupList.getChildren().add(createGroupButton(gid));
                                }
                            });
                            break;
                        }
                        case NOTIF_MEMBER_REMOVED: {
                            int gid = buf.getInt();
                            int uid = buf.getInt();

                            // On transforme les IDs en noms
                            String uName = client.displayName(uid);
                            String gName = client.displayName(gid);

                            Platform.runLater(() -> {
                                addMessage("✓ " + uName + " retiré du groupe " + gName, false);
                                // SI C'EST MOI qui suis retiré (ou qui ai quitté), j'enlève le bouton
                                if (uid == client.getIdentifier()) {
                                    removeGroupButton(gid);
                                }
                            });
                            break;
                        }
                        case NOTIF_GROUP_DELETED: {
                            int gid = buf.getInt();

                            String gName = client.displayName(gid); // On récupère le nom du groupe

                            Platform.runLater(() -> {
                                addMessage("✓ Le groupe " + gName + " a été supprimé", false);
                                removeGroupButton(gid); // On enlève le bouton car le groupe n'existe plus
                            });
                            break;
                        }
                        case NOTIF_NICKNAME_CHANGED: {
                            int uid = buf.getInt();
                            int len = buf.getInt();
                            byte[] nameBytes = new byte[len];
                            buf.get(nameBytes);
                            String newName = new String(nameBytes, StandardCharsets.UTF_8); // on suppose que le pseudo
                                                                                            // est encodé en UTF-8
                                                                                            // Met à jour la map des
                                                                                            // nicknames !
                            client.getNicknamesMap().put(uid, newName);
                            Contact.sauvegarderContact(uid, newName); // On sauvegarde le nouveau pseudo dans la BDD pour le retrouver plus tard
                            Platform.runLater(() -> {
                            	
                            addMessage("✨ " + uid + " s'appelle maintenant " + newName, false); // on affiche la notification de changement de pseudo
                            contactList.getChildren().clear();
                            afficherListeContacts();
                            });                                                                                           

                        }
                        case NOTIF_ALL_NICKNAMES: {
                            int nb = buf.getInt();
                            for (int i = 0; i < nb; i++) {
                                int uid = buf.getInt();
                                int len = buf.getInt();
                                byte[] nameBytes = new byte[len];
                                buf.get(nameBytes);
                                String name = new String(nameBytes, StandardCharsets.UTF_8);
                                client.getNicknamesMap().put(uid, name);
                            }
                            Platform.runLater(() -> {
                                String monNom = client.displayName(client.getIdentifier());

                                statusLabel.setText("Connecté en tant que : " + monNom + " (ID : " + client.getIdentifier() + ")");
                                
                                contactList.getChildren().clear();
                                afficherListeContacts();

                            });
                            break;
                        }
                        case NOTIF_ERROR: {
                            int len = buf.getInt();
                            byte[] msg = new byte[len];
                            buf.get(msg);
                            // Ajoute pour éviter les caractères bizarres
                            Platform.runLater(
                                    () -> addMessage("✗ Erreur: " + new String(msg, StandardCharsets.UTF_8), false));
                            break;
                        }
                    }
                } else {
                    // --- RECEPTION DE MESSAGE NORMAL ---
                    String msg = new String(p.data);
                    String sender = client.displayName(p.srcId); // Utilise le pseudo

                    if (p.destId < 0) { // Si c'est un message de groupe
                        String groupName = client.displayName(p.destId);
                        // sauvegarde le message reçu
                        // history.saveMessage(p.srcId, p.destId, msg); // SAUVEGARDE DANS LA BDD
                        Platform.runLater(() -> addMessage("[" + groupName + "] " + sender + " : " + msg, false));
                    } else {
                        Platform.runLater(() -> addMessage(sender + " : " + msg, false));
                    }
                }
            });

            // affiche l'ID en haut à droite quand connecté
            client.addConnectionListener(active -> {
                Platform.runLater(() -> {
                    if (active) {
                    	int monId = client.getIdentifier();
                        statusLabel.setText("Connecté (ID: " + monId + ")");
                        chargerHistorique(monId); // Charge l'historique des messages depuis la BDD à la connexion
                        //Contact.insererContactsDeTest(); // Insère des contacts de test dans la BDD (à faire une seule fois)
                        afficherListeContacts(); // Affiche la liste des contacts à la connexion

                    } else {
                        statusLabel.setText("Déconnecté");
                    }
                });
            });

            client.startSession();

            // Demande tous les nicknames au serveur
            try {
                client.requestAllNicknames();
            } catch (IOException e) {
                System.out.println("Erreur requête nicknames : " + e.getMessage());
            }

        } catch (UnknownHostException e) {
            System.out.println("Erreur connexion serveur");
        }
    }

    // ####################################
    // FONCTION POUR AJOUTER UN MESSAGE DANS LA ZONE DE MESSAGES
    // ####################################

    private void addMessage(String text, boolean isMine) {
        HBox container = new HBox(); // conteneur pour aligner la bulle à gauche ou à droite

        Label bubble = new Label(text); // le texte du message dans une bulle
        bubble.setWrapText(true); // retour à la ligne automatique
        bubble.setMaxWidth(320); // limite la largeur de la bulle pour éviter qu'elle soit trop large
        bubble.setPadding(new Insets(8, 12, 8, 12)); // espace intérieur de la bulle (haut, droite, bas, gauche)
        bubble.setFont(Font.font("Arial", 13)); // police de caractère pour le message

        if (isMine) {
            // bulle bleu à droite
            bubble.setStyle("-fx-background-color: #F4C9D6; -fx-background-radius: 15 15 0 15;");
            container.setAlignment(Pos.CENTER_RIGHT); // aligne la bulle à droite
        } else {
            // bulle blanche à gauche
            bubble.setStyle("-fx-background-color: white; -fx-background-radius: 15 15 15 0;");
            container.setAlignment(Pos.CENTER_LEFT); // aligne la bulle à gauche
        }

        container.getChildren().add(bubble); // on ajoute la bulle au conteneur
        messagesBox.getChildren().add(container); // on ajoute le conteneur à la zone de messages

        // scroll automatique vers le bas
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }
    // #############################
    // Affichage de la liste de contacts
    // #############################
    
    private void afficherListeContacts() {
    	contactList.getChildren().clear();
        // On récupère la liste BDD
        List<Contact> contactsBdd = Contact.getAllContacts(); // ou Message.getAllContacts()
        
        System.out.println("Nombre de contacts trouvés dans la BDD : " + contactsBdd.size());
        // On tourne sur chaque contact
        for (Contact c : contactsBdd) {
            // On ne crée pas de bouton pour discuter avec soi-même !
            if (c.getId() == client.getIdentifier()) continue; 
            
            // On crée un joli bouton
            Button btnContact = new Button(c.getNickname());
            btnContact.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFDEE2; -fx-font-weight: bold;");
            
            // L'ACTION DU CLIC
            btnContact.setOnAction(e -> {
                changerDeConversation(c.getId());
            });
            
            // On ajoute le bouton à la barre de gauche
            contactList.getChildren().add(btnContact);
        }
    }
    
    // #############################
    // Affichage de l'historique
    // #############################
    private void addHistoryMessage(String text, boolean isMine) {
        HBox container = new HBox(); 
        Label bubble = new Label(text); 
        bubble.setWrapText(true); 
        bubble.setMaxWidth(320); 
        bubble.setPadding(new Insets(8, 12, 8, 12)); 
        bubble.setFont(Font.font("Arial", 13)); 

        if (isMine) {
            // Gris pour tes anciens messages (à droite)
            bubble.setStyle("-fx-background-color: #D3D3D3; -fx-background-radius: 15 15 0 15;");
            container.setAlignment(Pos.CENTER_RIGHT); 
        } else {
            // Gris plus clair pour les anciens messages des autres (à gauche)
            bubble.setStyle("-fx-background-color: #EBEBEB; -fx-background-radius: 15 15 15 0;");
            container.setAlignment(Pos.CENTER_LEFT); 
        }

        container.getChildren().add(bubble); 
        messagesBox.getChildren().add(container); 
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    } 
    
    private void chargerHistorique(int monId) {
        try {
            // 1. On récupère les objets bruts
            List<Archive> archives = Message.getMessages(monId); // (Ou getMessagesSpecifiques)
            
            for (Archive arc : archives) {
                // 2. Est-ce que c'est MOI l'expéditeur ?
                boolean isMine = (arc.senderId == monId);
                
                // 3. On cherche le pseudo grâce au travail de ton amie !
                String pseudo;
                if (isMine) {
                    pseudo = "Moi";
                } else {
                    // Cherche le pseudo dans la map, s'il n'existe pas, affiche "ID X"
                    pseudo = client.getNicknamesMap().getOrDefault(arc.senderId, "ID " + arc.senderId);
                }
                
                // 4. On dessine la bulle grise !
                addHistoryMessage("🕰️ " + pseudo + " : " + arc.content, isMine); 
            }
        } catch (Exception e) {
            System.out.println("Erreur lors du chargement de l'historique : " + e.getMessage());
        }
    }
    
    // ####################################
    // FONCTION POUR CHANGER DE DISCUSSION
    // ####################################
    private void changerDeConversation(int destId) {
        // On remplit automatiquement le champ "À :" en haut
        destField.setText(String.valueOf(destId));
        messagesBox.getChildren().clear();
        try {
            int monId = client.getIdentifier(); 

            List<Archive> archives = Message.getMessagesSpecifiques(monId, destId);
            
            for (Archive arc : archives) {
                boolean isMine = (arc.senderId == monId);
                String pseudo = isMine ? "Moi" : client.getNicknamesMap().getOrDefault(arc.senderId, "ID " + arc.senderId);
                
                if (arc.content.startsWith("[FICHIER]:")) {
                    // On découpe le mot "[FICHIER]:" pour ne garder que "image.png"
                    String nomFichier = arc.content.substring(10); 
                    
                    // On va chercher le fichier dans le dossier
                    java.io.File fichierLocal = new java.io.File("fichiers_recus", nomFichier);
                    
                    if (fichierLocal.exists()) {
                        // Le fichier est là, on l'affiche !
                        afficherFichierHistorique(fichierLocal, nomFichier, pseudo, isMine);
                    } else {
                        // Le fichier a été supprimé de l'ordinateur, on affiche un texte d'erreur
                        addHistoryMessage("🕰️ " + pseudo + " : ❌ [Fichier manquant] " + nomFichier, isMine);
                    }
                    
                } else { 
                addHistoryMessage("🕰️ " + pseudo + " : " + arc.content, isMine); 
            }
            }
        } catch (Exception e) {
            System.out.println("Erreur de changement de conversation : " + e.getMessage());
        }
    }

    // #############################
    // Parcourt la liste des boutons de groupes et supprime celui qui correspond à
    // l'ID
    // #############################
    private void removeGroupButton(int gid) {
        // On cherche le bouton qui a l'ID "btn-group-X"
        String targetId = "btn-group-" + gid;

        // On retire de la VBox groupList tout composant qui correspond à cet ID
        groupList.getChildren().removeIf(node -> targetId.equals(node.getId()));
    }

    private void recevoirFichierUI(fr.uga.miashs.dciss.chatservice.common.Packet p) {
        try {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(p.data);
            java.io.DataInputStream dis = new java.io.DataInputStream(bis);

            byte type = dis.readByte(); // lis le type
            int tailleNom = dis.readInt(); // lis la taille du nom

            byte[] nameBytes = new byte[tailleNom];
            dis.readFully(nameBytes);
            String nomFichier = new String(nameBytes);

            int tailleFichier = dis.readInt();
            byte[] fileBytes = new byte[tailleFichier];
            dis.readFully(fileBytes);

            // Sauvegarder le fichier
            java.io.File dossier = new java.io.File("fichiers_recus");
            if (!dossier.exists())
                dossier.mkdirs();
            java.io.File fichierRecu = new java.io.File(dossier, nomFichier);
            java.nio.file.Files.write(fichierRecu.toPath(), fileBytes);

            
           Message.insertMessage(p.srcId, client.getIdentifier(), "[FICHIER]:" + nomFichier);
        
            // Afficher dans l'interface selon le type
            String sender = client.displayName(p.srcId);
            Platform.runLater(() -> afficherFichier(fichierRecu, nomFichier, sender));

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void afficherFichier(java.io.File fichier, String nomFichier, String sender) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle("-fx-background-color: white; -fx-background-radius: 15 15 15 0;");
        bubble.setMaxWidth(320);

        // Nom de l'expéditeur
        Label senderLabel = new Label(sender);
        senderLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        senderLabel.setTextFill(Color.web("#3E2723"));
        bubble.getChildren().add(senderLabel);

        // Détection du type
        String ext = nomFichier.toLowerCase();
        if (ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".gif")) {
            // C'est une image ou un GIF - afficher directement
            try {
                javafx.scene.image.Image image = new javafx.scene.image.Image(fichier.toURI().toString());
                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(image);
                imageView.setFitWidth(300);
                imageView.setPreserveRatio(true);
                bubble.getChildren().add(imageView);
            } catch (Exception e) {
                bubble.getChildren().add(new Label("📎 " + nomFichier));
            }
        } else {
            // Autre type de fichier - afficher comme lien
            Label fileLabel = new Label("📎 " + nomFichier);
            fileLabel.setStyle("-fx-text-fill: #3E2723; -fx-underline: true; -fx-cursor: hand;");
            fileLabel.setOnMouseClicked(e -> {
                try {
                    java.awt.Desktop.getDesktop().open(fichier);
                } catch (Exception ex) {
                    System.out.println("Impossible d'ouvrir le fichier");
                }
            });
            bubble.getChildren().add(fileLabel);
        }

        container.getChildren().add(bubble);
        messagesBox.getChildren().add(container);

        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }

    private void afficherFichierEnvoye(java.io.File fichier, String nomFichier) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_RIGHT); // Aligné à DROITE car c'est moi qui envoie

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle("-fx-background-color: #F4C9D6; -fx-background-radius: 15 15 0 15;"); // Rose comme mes messages
        bubble.setMaxWidth(320);

        // Détection du type
        String ext = nomFichier.toLowerCase();
        if (ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".gif")) {
            // C'est une image ou un GIF - afficher directement
            try {
                javafx.scene.image.Image image = new javafx.scene.image.Image(fichier.toURI().toString());
                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(image);
                imageView.setFitWidth(300);
                imageView.setPreserveRatio(true);
                bubble.getChildren().add(imageView);
            } catch (Exception e) {
                bubble.getChildren().add(new Label("📎 " + nomFichier));
            }
        } else {
            // Autre type de fichier - afficher comme lien
            Label fileLabel = new Label("📎 " + nomFichier);
            fileLabel.setStyle("-fx-text-fill: #3E2723; -fx-underline: true; -fx-cursor: hand;");
            fileLabel.setOnMouseClicked(e -> {
                try {
                    java.awt.Desktop.getDesktop().open(fichier);
                } catch (Exception ex) {
                    System.out.println("Impossible d'ouvrir le fichier");
                }
            });
            bubble.getChildren().add(fileLabel);
        }

        container.getChildren().add(bubble);
        messagesBox.getChildren().add(container);

        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }

 // ####################################
    // AFFICHER UN FICHIER DE L'HISTORIQUE EN GRIS
    // ####################################
    private void afficherFichierHistorique(java.io.File fichier, String nomFichier, String sender, boolean isMine) {
        HBox container = new HBox();
        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setMaxWidth(320);
        
        if (isMine) {
            container.setAlignment(Pos.CENTER_RIGHT);
            bubble.setStyle("-fx-background-color: #D3D3D3; -fx-background-radius: 15 15 0 15;");
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            bubble.setStyle("-fx-background-color: #EBEBEB; -fx-background-radius: 15 15 15 0;");
            
            Label senderLabel = new Label("🕰️ " + sender);
            senderLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            senderLabel.setTextFill(Color.web("#3E2723"));
            bubble.getChildren().add(senderLabel);
        }
        
        // Détection du type
        String ext = nomFichier.toLowerCase();
        if (ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".gif")) {
            try {
                javafx.scene.image.Image image = new javafx.scene.image.Image(fichier.toURI().toString());
                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(image);
                imageView.setFitWidth(200); // Un peu plus petit pour l'historique
                imageView.setPreserveRatio(true);
                bubble.getChildren().add(imageView);
            } catch (Exception e) {
                bubble.getChildren().add(new Label("📎 " + nomFichier));
            }
        } else {
            Label fileLabel = new Label("📎 " + nomFichier);
            fileLabel.setStyle("-fx-text-fill: #3E2723; -fx-underline: true; -fx-cursor: hand;");
            fileLabel.setOnMouseClicked(e -> {
                try { java.awt.Desktop.getDesktop().open(fichier); } 
                catch (Exception ex) { System.out.println("Impossible d'ouvrir"); }
            });
            bubble.getChildren().add(fileLabel);
        }
        
        container.getChildren().add(bubble);
        messagesBox.getChildren().add(container);
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}