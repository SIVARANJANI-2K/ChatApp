import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 1234;
    private static Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running and waiting for connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private int userId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Set up I/O streams
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // User authentication
                if (!authenticateUser()) {
                    closeConnection();
                    return;
                }

                // Add user to online users
                onlineUsers.put(username, this);
                System.out.println(username + " is online.");

                // Notify the client of their friends
                sendFriendsList();

                // Handle messages
                String input;
                while ((input = in.readLine()) != null) {
                    processMessage(input);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeConnection();
            }
        }

        private boolean authenticateUser() throws IOException {

            username = in.readLine();

            try (Connection conn = DatabaseConnection.getConnection()) {
                String query = "SELECT id FROM users WHERE username = ?";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    userId = rs.getInt("id");
                    out.println("Login successful! Welcome "+username);
                    return true;
                } else {
                    out.println("Username not found. Connection closed.");
                    return false;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Database error. Connection closed.");
                return false;
            }
        }

        private void sendFriendsList() {
            try (Connection conn = DatabaseConnection.getConnection()) {
                String query = """
                        SELECT users.id, users.username
                        FROM user_friends
                        JOIN users ON user_friends.friend_id = users.id
                        WHERE user_friends.user_id = ?;
                        """;
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                out.println("Friends List:");
                while (rs.next()) {
                    out.println(rs.getInt("id") + ": " + rs.getString("username"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error retrieving friends list.");
            }
        }
        private void sendChatHistory(int recipientId) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                String query = """
                                SELECT messages.sender_id, messages.message, messages.msg_id, users.username AS sender_name,messages.timestamp 
                                FROM messages
                                JOIN users ON messages.sender_id = users.id
                                WHERE (messages.sender_id = ? AND messages.receiver_id = ?)
                                OR (messages.sender_id = ? AND messages.receiver_id = ?)
                                ORDER BY messages.msg_id ASC;
                """;
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setInt(1, userId);
                stmt.setInt(2, recipientId);
                stmt.setInt(3, recipientId);
                stmt.setInt(4, userId);
                ResultSet rs = stmt.executeQuery();

                out.println("ChatHistoryStart");
                while (rs.next()) {
                    String sender = rs.getInt("sender_id") == userId ? "You" : rs.getString("sender_name");;
                    String message = rs.getString("message");
                    String timestamp=rs.getTimestamp("timestamp").toString();
                    out.println(sender + ": " + message+" "+timestamp+"r");
                }
                out.println("ChatHistoryEnd");
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error retrieving chat history.");
            }
        }

        // Handle request for chat history
        private void processMessage(String input) {
            try {
                if (input.startsWith("CHAT_HISTORY")) {
                    int recipientId = Integer.parseInt(input.split(":")[1].trim());
                    sendChatHistory(recipientId);
                } else {
                    String[] parts = input.split(":", 2);
                    if (parts.length < 2) {
                        out.println("Invalid message format.");
                        return;
                    }

                    int recipientId = Integer.parseInt(parts[0].trim());
                    String message = parts[1].trim();

                    saveMessage(recipientId, message);

                    for (ClientHandler client : onlineUsers.values()) {
                        if (client.userId == recipientId) {
                            client.out.println(username + ": " + message);
                            out.println("You:"+message);
                            return;
                        }
                    }
                    out.println("You" + ": " + message);
                    out.println("Message sent (recipient offline).");
                }
            } catch (Exception e) {
                e.printStackTrace();
                out.println("Error processing message.");
            }
        }




        private void saveMessage(int recipientId, String message) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                String query = "INSERT INTO messages (sender_id, receiver_id, message) VALUES (?, ?, ?)";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setInt(1, userId);
                stmt.setInt(2, recipientId);
                stmt.setString(3, message);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error saving message.");
            }
        }

        private void closeConnection() {
            try {
                if (username != null) {
                    onlineUsers.remove(username);
                    System.out.println(username + " is offline.");
                }

                if (socket != null) socket.close();
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
