import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteGUI extends JFrame {

  // Parámetros de conexión hardcodeados
  private static final String HOST = "192.168.194.119"; // Cambia esto por la IP de tu servidor
  private static final int PUERTO = 6789;        // Cambia esto por tu puerto

  // Componentes de la interfaz
  private JTextArea areaChat;
  private JTextField campoEntrada;
  private JButton botonEnviar;
  private JLabel indicadorEstado;

  // Sockets y flujos de datos
  private Socket socket;
  private PrintWriter salida;
  private BufferedReader entrada;
  private String nombreUsuario;

  public ClienteGUI() {
    // Pedir el nombre de usuario antes de armar la ventana
    nombreUsuario = JOptionPane.showInputDialog(this, "Ingrese su nombre de usuario:", "Ingreso", JOptionPane.PLAIN_MESSAGE);
    if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
      System.exit(0); // Si cancela, cerramos la app
    }

    configurarVentana();
    conectarAlServidor();
  }

  private void configurarVentana() {
    setTitle("Cliente de Chat y Comandos - " + nombreUsuario);
    setSize(500, 400);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    // Panel Superior: Indicador de estado
    JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT));
    indicadorEstado = new JLabel("🔴 Desconectado");
    indicadorEstado.setForeground(Color.RED);
    indicadorEstado.setFont(new Font("Arial", Font.BOLD, 14));
    panelSuperior.add(indicadorEstado);
    add(panelSuperior, BorderLayout.NORTH);

    // Centro: Área de chat
    areaChat = new JTextArea();
    areaChat.setEditable(false);
    areaChat.setLineWrap(true);
    areaChat.setFont(new Font("Monospaced", Font.PLAIN, 14));
    JScrollPane scrollPane = new JScrollPane(areaChat);
    add(scrollPane, BorderLayout.CENTER);

    // Panel Inferior: Entrada de texto y botón
    JPanel panelInferior = new JPanel(new BorderLayout());
    campoEntrada = new JTextField();
    botonEnviar = new JButton("Enviar");

    // Acción al presionar "Enviar" o darle "Enter" al campo de texto
    ActionListener accionEnviar = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        enviarMensaje();
      }
    };
    botonEnviar.addActionListener(accionEnviar);
    campoEntrada.addActionListener(accionEnviar);

    panelInferior.add(campoEntrada, BorderLayout.CENTER);
    panelInferior.add(botonEnviar, BorderLayout.EAST);
    add(panelInferior, BorderLayout.SOUTH);
  }

  private void conectarAlServidor() {
    try {
      socket = new Socket(HOST, PUERTO);
      salida = new PrintWriter(socket.getOutputStream(), true);
      entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

      // Actualizar estado visual
      indicadorEstado.setText("🟢 Conectado a " + HOST);
      indicadorEstado.setForeground(new Color(0, 153, 0));

      // Enviar el nombre de usuario apenas nos conectamos
      salida.println(nombreUsuario);

      // Iniciar el hilo para escuchar los mensajes del servidor
      Thread hiloEscucha = new Thread(new EscucharServidor());
      hiloEscucha.start();

    } catch (IOException e) {
      indicadorEstado.setText("🔴 Error de conexión");
      areaChat.append("Error al conectar con el servidor: " + e.getMessage() + "\n");
    }
  }

  private void enviarMensaje() {
    String mensaje = campoEntrada.getText().trim();
    if (!mensaje.isEmpty() && salida != null) {
      // Enviar el comando o mensaje al servidor
      salida.println(mensaje);

      // Si quieres que el usuario vea lo que él mismo escribió, descomenta la siguiente línea:
      // areaChat.append("Tú: " + mensaje + "\n");

      campoEntrada.setText("");
      campoEntrada.requestFocus(); // Devolver el foco al campo de texto
    }
  }

  // Clase interna (Hilo) para recibir mensajes sin congelar la interfaz Swing
  private class EscucharServidor implements Runnable {
    @Override
    public void run() {
      try {
        String mensajeRecibido;
        while ((mensajeRecibido = entrada.readLine()) != null) {
          // SwingUtilities asegura que actualizamos la interfaz gráfica de forma segura
          final String msj = mensajeRecibido;
          SwingUtilities.invokeLater(() -> {
            areaChat.append(msj + "\n");
            // Auto-scroll hacia abajo
            areaChat.setCaretPosition(areaChat.getDocument().getLength());
          });
        }
      } catch (IOException e) {
        SwingUtilities.invokeLater(() -> {
          indicadorEstado.setText("🔴 Desconectado");
          indicadorEstado.setForeground(Color.RED);
          areaChat.append("\n[Conexión cerrada por el servidor]\n");
        });
      }
    }
  }

  public static void main(String[] args) {
    // Iniciar la GUI en el hilo de eventos de Swing (buenas prácticas)
    SwingUtilities.invokeLater(() -> {
      ClienteGUI gui = new ClienteGUI();
      gui.setVisible(true);
    });
  }
}