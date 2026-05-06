import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class ClienteGUI extends JFrame {
  private static final String HOST = "192.168.194.119";
  private static final int PUERTO = 6789;

  private Socket socket;
  private PrintWriter salida;
  private BufferedReader entrada;
  private String nombreUsuario;

  private JTextArea areaSalidaGeneral;
  private JLabel indicadorEstado;
  private Map<String, VentanaChat> chatsAbiertos = new HashMap<>();

  public ClienteGUI() {
    solicitarNombre();
    configurarVentanaPrincipal();
    conectar();
  }

  private void solicitarNombre() {
    nombreUsuario = JOptionPane.showInputDialog(this, "Ingrese su nombre de usuario:", "Registro", JOptionPane.PLAIN_MESSAGE);
    if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) System.exit(0);
  }

  private void configurarVentanaPrincipal() {
    setTitle("Terminal de Comandos - " + nombreUsuario);
    setSize(750, 600);
    // Cambiamos el comportamiento de la X para que use nuestro método de salida limpia
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent e) {
        salirDelSistema();
      }
    });

    setLayout(new BorderLayout());

    // Panel Superior: Estado
    JPanel panelEstado = new JPanel(new FlowLayout(FlowLayout.LEFT));
    indicadorEstado = new JLabel("● Desconectado");
    indicadorEstado.setForeground(Color.RED);
    panelEstado.add(indicadorEstado);
    add(panelEstado, BorderLayout.NORTH);

    // Centro: Salida de comandos
    areaSalidaGeneral = new JTextArea();
    areaSalidaGeneral.setEditable(false);
    areaSalidaGeneral.setBackground(new Color(240, 240, 240));
    areaSalidaGeneral.setFont(new Font("Monospaced", Font.PLAIN, 12));
    add(new JScrollPane(areaSalidaGeneral), BorderLayout.CENTER);

    limpiarYMostrarMenu();

    // Lateral: Menú de Botones (Derecha)
    JPanel panelMenu = new JPanel();
    // Ajustamos a 11 filas para incluir separador y botón Salir
    panelMenu.setLayout(new GridLayout(11, 1, 5, 5));
    panelMenu.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    JButton btnFecha = new JButton("Ver Fecha");
    JButton btnLista = new JButton("Lista Clientes");
    JButton btnProvincias = new JButton("Provincias");
    JButton btnResolver = new JButton("Resolver");
    JButton btnContar = new JButton("Contar");
    JButton btnPrivado = new JButton("Chat Privado");
    JButton btnGlobal = new JButton("Chat Global");
    JButton btnSalir = new JButton("Salir");

    // Estilo especial para el botón de salir
    btnSalir.setBackground(new Color(255, 204, 204));

    btnFecha.addActionListener(e -> enviar("FECHA"));
    btnLista.addActionListener(e -> enviar("LISTA"));
    btnProvincias.addActionListener(e -> enviar("PROVINCIAS"));
    btnResolver.addActionListener(e -> abrirVentanaOperacion("RESOLVER"));
    btnContar.addActionListener(e -> abrirVentanaOperacion("CONTAR"));
    btnPrivado.addActionListener(e -> {
      String destino = JOptionPane.showInputDialog("¿Con quién quieres hablar?");
      if (destino != null && !destino.trim().isEmpty()) {
        obtenerVentanaChat(destino).setVisible(true);
      }
    });
    btnGlobal.addActionListener(e -> obtenerVentanaChat("TODOS").setVisible(true));
    btnSalir.addActionListener(e -> salirDelSistema());

    panelMenu.add(btnFecha);
    panelMenu.add(btnLista);
    panelMenu.add(btnProvincias);
    panelMenu.add(new JSeparator());
    panelMenu.add(btnResolver);
    panelMenu.add(btnContar);
    panelMenu.add(new JSeparator());
    panelMenu.add(btnPrivado);
    panelMenu.add(btnGlobal);
    panelMenu.add(new JSeparator());
    panelMenu.add(btnSalir);

    add(panelMenu, BorderLayout.EAST);

    // Panel Inferior: Botón Limpiar
    JPanel panelInferior = new JPanel(new FlowLayout(FlowLayout.CENTER));
    JButton btnLimpiar = new JButton("Limpiar Pantalla");
    btnLimpiar.setPreferredSize(new Dimension(150, 30));
    btnLimpiar.addActionListener(e -> limpiarYMostrarMenu());
    panelInferior.add(btnLimpiar);
    add(panelInferior, BorderLayout.SOUTH);
  }

  private void salirDelSistema() {
    int confirmar = JOptionPane.showConfirmDialog(this,
        "¿Estás seguro de que deseas salir?", "Confirmar Salida",
        JOptionPane.YES_NO_OPTION);

    if (confirmar == JOptionPane.YES_OPTION) {
      enviar("SALIR"); // Avisa al servidor
      System.exit(0);  // Cierra la aplicación
    }
  }

  private void limpiarYMostrarMenu() {
    String menuHeader = "============================================\n" +
        "  Tu usuario: " + nombreUsuario + "\n" +
        "============================================\n\n" +
        "  MENU                     - Muestra este menu\n" +
        "  FECHA                    - Muestra fecha y hora\n" +
        "  LISTA                    - Lista clientes conectados\n" +
        "  RESOLVER                 - Ej: RESOLVER \"45*23/54+234\"\n" +
        "  CONTAR                   - Ej: CONTAR \"Hola mundo\"\n" +
        "  PROVINCIAS               - Lista provincias de Argentina\n" +
        "  CHAT PRIVADO             - Envia mensaje a un cliente\n" +
        "  CHAT GLOBAL              - Envia mensaje a todos\n" +
        "  SALIR                    - Desconectarse\n\n" +
        "--------------------------------------------\n";
    areaSalidaGeneral.setText(menuHeader);
  }

  private void conectar() {
    try {
      Charset charset = Charset.defaultCharset();
      socket = new Socket(HOST, PUERTO);
      salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset), true);
      entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));

      salida.println("NOMBRE " + nombreUsuario);

      indicadorEstado.setText("● Conectado a " + HOST);
      indicadorEstado.setForeground(new Color(0, 150, 0));

      new Thread(this::escucharServidor).start();
    } catch (IOException e) {
      areaSalidaGeneral.append("[ERROR] No se pudo conectar: " + e.getMessage() + "\n");
    }
  }

  private void escucharServidor() {
    try {
      String linea;
      while ((linea = entrada.readLine()) != null) {
        final String msj = linea;
        SwingUtilities.invokeLater(() -> procesarMensajeServidor(msj));
      }
    } catch (IOException e) {
      SwingUtilities.invokeLater(() -> {
        indicadorEstado.setText("● Desconectado");
        indicadorEstado.setForeground(Color.RED);
      });
    }
  }

  private void procesarMensajeServidor(String msj) {
    if (msj.contains(" -> TODOS]")) {
      obtenerVentanaChat("TODOS").recibir(msj);
    } else if (msj.contains(" -> " + nombreUsuario + "]")) {
      String remitente = msj.substring(1, msj.indexOf(" -> "));
      obtenerVentanaChat(remitente).recibir(msj);
    } else if (msj.startsWith("Resultado de") || msj.startsWith("Texto: \"")) {
      JOptionPane.showMessageDialog(this, msj, "Resultado de Operación", JOptionPane.INFORMATION_MESSAGE);
    } else if (
        !msj.startsWith("---") &&
            !msj.contains("Bienvenido") &&
            !msj.contains("Tu usuario:") &&
            !msj.contains("============================================") &&
            !msj.contains(" - ")
    ) {
      areaSalidaGeneral.append(msj + "\n");
      areaSalidaGeneral.setCaretPosition(areaSalidaGeneral.getDocument().getLength());
    }
  }

  private void abrirVentanaOperacion(String comando) {
    String input = JOptionPane.showInputDialog(this, "Ingrese el dato para " + comando + ":");
    if (input != null && !input.trim().isEmpty()) {
      enviar(comando + " \"" + input + "\"");
    }
  }

  private void enviar(String texto) {
    if (salida != null) salida.println(texto);
  }

  private VentanaChat obtenerVentanaChat(String id) {
    return chatsAbiertos.computeIfAbsent(id, k -> new VentanaChat(id));
  }

  private class VentanaChat extends JFrame {
    private JTextArea area;
    private JTextField input;
    private String destino;

    public VentanaChat(String destino) {
      this.destino = destino;
      setTitle(destino.equals("TODOS") ? "Chat Global" : "Chat con " + destino);
      setSize(400, 300);
      setLayout(new BorderLayout());

      area = new JTextArea();
      area.setEditable(false);
      area.setFont(new Font("SansSerif", Font.PLAIN, 12));
      add(new JScrollPane(area), BorderLayout.CENTER);

      input = new JTextField();
      input.addActionListener(e -> {
        String msj = input.getText().trim();
        if (!msj.isEmpty()) {
          String cmd = destino.equals("TODOS") ? "*ALL \"" + msj + "\"" : "*" + destino + " \"" + msj + "\"";
          enviar(cmd);
          area.append("[Tú -> " + destino + "] " + msj + "\n");
          input.setText("");
        }
      });
      add(input, BorderLayout.SOUTH);
    }

    public void recibir(String msj) {
      area.append(msj + "\n");
      area.setCaretPosition(area.getDocument().getLength());
      if (!isVisible()) setVisible(true);
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new ClienteGUI().setVisible(true));
  }
}