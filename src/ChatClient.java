import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatClient {
     static final String SERVER_ADDRESS = "localhost";
     static final int SERVER_PORT = 1234;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginWindow());
    }
}

class LoginWindow extends JFrame {
    private JTextField usernameField;

    public LoginWindow() {
        setTitle("Login");
        setSize(300, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        usernameField = new JTextField(15);
        JButton loginButton = new JButton("Login");
        loginButton.setBackground(new Color(19, 171, 216));
        loginButton.setForeground(Color.WHITE);
        panel.add(new JLabel("Enter Username:"));
        panel.add(usernameField);
        panel.add(loginButton);

        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            new ChatClientWindow(username);
            dispose();
        });

        add(panel);
        setLocationRelativeTo(null);
        setVisible(true);
    }
}

class ChatClientWindow extends JFrame {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    public JPanel chatArea;
    private JTextField messageField;
    private JPanel friendsPanel;
    private int selectedFriendId = -1;
    private JButton logoutButton;

    public ChatClientWindow(String username) {
        setTitle("Chat");
        setSize(600, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Chat area
        chatArea = new JPanel();
        //chatArea.setEditable(false);
        chatArea.setLayout(new BoxLayout(chatArea, BoxLayout.Y_AXIS));
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        JLabel welcomeLabel = new JLabel("Welcome, " + username + "!", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 18));
        welcomeLabel.setForeground(new Color(0, 102, 204));

        // Message input

        JButton sendButton;
        messageField = new JTextField(30) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Set padding inside the text field
                ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
        };
        messageField.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16)); // Set modern font
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 2), // Gray border
                BorderFactory.createEmptyBorder(8, 8, 8, 8) // Padding inside
        ));
        messageField.setBackground(new Color(245, 245, 245)); // Light gray background

