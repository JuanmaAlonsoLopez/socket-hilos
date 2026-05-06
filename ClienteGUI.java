import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
      Charset charset = StandardCharsets.UTF_8;
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

  // --- INTERFAZ DE CHAT MEJORADA TIPO WHATSAPP ---
  private class VentanaChat extends JFrame {
    private JTextPane area; // Se reemplaza JTextArea por JTextPane
    private JTextField input;
    private String destino;

    public VentanaChat(String destino) {
      this.destino = destino;
      setTitle(destino.equals("TODOS") ? "Chat Global" : "Chat con " + destino);
      setSize(400, 450); // Ligeramente más grande para mayor comodidad
      setLayout(new BorderLayout());

      area = new JTextPane();
      area.setEditable(false);
      area.setBackground(new Color(230, 230, 230)); // Fondo estilo app de mensajería
      add(new JScrollPane(area), BorderLayout.CENTER);

      input = new JTextField();
      input.setFont(new Font("SansSerif", Font.PLAIN, 14));
      input.addActionListener(e -> {
        String msj = input.getText().trim();
        if (!msj.isEmpty()) {
          String cmd = destino.equals("TODOS") ? "*ALL \"" + msj + "\"" : "*" + destino + " \"" + msj + "\"";
          enviar(cmd);
          // Como este mensaje lo mandas tú, lo alineamos a la derecha
          agregarMensaje("[Tú] " + msj, true);
          input.setText("");
        }
      });

      JPanel panelSur = new JPanel(new BorderLayout());
      panelSur.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      panelSur.add(input, BorderLayout.CENTER);
      add(panelSur, BorderLayout.SOUTH);
    }

    public void recibir(String msj) {
      // Como este mensaje llega desde el servidor, lo alineamos a la izquierda
      agregarMensaje(msj, false);
      if (!isVisible()) setVisible(true);
    }

    // Método que gestiona la magia de los colores y la alineación
    private void agregarMensaje(String texto, boolean enviadoPorMi) {
      StyledDocument doc = area.getStyledDocument();

      // Atributos de alineación (Derecha o Izquierda)
      SimpleAttributeSet alineacion = new SimpleAttributeSet();
      StyleConstants.setAlignment(alineacion, enviadoPorMi ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);

      // Atributos de fuente y color
      SimpleAttributeSet fuente = new SimpleAttributeSet();
      StyleConstants.setFontFamily(fuente, "SansSerif");
      StyleConstants.setFontSize(fuente, 13);
      if (enviadoPorMi) {
        StyleConstants.setForeground(fuente, new Color(0, 100, 0)); // Verde oscuro para mensajes propios
        StyleConstants.setBold(fuente, true);
      } else {
        StyleConstants.setForeground(fuente, Color.BLACK); // Negro para mensajes recibidos
        StyleConstants.setBold(fuente, false);
      }

      try {
        int length = doc.getLength();
        doc.insertString(length, texto + "\n\n", fuente); // Inserta texto
        doc.setParagraphAttributes(length, texto.length() + 2, alineacion, false); // Aplica alineación
        area.setCaretPosition(doc.getLength()); // Auto-scroll
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new ClienteGUI().setVisible(true));
  }
}