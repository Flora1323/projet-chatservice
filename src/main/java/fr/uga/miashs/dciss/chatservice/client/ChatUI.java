package fr.uga.miashs.dciss.chatservice.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.net.UnknownHostException;
import javafx.scene.layout.Region;
import java.nio.ByteBuffer;
import static fr.uga.miashs.dciss.chatservice.common.MessageType.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static fr.uga.miashs.dciss.chatservice.common.MessageType.*;

public class ChatUI extends Application {

    private ClientMsg client;
    private VBox messagesBox;
    private ScrollPane scrollPane;
    private TextField inputField;
    private TextField destField;
    private Label statusLabel;
    private VBox groupList;
    private VBox groupList;
    private Map<Integer, String> groupNames = new HashMap<>();
    // On crée un dictionnaire qui associe un ID de groupe à un nom personnalisé
    private java.util.Map<Integer, String> customGroupNames = new java.util.HashMap<>();
    // Une variable temporaire pour stocker le nom qu'on vient de taper
    private String lastCreatedGroupName;

    @Override
    public void start(Stage stage) {

        // ####################################
        // LE HEADER
        // ####################################

        HBox header = new HBox(); // aligne les éléments horizontalement
        header.setPadding(new Insets(15)); // espace intérieur
        header.setStyle("-fx-background-color: #3E2723;"); // Le titre
        Label titleLabel = new Label("💅 Baddies");
        header.setStyle("-fx-background-color: #3E2723;"); // Le titre
        Label titleLabel = new Label("💅 Baddies");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18)); // police en gras et taille 18
        titleLabel.setTextFill(Color.web("#FFDEE2")); // couleur rose
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
        nickDialog.setContentText("Choisis ton petit nom de Baddies :");

        nickDialog.showAndWait().ifPresent(nick -> {
            connectToServer(); // On initialise le client
            try {
                // On demande au serveur de nous attribuer ce pseudo
                client.requestSetNickname(nick);
                stage.setTitle("Chat - " + nick);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // PANNEAU GAUCHE
        VBox leftPanel = new VBox(10);
        leftPanel.setPrefWidth(150);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setStyle("-fx-background-color: #3E2723;");

        Label groupTitle = new Label("Groupes");
        groupTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        groupTitle.setTextFill(Color.WHITE);

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
            // On peut demander "Nom:ID1,ID2"
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Format : NomDuGroupe : ID1,ID2");

            dialog.showAndWait().ifPresent(input -> {
                try {
                    String[] parts = input.split(":");

                    if (parts.length < 2)
                        throw new Exception("Format incorrect");

                    String gName = parts[0];
                    String[] ids = parts[1].split(",");
                    int[] members = new int[ids.length];
                    for (int i = 0; i < ids.length; i++)
                        members[i] = Integer.parseInt(ids[i].trim());

                    client.requestCreateGroup(gName, members); // Envoie le nom et les membres au serveur

                    this.lastCreatedGroupName = gName; // Stocke le nom du groupe qu'on vient de créer pour l'utiliser
                                                       // dans la notification

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
        // blanc, arrondi,
        // taille de police
        // 16, padding

        bottomBar.getChildren().addAll(inputField, sendButton); // on ajoute le champ et le bouton à la barre du bas
        root.setBottom(bottomBar); // on met la barre en bas

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
                addMessage(msg, true);
                inputField.clear();
            } catch (NumberFormatException ex) {
                System.out.println("ID invalide");
            }
        });
    }

    private Button createGroupButton(int gid) {
        // On récupère le nom dans la Map, sinon on met "Groupe ID"
        String name = customGroupNames.getOrDefault(gid, "Groupe " + gid);

        Button groupBtn = new Button(name);
        groupBtn.setId("btn-group-" + gid);
        groupBtn.setStyle("-fx-background-color: #F4C9D6; -fx-text-fill: #3E2723; -fx-background-radius: 10;");
        groupBtn.setMaxWidth(Double.MAX_VALUE);

        // Clic gauche : définit la destination du message
        groupBtn.setOnAction(ev -> destField.setText(String.valueOf(gid)));

        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();

        javafx.scene.control.MenuItem addMember = new javafx.scene.control.MenuItem("➕ Ajouter un membre");
        javafx.scene.control.MenuItem removeMember = new javafx.scene.control.MenuItem("➖ Exclure un membre");
        javafx.scene.control.MenuItem deleteGroup = new javafx.scene.control.MenuItem("🗑 Supprimer le groupe");
        javafx.scene.control.MenuItem leaveGroup = new javafx.scene.control.MenuItem("🚪 Quitter le groupe");

        // Action : Ajouter un membre
        addMember.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Ajouter un membre au groupe " + gid);
            dialog.showAndWait().ifPresent(uid -> {
                try {
                    client.requestAddMember(gid, Integer.parseInt(uid.trim()));
                } catch (Exception ex) {
                    System.out.println("ID invalide");
                }
            });
        });

        // Action : Exclure un membre
        removeMember.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Exclure un membre du groupe " + gid);
            dialog.showAndWait().ifPresent(uid -> {
                try {
                    client.requestRemoveMember(gid, Integer.parseInt(uid.trim()));
                } catch (Exception ex) {
                    System.out.println("ID invalide");
                }
            });
        });

        // Action : Supprimer le groupe
        deleteGroup.setOnAction(e -> {
            try {
                client.requestDeleteGroup(gid);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // Action : Quitter le groupe
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

    private Button createGroupButton(int gid) {
        Button groupBtn = new Button("Groupe " + gid);
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
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Exclure un membre du groupe " + gid);
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
                if (p.srcId == 0 && p.data != null && p.data.length >= 1) {
                    ByteBuffer buf = ByteBuffer.wrap(p.data);
                    byte type = buf.get();
                    switch (type) {
                        case NOTIF_GROUP_CREATED: {
                            int gid = buf.getInt();
                            // Lire le nom envoyé par le serveur (ASSURE-TOI QUE LE SERVEUR L'ENVOIE BIEN !)
                            int nameLen = buf.getInt();
                            byte[] nameBytes = new byte[nameLen];
                            buf.get(nameBytes);
                            String gName = new String(nameBytes, StandardCharsets.UTF_8);

                            // Maintenant que le getter existe, cette ligne va fonctionner !
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
                            System.out.println("NOTIF_MEMBER_ADDED reçu : gid=" + gid + " uid=" + uid + " monId="
                                    + client.getIdentifier());
                            Platform.runLater(() -> {
                                addMessage("✓ User " + uid + " ajouté au groupe " + gid, false);
                                if (uid == client.getIdentifier()) {
                                    groupList.getChildren().add(createGroupButton(gid));
                                }
                            });
                            break;
                        }
                        case NOTIF_MEMBER_REMOVED: {
                            int gid = buf.getInt();
                            int uid = buf.getInt();
                            Platform.runLater(() -> {
                                addMessage("✓ User " + uid + " retiré du groupe " + gid, false);
                                // SI C'EST MOI qui suis retiré (ou qui ai quitté), j'enlève le bouton
                                if (uid == client.getIdentifier()) {
                                    removeGroupButton(gid);
                                }
                            });
                            break;
                        }
                        case NOTIF_GROUP_DELETED: {
                            int gid = buf.getInt();
                            Platform.runLater(() -> {
                                addMessage("✓ Groupe " + gid + " supprimé", false);
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
                            Platform.runLater(() -> addMessage("✨ " + uid + " s'appelle maintenant " + newName, false)); // on
                                                                                                                         // affiche
                                                                                                                         // la
                                                                                                                         // notification
                                                                                                                         // de
                                                                                                                         // changement
                                                                                                                         // de
                                                                                                                         // pseudo
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
                    String msg = new String(p.data);
                    String sender = client.displayName(p.srcId); // Utilise le pseudo

                    if (p.destId < 0) { // Si c'est un message de groupe
                        String groupName = client.displayName(p.destId);
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
                        statusLabel.setText("ID : " + client.getIdentifier());
                    } else {
                        statusLabel.setText("Déconnecté");
                    }
                });
            });

            client.startSession();

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
    // Parcourt la liste des boutons de groupes et supprime celui qui correspond à
    // l'ID
    // #############################
    private void removeGroupButton(int gid) {
        // On cherche le bouton qui a l'ID "btn-group-X"
        String targetId = "btn-group-" + gid;

        // On retire de la VBox groupList tout composant qui correspond à cet ID
        groupList.getChildren().removeIf(node -> targetId.equals(node.getId()));
    }

    public static void main(String[] args) {
        launch(args);
    }
}