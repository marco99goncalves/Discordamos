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

    TreeSet<Room> rooms;
    static Room defaultRoom;

    static public void main(String args[]) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt(args[0]);

        // TODO Remove this room
        defaultRoom = new Room("DEFAULT");

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
                                key.cancel();

                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    System.out.println("Closing connection to " + s);
                                    s.close();
                                } catch (IOException ie) {
                                    System.err.println("Error closing socket " + s + ": " + ie);
                                }
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

        System.out.println(message + " " + message.contains("\n"));



        // TODO: Find a way to replace
        if (thisSelectionKey.attachment() == null) {
            ClientModel client = new ClientModel();
            client.setName(message.replace("\n", ""));
            client.setKey(thisSelectionKey);
            client.setRoom("DEFAULT");
            defaultRoom.clients.add(client);

            thisSelectionKey.attach(client);
            sc.write(StandardCharsets.US_ASCII.encode("OK\n"));
            buffer.clear();
            return true;
        }

        if (!message.contains("\n")) {
            // Buffer it because we don't have a new line, so it's not a complete message.
            ((ClientModel) thisSelectionKey.attachment()).buffer += message;
            buffer.clear();
            return true;
        }

        message = ((ClientModel)thisSelectionKey.attachment()).buffer + message;
        processMessage(message, selector.keys(), thisSelectionKey);


//        // Decode and print the message to stdout
//        Set<SelectionKey> keys = selector.keys();
//        Iterator<SelectionKey> it = keys.iterator();
//        while (it.hasNext()) {
//            SelectionKey key = it.next();
//            if (!key.isAcceptable()) {
//                SocketChannel s = (SocketChannel) key.channel();
//                ClientModel client = (ClientModel) sc.keyFor(selector).attachment();
//
//                String send = client.name + "> " + client.buffer + message;
////                System.out.println("FINAL_MESSAGE: " + send);
////                System.out.println("ola");
//                s.write(StandardCharsets.US_ASCII.encode(send));
//                buffer.rewind();
//            }
//
//        }

        ClientModel client = (ClientModel) sc.keyFor(selector).attachment();
        client.setBuffer("");

        buffer.clear();
        return true;
    }

    static void processMessage(String message, Set<SelectionKey> keys, SelectionKey senderKey){
        //Parse message
        if(IsCommand(message)){
            //Run the corresponding command
        }else{
            //It's a message,  broadcast it to all users in the room
            SendMessageToAllUsers(((ClientModel)senderKey.attachment()), message, defaultRoom);
        }
    }

    static void SendMessageToAllUsers(ClientModel sender, String message, Room room){
        for(ClientModel client : room.clients){
            SelectionKey key = client.getKey();
            if(!key.isAcceptable()){
                SocketChannel s = (SocketChannel) key.channel();
                String send = "MESSAGE " + sender.getName() + " " + message;
                try {
                    System.out.print("FINAL_MESSAGE: " + send + "ola");
                    s.write(StandardCharsets.US_ASCII.encode(send));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static boolean IsCommand(String message){
        return false;
    }
}