// Style the send button with rounded corners
        sendButton = new JButton("Send") {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isOpaque() && getBackground() != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); // Rounded corners with a radius of 20
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        sendButton.setOpaque(false);
        sendButton.setContentAreaFilled(false);
        sendButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 204), 2), // Blue border
                BorderFactory.createEmptyBorder(5, 10, 5, 10) // Inner padding
        ));
        sendButton.setBackground(new Color(0, 102, 204)); // Deep blue background
        sendButton.setForeground(Color.WHITE); // White text
        sendButton.setFont(new Font("Arial", Font.BOLD, 14)); // Bold modern font

        JButton emojiButton = new JButton("😊");
        emojiButton.setPreferredSize(new Dimension(50, 30));
        emojiButton.setBackground(new Color(255, 250, 200)); // Light yellow background
        emojiButton.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
        emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        // Emoji picker (popup)
        JPopupMenu emojiPicker = new JPopupMenu();
        String[] emojis = {"😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊", "😍", "😘", "😜"};
        for (String emoji : emojis) {
            JMenuItem emojiItem = new JMenuItem(emoji);
            emojiItem.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            emojiPicker.add(emojiItem);

            // Add action listener to insert emoji into the input field
            emojiItem.addActionListener(e -> messageField.setText(messageField.getText() + emoji));
        }
        emojiButton.addActionListener(e -> emojiPicker.show(emojiButton, 0, emojiButton.getHeight()));
        sendButton.addActionListener(e -> sendMessage());

        JPanel inputPanel = new JPanel();

        inputPanel.add(messageField);
        inputPanel.add(emojiButton);
        inputPanel.add(sendButton);

        // Friends list
        friendsPanel = new JPanel();
        friendsPanel.setLayout(new BoxLayout(friendsPanel, BoxLayout.Y_AXIS));
        friendsPanel.setBackground(new Color(240, 248, 255));
        JScrollPane friendsScrollPane = new JScrollPane(friendsPanel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, friendsScrollPane, chatScrollPane);
        splitPane.setDividerLocation(200);
        logoutButton = new JButton("Logout");
        logoutButton.setPreferredSize(new Dimension(100, 30)); // Optional: Set the size of the logout button
        logoutButton.setBackground(new Color(102, 219, 255)); // Optional: Set a background color for the button
        logoutButton.setForeground(Color.WHITE); // Optional: Set the text color
        logoutButton.setFont(new Font("Arial", Font.BOLD, 12)); // Optional: Set font style
        logoutButton.setBorder(BorderFactory.createLineBorder(new Color(150, 0, 200), 2));
        logoutButton.addActionListener(e -> logout());
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // Use FlowLayout to align components in a row
        topPanel.add(welcomeLabel);
        topPanel.add(logoutButton);
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);
        connectToServer(username);
        setVisible(true);
    }

    private void connectToServer(String username) {
        try {
            socket = new Socket(ChatClient.SERVER_ADDRESS, ChatClient.SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(username);

            // Listen for server messages
            new Thread(() -> {
                try {
                    String serverResponse;
                    while ((serverResponse = in.readLine()) != null) {
                        if (serverResponse.startsWith("Friends List:")) {
                            loadFriendsList();
                        } else if (serverResponse.equals("ChatHistoryStart")) {
                            chatArea.removeAll(); // Clear previous messages
                            chatArea.revalidate();
                            chatArea.repaint();
                        } else if (serverResponse.equals("ChatHistoryEnd")) {
                            // End of chat history
                        } else if (serverResponse.startsWith("You")) {
                            if(serverResponse.endsWith("r"))
                                addMessage(serverResponse, true,true);
                            else
                                addMessage(serverResponse, true,false);
                            // Add received message
                        } else {
                            if(serverResponse.endsWith("r"))
                                addMessage(serverResponse, false,true);
                            else
                                addMessage(serverResponse, false,false); // Fallback for other messages
                        }
                    }
                } catch (IOException e) {

                    System.out.println("Exception:logout");
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFriendsList() {
        try {
            String line;
            friendsPanel.removeAll();
            while (!(line = in.readLine()).isEmpty() && line.contains(":")) {
                System.out.println(line);

                String[] parts = line.split(":");

                int friendId = Integer.parseInt(parts[0].trim());
                String friendName = parts[1].trim();

                /*JButton friendButton = new JButton(friendName);
                Border border=BorderFactory.createLineBorder(Color.WHITE);
                friendButton.setBorder(border);
                friendButton.setBackground(Color.BLUE);
                friendButton.setForeground(Color.WHITE);
                friendButton.setMargin(new Insets(10,10,10,10));
                friendButton.addActionListener(e -> selectFriend(friendId));
                friendsPanel.add(friendButton);*/
                friendsPanel.setLayout(new BoxLayout(friendsPanel, BoxLayout.Y_AXIS)); // Vertical layout

                JButton friendButton = new JButton(friendName);

// Set the maximum size to ensure the button stretches horizontally
                friendButton.setMaximumSize(new Dimension(100, 40)); // Full width, with height 40

// Add a border with padding for better aesthetics
                Border border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(51, 102, 255,200), 2), // Border color (darker blue)
                        BorderFactory.createEmptyBorder(5, 10, 5, 10) // Inner padding
                );
                friendButton.setBorder(border);

// Background and foreground colors
                friendButton.setBackground(new Color(0, 153, 255,128)); // Bright cyan for contrast
                friendButton.setForeground(Color.WHITE);

// Font customization
                friendButton.setFont(new Font("Arial", Font.BOLD, 16)); // Bold Arial font with size 16

// Add action listener for friend selection
                friendButton.addActionListener(e -> selectFriend(friendId));

// Add spacing between buttons
                friendsPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Space between buttons
                friendsPanel.add(friendButton);


            }
            friendsPanel.revalidate();
            friendsPanel.repaint();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void selectFriend(int friendId) {
        selectedFriendId = friendId;
        out.println("CHAT_HISTORY:" + friendId);
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && selectedFriendId != -1) {
            out.println(selectedFriendId + ":" + message);
            messageField.setText("");
        }
    }
   /* private void appendMessageToChatArea(String message) {
        SwingUtilities.invokeLater(() -> {
            // Check if the message contains the word "you"
            boolean isBlueMessage = message.toLowerCase().contains("You");

            // Create a StyledDocument for the chatArea's document
            StyledDocument doc = chatArea.getStyledDocument();

            // Create styles for the blue and black backgrounds with white text
            Style blueStyle = chatArea.addStyle("BlueStyle", null);
            StyleConstants.setBackground(blueStyle, Color.BLUE);
            StyleConstants.setForeground(blueStyle, Color.WHITE);


            Style blackStyle = chatArea.addStyle("BlackStyle", null);
            StyleConstants.setBackground(blackStyle, Color.BLACK);
            StyleConstants.setForeground(blackStyle, Color.WHITE);

            try {
                // Apply the appropriate style based on the message content
                if (isBlueMessage) {
                    doc.insertString(doc.getLength(), message + "\n", blueStyle);
                } else {
                    doc.insertString(doc.getLength(), message + "\n", blackStyle);
                }

                // Auto-scroll to the bottom
                chatArea.setCaretPosition(doc.getLength());
            } catch ( BadLocationException e) {
                e.printStackTrace();
            }
        });
    }*/
   private void addMessage(String message, boolean isSender,boolean isRetrived) {
       // Set chatArea layout to BoxLayout for vertical stacking (do this once in the constructor)
       if (chatArea.getLayout() == null) {
           chatArea.setLayout(new BoxLayout(chatArea, BoxLayout.Y_AXIS));
       }

       // Create a JPanel for the message bubble
       JPanel messagePanel = new JPanel();
       messagePanel.setLayout(new BorderLayout());
       messagePanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
       messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
       JLabel messageLabel;
       if(!isRetrived) {// Ensure proper alignment
           LocalDateTime currentTime = LocalDateTime.now();

           // Format the date and time
           DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
           String formattedDateTime = currentTime.format(formatter);

           // Create a JLabel for the message
           messageLabel = new JLabel("<html><body style='width: 250px;'>" + message + "<br>"+formattedDateTime+"</body></html>");
           messageLabel.setOpaque(true);
       }
       else{
           message=message.substring(0,message.length()-1);
           String[] messages=message.split("2024");
           messageLabel = new JLabel("<html><body style='width: 250px;'>" + messages[0] +"<br>"+"2024"+messages[1]+"</body></html>");
           messageLabel.setOpaque(true);
       }

       // Set alignment and color based on sender or receiver
       if (isSender) {
           messageLabel.setBackground(new Color(173, 216, 230)); // Light Blue for sender
           messagePanel.add(messageLabel, BorderLayout.EAST);   // Align to the right
       } else {
           messageLabel.setBackground(new Color(210, 240, 240)); // Light Gray for receiver
           messagePanel.add(messageLabel, BorderLayout.WEST);   // Align to the left
       }

       messageLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

       // Add the message panel to the chat area
       chatArea.add(messagePanel);
       chatArea.add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing between messages
       chatArea.revalidate();
       chatArea.repaint();

       // Scroll to the bottom
       JScrollBar vertical = ((JScrollPane) chatArea.getParent().getParent()).getVerticalScrollBar();
       vertical.setValue(vertical.getMaximum());
   }
    private void logout() {
        try {
            out.println("LOGOUT"); // Notify the server
            socket.close(); // Close the connection
            this.dispose(); // Close the GUI
            System.out.println("Logged out successfully.");
        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Error during logout.");
        }
    }
}
