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
import javafx.application.Platform;
import java.net.UnknownHostException;
import javafx.scene.layout.Region;

public class ChatUI extends Application {

    private ClientMsg client;
    private VBox messagesBox;
    private ScrollPane scrollPane;
    private TextField inputField;
    private TextField destField;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {

        // ####################################
        // LE HEADER
        // ####################################

        HBox header = new HBox(); // aligne les éléments horizontalement
        header.setPadding(new Insets(15)); // espace intérieur
        header.setStyle("-fx-background-color: #007FFF;"); // bleu azur

        // Le titre
        Label titleLabel = new Label("📱 Chat");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18)); // police en gras et taille 18
        titleLabel.setTextFill(Color.WHITE); // couleur du texte en blanc

        // Après titleLabel dans le header

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS); // pousse le label à droite

        statusLabel = new Label("Connexion...");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        statusLabel.setTextFill(Color.WHITE);

        header.getChildren().addAll(titleLabel, spacer, statusLabel);

        BorderPane root = new BorderPane(); // layout principal avec haut/bas/gauche/droite/centre
        root.setTop(header); // on met le header en haut

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
        messagesBox.setStyle("-fx-background-color: #ECE5DD;"); // fond beige

        HBox destBar = new HBox(10); // barre pour saisir l'id du destinataire, 10 = espace entre les éléments
        destBar.setPadding(new Insets(8, 15, 8, 15));// espace intérieur (haut, droite, bas, gauche)
        destBar.setStyle("-fx-background-color: #187db4ff;"); // bleu plus foncé que le header pour différencier, avec
                                                              // un peu de transparence
        destBar.setAlignment(Pos.CENTER_LEFT); // aligne les éléments à gauche

        Label destLabel = new Label("À :"); // label pour indiquer que c'est le champ du destinataire
        destLabel.setTextFill(Color.WHITE); // couleur du texte en blanc
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
        scrollPane.setStyle("-fx-background-color: #ECE5DD; -fx-background: #ECE5DD;"); // même fond que messagesBox
                                                                                        // pour éviter les zones
                                                                                        // blanches

        root.setCenter(scrollPane); // on met la zone messages au centre

        // ####################################
        // LE BAS DE LA FENETRE
        // ####################################

        HBox bottomBar = new HBox(8); // aligne le champ et le bouton horizontalement, 8 = espace entre les éléments
        bottomBar.setPadding(new Insets(10)); // espace intérieur
        bottomBar.setStyle("-fx-background-color: #F0F0F0;"); // gris clair
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
                "-fx-background-color: #007FFF; -fx-text-fill: white; " +
                        "-fx-background-radius: 20; -fx-font-size: 16; -fx-padding: 5 15 5 15;"); // bleu azur, texte
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
        inputField.setOnAction(e -> { // même action que le bouton envoyer mais avec entrée
            String msg = inputField.getText().trim();
            if (!msg.isEmpty()) {
                addMessage(msg, true);
                inputField.clear();
            }
        });

        connectToServer(); // on se connecte au serveur après avoir mis en place l'interface pour pouvoir
                           // afficher les messages reçus
    }

    // ####################################
    // FONCTION POUR SE CONNECTER AU SERVEUR ET RECEVOIR LES MESSAGES
    // ####################################

    private void connectToServer() {
        try {
            client = new ClientMsg("localhost", 1666);

            // quand on reçoit un message, on l'affiche à gauche
            client.addMessageListener(p -> {
                String msg = new String(p.data);
                Platform.runLater(() -> addMessage("ID " + p.srcId + ": " + msg, false));
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
            bubble.setStyle("-fx-background-color: #90D5FF; -fx-background-radius: 15 15 0 15;");
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

    public static void main(String[] args) {
        launch(args);
    }
}