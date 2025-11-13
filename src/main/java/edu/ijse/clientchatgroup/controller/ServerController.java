package edu.ijse.clientchatgroup.controller;

import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class ServerController implements Initializable {
    public ScrollPane scrollPane;
    public TextField msgInput;
    public Button sendBtn;
    public VBox chatDisplay;
    public Label clientCountLabel;

    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private boolean isServerRunning = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        startServer();
    }

    private void startServer() {
        if (isServerRunning) {
            showAlert("Server is already running!");
            return;
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(3000);
                isServerRunning = true;
                System.out.println("Group Chat Server started. Waiting for clients...");
                Platform.runLater(() -> displaySystemMessage("Server started on port 3000"));

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();

                    DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                    String username = dis.readUTF();

                    ClientHandler clientHandler = new ClientHandler(clientSocket, username);
                    clients.put(username, clientHandler);

                    Platform.runLater(() -> {
                        clientCountLabel.setText("Connected Clients: " + clients.size());
                        displaySystemMessage(username + " joined the chat");
                    });

                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                if (!isServerRunning) {
                    return;
                }
                e.printStackTrace();
                Platform.runLater(() -> displaySystemMessage("Server error: " + e.getMessage()));
            }
        }).start();
    }

    public void sendMsg(javafx.event.ActionEvent actionEvent) {
        try {
            String message = msgInput.getText().trim();
            if (!message.isEmpty()) {
                String serverMessage = "Server: " + message;
                broadcastMessage("TEXT", serverMessage, null, "server");
                displayMsg(message, "server");
                msgInput.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error sending message: " + e.getMessage());
        }
    }

    private void displayMsg(String inputMsg, String send) {
        HBox bubble = new HBox();
        Label msg = new Label(inputMsg);
        msg.setWrapText(true);

        if (send.equals("server")) {
            msg.setStyle("-fx-background-color: #34c759; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 15;");
            bubble.setAlignment(Pos.BASELINE_RIGHT);
            bubble.getChildren().add(msg);
        } else {
            HBox messageWithSend = new HBox();
            messageWithSend.setSpacing(5);
            messageWithSend.setAlignment(Pos.CENTER_LEFT);

            Label senderLabel = new Label(send + ":");
            senderLabel.setStyle("-fx-text-fill: #666; -fx-font-weight: bold; -fx-padding: 0 5 0 0;");

            Label messageLabel = new Label(inputMsg);
            messageLabel.setWrapText(true);
            messageLabel.setStyle("-fx-background-color: #4a90e2; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 15;");

            messageWithSend.getChildren().addAll(senderLabel, messageLabel);
            bubble.getChildren().add(messageWithSend);
            bubble.setAlignment(Pos.BASELINE_LEFT);
        }

        chatDisplay.getChildren().add(bubble);
        scrollPane.setVvalue(1.0);
    }



    private void displaySystemMessage(String message) {
        HBox bubble = new HBox();
        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setStyle("-fx-background-color: #666666; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 15; -fx-font-style: italic;");
        bubble.getChildren().add(msg);
        bubble.setAlignment(Pos.CENTER);
        chatDisplay.getChildren().add(bubble);
        scrollPane.setVvalue(1.0);
    }


    private void broadcastMessage(String type, String message, byte[] imageBytes, String send) throws IOException {
        for (ClientHandler client : clients.values()) {
            if (client.isConnected()) {
                client.sendMessage(type, message, imageBytes);
            }
        }
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
            alert.show();
        });
    }

    public void stopServer() {
        try {
            isServerRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients.values()) {
                client.disconnect();
            }
            clients.clear();
            Platform.runLater(() -> {
                clientCountLabel.setText("Connected Clients: 0");
                displaySystemMessage("Server stopped");
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private DataOutputStream dOS;
        private DataInputStream dIS;
        private String username;

        public ClientHandler(Socket socket, String username) {
            this.socket = socket;
            this.username = username;
            try {
                this.dOS = new DataOutputStream(socket.getOutputStream());
                this.dIS = new DataInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (!socket.isClosed() && isConnected()) {
                    try {
                        String type = dIS.readUTF();

                        if (type.equals("TEXT")) {
                            String message = dIS.readUTF();
                            String formattedMessage = username + ": " + message;

                            for (ClientHandler client : clients.values()) {
                                if (!client.username.equals(this.username) && client.isConnected()) {
                                    client.sendMessage("TEXT", formattedMessage, null);
                                }
                            }

                            Platform.runLater(() -> displayMsg(message, username));

                        }
                    } catch (IOException e) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }

        public void sendMessage(String type, String message, byte[] imageBytes) throws IOException {
            if (isConnected()) {
                dOS.writeUTF(type);
                if (type.equals("TEXT")) {
                    dOS.writeUTF(message);
                } else if (type.equals("IMAGE")) {
                    dOS.writeInt(imageBytes.length);
                    dOS.write(imageBytes);
                }
                dOS.flush();
            }
        }

        public boolean isConnected() {
            return socket != null && !socket.isClosed() && socket.isConnected();
        }

        private void disconnect() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                clients.remove(username);
                Platform.runLater(() -> {
                    clientCountLabel.setText("Connected Clients: " + clients.size());
                    displaySystemMessage(username + " left the chat");
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        stopServer();
    }
}