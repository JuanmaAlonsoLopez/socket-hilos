import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

  // Modelo para la lista desplegable de usuarios
  private DefaultComboBoxModel<String> modeloUsuarios = new DefaultComboBoxModel<>();

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
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent e) {
        salirDelSistema();
      }
    });

    setLayout(new BorderLayout());

    JPanel panelEstado = new JPanel(new FlowLayout(FlowLayout.LEFT));
    indicadorEstado = new JLabel("● Desconectado");
    indicadorEstado.setForeground(Color.RED);
    panelEstado.add(indicadorEstado);
    add(panelEstado, BorderLayout.NORTH);

    areaSalidaGeneral = new JTextArea();
    areaSalidaGeneral.setEditable(false);
    areaSalidaGeneral.setBackground(new Color(240, 240, 240));
    areaSalidaGeneral.setFont(new Font("Monospaced", Font.PLAIN, 12));
    add(new JScrollPane(areaSalidaGeneral), BorderLayout.CENTER);

    limpiarYMostrarMenu();

    JPanel panelMenu = new JPanel();
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

    btnSalir.setBackground(new Color(255, 204, 204));

    btnFecha.addActionListener(e -> enviar("FECHA"));
    btnLista.addActionListener(e -> enviar("LISTA"));
    btnProvincias.addActionListener(e -> enviar("PROVINCIAS"));
    btnResolver.addActionListener(e -> abrirVentanaOperacion("RESOLVER"));
    btnContar.addActionListener(e -> abrirVentanaOperacion("CONTAR"));

    // --- NUEVA LÓGICA DE CHAT PRIVADO CON LISTA DESPLEGABLE ---
    btnPrivado.addActionListener(e -> {
      // Primero solicitamos la lista actualizada al servidor
      enviar("LISTA");

      // Pequeña validación por si la lista está vacía (solo tú conectado)
      if (modeloUsuarios.getSize() == 0) {
        JOptionPane.showMessageDialog(this, "No hay otros usuarios conectados actualmente.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
        return;
      }

      JComboBox<String> comboUsuarios = new JComboBox<>(modeloUsuarios);
      int seleccion = JOptionPane.showConfirmDialog(this, comboUsuarios, "Selecciona el usuario para chatear:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

      if (seleccion == JOptionPane.OK_OPTION) {
        String destino = (String) comboUsuarios.getSelectedItem();
        if (destino != null) {
          obtenerVentanaChat(destino).setVisible(true);
        }
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
      enviar("SALIR");
      System.exit(0);
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
    // Analizar la respuesta de LISTA del servidor para llenar el ComboBox
    if (msj.contains("Clientes conectados (")) {
      modeloUsuarios.removeAllElements();
    } else if (msj.matches("^\\s+\\d+\\.\\s+.+\\s+\\(IP:.*\\)")) {
      // Extraemos el nombre: está entre el ". " y el " (IP:"
      String nombre = msj.substring(msj.indexOf(". ") + 2, msj.indexOf(" (IP:")).trim();
      if (!nombre.equals(nombreUsuario)) {
        modeloUsuarios.addElement(nombre);
      }
    }

    if (msj.startsWith("[") && msj.contains(" -> ")) {
      String remitente = msj.substring(1, msj.indexOf(" -> "));
      String resto = msj.substring(msj.indexOf("]") + 1).trim();

      if (msj.contains(" -> TODOS]")) {
        obtenerVentanaChat("TODOS").recibir(remitente, resto);
      } else if (msj.contains(" -> " + nombreUsuario + "]")) {
        obtenerVentanaChat(remitente).recibir(remitente, resto);
      }
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
    private JTextPane area;
    private JTextField input;
    private String destino;
    private Map<String, Color> coloresUsuarios = new HashMap<>();
    private Random random = new Random();

    public VentanaChat(String destino) {
      this.destino = destino;
      setTitle(destino.equals("TODOS") ? "Chat Global" : "Chat con " + destino);
      setSize(400, 450);
      setLayout(new BorderLayout());

      area = new JTextPane();
      area.setEditable(false);
      area.setBackground(new Color(235, 235, 235));
      add(new JScrollPane(area), BorderLayout.CENTER);

      input = new JTextField();
      input.setFont(new Font("SansSerif", Font.PLAIN, 14));
      input.addActionListener(e -> {
        String msj = input.getText().trim();
        if (!msj.isEmpty()) {
          String cmd = destino.equals("TODOS") ? "*ALL \"" + msj + "\"" : "*" + destino + " \"" + msj + "\"";
          enviar(cmd);
          agregarMensaje("Tú", msj, true);
          input.setText("");
        }
      });

      JPanel panelSur = new JPanel(new BorderLayout());
      panelSur.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      panelSur.add(input, BorderLayout.CENTER);
      add(panelSur, BorderLayout.SOUTH);
    }

    public void recibir(String remitente, String cuerpo) {
      agregarMensaje(remitente, cuerpo, false);
      if (!isVisible()) setVisible(true);
    }

    private void agregarMensaje(String remitente, String cuerpo, boolean enviadoPorMi) {
      StyledDocument doc = area.getStyledDocument();

      SimpleAttributeSet alineacion = new SimpleAttributeSet();
      StyleConstants.setAlignment(alineacion, enviadoPorMi ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);

      SimpleAttributeSet estiloNombre = new SimpleAttributeSet();
      StyleConstants.setBold(estiloNombre, true);

      if (enviadoPorMi) {
        StyleConstants.setForeground(estiloNombre, new Color(0, 128, 0));
      } else if (destino.equals("TODOS")) {
        StyleConstants.setForeground(estiloNombre, obtenerColorUsuario(remitente));
      } else {
        StyleConstants.setForeground(estiloNombre, Color.BLUE);
      }

      SimpleAttributeSet estiloCuerpo = new SimpleAttributeSet();
      StyleConstants.setForeground(estiloCuerpo, Color.BLACK);
      StyleConstants.setBold(estiloCuerpo, false);

      try {
        int lengthBefore = doc.getLength();
        doc.insertString(doc.getLength(), "[" + remitente + "]: ", estiloNombre);
        doc.insertString(doc.getLength(), cuerpo + "\n\n", estiloCuerpo);
        doc.setParagraphAttributes(lengthBefore, doc.getLength() - lengthBefore, alineacion, false);
        area.setCaretPosition(doc.getLength());
      } catch (BadLocationException e) {
        e.printStackTrace();
      }
    }

    private Color obtenerColorUsuario(String usuario) {
      return coloresUsuarios.computeIfAbsent(usuario, k -> {
        return new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180));
      });
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new ClienteGUI().setVisible(true));
  }
}