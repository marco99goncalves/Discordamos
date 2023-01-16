import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class Server {
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    // Decoder for incoming text -- assume UTF-8

    static TreeMap<String, Room> rooms;
    static TreeMap<String, ClientModel> chosenNicks;
    static TreeSet<String> commands;

    static public void main(String args[]) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt(args[0]);

        // TODO Remove this room
        rooms = new TreeMap<>();
        chosenNicks = new TreeMap<>();

        InitializeCommands();

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            while (true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                int num = selector.select();

                // If we don't have any activity, loop around and wait again
                if (num == 0) {
                    continue;
                }

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    // Get a key representing one of bits of I/O activity
                    SelectionKey key = it.next();

                    // What kind of activity is it?
                    if (key.isAcceptable()) {

                        // It's an incoming connection. Register this socket with
                        // the Selector so we can listen for input on it
                        Socket s = ss.accept();
                        System.out.println("Got connection from " + s);

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();

                        sc.configureBlocking(false);

                        // Register it with the selector, for reading
                        sc.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {

                        SocketChannel sc = null;

                        try {
                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel) key.channel();
                            boolean ok = processInput(sc, selector, key);

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                RunByeCommand(keys, key, false);
                            }

                        } catch (IOException ie) {

                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                sc.close();
                            } catch (IOException ie2) {
                                ie2.printStackTrace();
                            }

                            System.out.println("Closed " + sc);
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    private static void InitializeCommands() {
        commands = new TreeSet<>();
        commands.add("/nick");
        commands.add("/join");
        commands.add("/leave");
        commands.add("/bye");
        commands.add("/priv");
    }

    // Just read the message from the socket and send it to stdout
    static private boolean processInput(SocketChannel sc, Selector selector, SelectionKey thisSelectionKey)
            throws IOException {
        // Read the message to the buffer
        buffer.clear();
        sc.read(buffer);
        buffer.flip();

        String message = decoder.decode(buffer).toString();
        buffer.flip();

        // message = message.replace("\n", "").replace("\r", "");

        // If no data, close the connection
        if (buffer.limit() == 0) {
            return false;
        }

        if (thisSelectionKey.attachment() == null) {
            ClientModel client = new ClientModel();
            client.setKey(thisSelectionKey);

            thisSelectionKey.attach(client);
            buffer.clear();
        }

        ClientModel client = (ClientModel) thisSelectionKey.attachment();
        if (!message.contains("\n")) {
            // Buffer it because we don't have a new line, so it's not a complete message.
            client.buffer += message;
            buffer.clear();
            return true;
        }

        message = client.buffer + message;
        processMessage(message, selector.keys(), thisSelectionKey);

        client.setBuffer("");

        buffer.clear();
        return true;
    }

    static void processMessage(String message, Set<SelectionKey> keys, SelectionKey senderKey) {
        // Parse message
        ClientModel client = (ClientModel) senderKey.attachment();

        if (IsCommand(message)) {
            RunCommand(message, keys, senderKey);
        } else {
            if (client.getName().equals("") || client.getRoom() == null) {
                SendMessageToUser("ERROR\n", senderKey);
                return;
            }
            // It's a message, broadcast it to all users in the room
            SendMessageToAllUsers("MESSAGE " + client.name + " " + message, client.room);
        }
    }

    static void SendMessageToAllUsers(String message, Room room) {
        for (ClientModel client : room.clients) {
            SelectionKey key = client.getKey();
            if (!key.isAcceptable()) {
                SocketChannel s = (SocketChannel) key.channel();
                try {
                    s.write(ByteBuffer.wrap(message.getBytes()));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static void SendMessageToUser(String message, SelectionKey receiverKey) {
        if (!receiverKey.isAcceptable()) {
            SocketChannel s = (SocketChannel) receiverKey.channel();
            try {
                s.write(ByteBuffer.wrap(message.getBytes()));
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    static void SendMessageToAllButSender(String message, Room room, SelectionKey receiverKey) {
        if (room == null)
            return;

        for (ClientModel client : room.clients) {
            SelectionKey key = client.getKey();
            if (key == receiverKey) // Ignore the sender
                continue;

            if (!key.isAcceptable()) {
                SocketChannel s = (SocketChannel) key.channel();
                try {
                    s.write(ByteBuffer.wrap(message.getBytes()));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static boolean IsCommand(String message) {
        Scanner sc = new Scanner(message);

        if (!sc.hasNext())
            return false;

        String first = sc.next();
        return commands.contains(first);
    }

    static void RunCommand(String command, Set<SelectionKey> keys, SelectionKey senderKey) {
        Scanner sc = new Scanner(command);
        String first = sc.next();
        ClientModel client = (ClientModel) senderKey.attachment();

        if (client.getRoom() == null)
            if (!first.equals("/nick") && !first.equals("/bye") && !first.equals("/join") && !first.equals("/priv")) {
                SendMessageToUser("ERROR\n", senderKey);
                return;
            }

        switch (first) {
            case "/nick" -> {
                String newNick = sc.next();
                RunNickCommand(newNick, keys, senderKey);
            }RunByeCommand
            case "/join" -> {
                String roomName = sc.next();
                RunJoinCommand(roomName, keys, senderKey);
            }
            case "/leave" -> {
                RunLeaveCommand(keys, senderKey);
            }
            case "/bye" -> {
                RunByeCommand(keys, senderKey, true);
            }
            case "/priv" -> {
                String receiverNick = sc.next();
                String message = sc.nextLine();
                message = message.stripLeading() + "\n";
                RunPrivCommand(keys, senderKey, receiverNick, message);
            }
        }
    }

    static void RunPrivCommand(Set<SelectionKey> keys, SelectionKey senderKey, String receiverNick, String message) {
        ClientModel client = (ClientModel) senderKey.attachment();

        if (client.getName().isEmpty() || !chosenNicks.containsKey(receiverNick)) {
            SendMessageToUser("ERROR\n", senderKey);
            return;
        }

        ClientModel receiver = chosenNicks.get(receiverNick);
        String msg = "PRIVATE " + client.getName() + " " + message;
        SendMessageToUser(msg, receiver.getKey());

        SendMessageToUser("OK\n", senderKey);
    }

    static void RunNickCommand(String newNick, Set<SelectionKey> keys, SelectionKey senderKey) {
        if (chosenNicks.containsKey(newNick)) {
            // User is already in use
            SendMessageToUser("ERROR\n", senderKey);
            return;
        }

        ClientModel client = (ClientModel) senderKey.attachment();
        String oldNick = client.getName();
        client.setName(newNick);

        chosenNicks.remove(oldNick);
        chosenNicks.put(newNick, client);

        SendMessageToAllButSender("NEWNICK " + oldNick + " " + newNick + "\n", client.room, senderKey);
        SendMessageToUser("OK\n", senderKey);
    }

    static void RunJoinCommand(String roomName, Set<SelectionKey> keys, SelectionKey senderKey) {
        ClientModel client = (ClientModel) senderKey.attachment();

        if (client.getName().isEmpty()) {
            SendMessageToUser("ERROR\n", senderKey);
            return;
        }

        if (client.getRoom() != null) {
            // We're in a room
            String message = "LEFT " + client.getName() + "\n";
            SendMessageToAllButSender(message, client.getRoom(), senderKey);
        }

        if (!rooms.containsKey(roomName)) {
            Room newRoom = new Room(roomName);
            rooms.put(roomName, newRoom);
        }

        client.setRoom(rooms.get(roomName));
        SendMessageToUser("OK\n", senderKey);

        String message = "JOINED " + client.getName() + "\n";
        SendMessageToAllButSender(message, client.getRoom(), senderKey);
    }

    static void RunLeaveCommand(Set<SelectionKey> keys, SelectionKey senderKey) {
        ClientModel client = (ClientModel) senderKey.attachment();
        Room oldRoom = client.getRoom();
        client.setRoom(null);
        oldRoom.clients.remove(client);

        SendMessageToAllButSender("LEFT " + client.name + "\n", oldRoom, senderKey);
        SendMessageToUser("OK\n", senderKey);
    }

    static void RunByeCommand(Set<SelectionKey> keys, SelectionKey senderKey, boolean sendBye) {
        ClientModel client = (ClientModel) senderKey.attachment();
        if (client.getRoom() != null) {
            client.getRoom().clients.remove(client);
            SendMessageToAllButSender("LEFT " + client.name + "\n", client.getRoom(), senderKey);
        }
        chosenNicks.remove(client.getName());

        if (sendBye)
            SendMessageToUser("BYE\n", senderKey);

        senderKey.cancel();
        try {
            senderKey.channel().close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}