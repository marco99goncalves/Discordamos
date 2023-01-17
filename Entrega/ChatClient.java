import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;

public class ChatClient implements Runnable {

  static TreeSet<String> commands;

  static boolean IsCommand(String message) {
    Scanner sc = new Scanner(message);

    if (!sc.hasNext()) return false;

    String first = sc.next();
    return commands.contains(first);
  }

  // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
  JFrame frame = new JFrame("Chat Client");
  private JTextField chatBox = new JTextField();
  private JTextArea chatArea = new JTextArea();

  // --- Fim das variáveis relacionadas coma interface gráfica

  // Se for necessário adicionar variáveis ao objecto ChatClient, devem
  // ser colocadas aqui

  // Método a usar para acrescentar uma string à caixa de texto
  // * NÃO MODIFICAR *
  public void printMessage(final String message) {
    chatArea.append(message);
  }

  Socket connSocket;
  DataOutputStream outToServer;
  BufferedReader inFromServer;

  boolean clientActive = false;

  // Construtor
  public ChatClient(String server, int port) throws IOException {
    // Inicialização da interface gráfica --- * NÃO MODIFICAR *
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(chatBox);
    frame.setLayout(new BorderLayout());
    frame.add(panel, BorderLayout.SOUTH);
    frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
    frame.setSize(500, 500);
    frame.setVisible(true);
    chatArea.setEditable(false);
    chatBox.setEditable(true);
    chatBox.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            String message = chatBox.getText();
            newMessage(message);
          } catch (IOException ex) {
            //                    ex.printStackTrace();
          } finally {
            chatBox.setText("");
          }
        }
      }
    );
    frame.addWindowListener(
      new WindowAdapter() {
        public void windowOpened(WindowEvent e) {
          chatBox.requestFocusInWindow();
        }
      }
    );
    // --- Fim da inicialização da interface gráfica

    // Se for necessário adicionar código de inicialização ao
    // construtor, deve ser colocado aqui

    clientActive = true;
    // Initialize the TCP Connection
    connSocket = new Socket(server, port);
    outToServer = new DataOutputStream(connSocket.getOutputStream());
    inFromServer =
      new BufferedReader(new InputStreamReader(connSocket.getInputStream()));

    InitializeCommands();
  }

  private static void InitializeCommands() {
    commands = new TreeSet<>();
    commands.add("/nick");
    commands.add("/join");
    commands.add("/leave");
    commands.add("/bye");
    commands.add("/priv");
  }

  // Método invocado sempre que o utilizador insere uma mensagem
  // na caixa de entrada
  public void newMessage(String message) throws IOException {
    // PREENCHER AQUI com código que envia a mensagem ao servidor
    if (message == null || message.isEmpty()) return;
    try {
      if (message.charAt(0) == '/' && !IsCommand(message)) {
        message = '/' + message;
      }
      outToServer.write((message + "\n").getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      // e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  // Método principal do objecto
  public void run() {
    // PREENCHER AQUI
    String res = null;
    while (clientActive) {
      try {
        if (((res = inFromServer.readLine()) != null)) {
          String parsed = ParseMessage(res);
          printMessage(parsed);
        }
      } catch (IOException e) {
        // System.out.println("ERRO: " + res);
        // e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  }

  public String ParseMessage(String message) throws IOException {
    Scanner sc = new Scanner(message);

    if (!sc.hasNext()) return "";

    String first = sc.next();
    String result = "";
    switch (first) {
      case "MESSAGE":
        {
          String name = sc.next();
          String msg = sc.nextLine();
          result = name + ":" + msg;
          break;
        }
      case "JOINED":
        {
          String name = sc.next();
          result = name + " has joined the room";
          break;
        }
      case "NEWNICK":
        {
          String old_name = sc.next();
          String new_name = sc.next();
          result = old_name + " changed their nickname to " + new_name;
          break;
        }
      case "PRIVATE":
        {
          String name = sc.next();
          String msg = sc.nextLine();
          result = "[Private] " + name + ":" + msg;
          break;
        }
      case "ERROR":
        {
          result = "An error has occurred.";
          break;
        }
      case "LEFT":
        {
          String name = sc.next();
          result = name + " has left the room.";
          break;
        }
      case "BYE":
        {
          result = "Goodbye : (";
          clientActive = false;
          inFromServer.close();
          connSocket.close();
          System.exit(0);
          break;
        }
      case "OK":
        {
          result = "Success";
          break;
        }
      default:
        {
          result = first;
        }
    }

    return result + "\n";
  }

  // Instancia o ChatClient e arranca-o invocando o seu método run()
  // * NÃO MODIFICAR *
  public static void main(String[] args) throws IOException {
    ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
    client.run();
  }
}
