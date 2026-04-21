import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor Socket - Segunda Parte: Comunicacion entre Clientes
 * Autor: Alonso Juan Manuel
 * 
 * Servidor mejorado que:
 * - Gestiona multiples clientes simultaneamente con hilos
 * - Permite comunicacion entre clientes (*NOMBRE "mensaje" o *ALL "mensaje")
 * - Asigna nombres unicos a cada cliente
 * - Mantiene un log detallado de todas las operaciones
 * 
 * Comandos soportados:
 *   MENU                      -> Muestra el menu de ayuda
 *   FECHA                     -> Muestra la fecha y hora actual
 *   LISTA                     -> Lista los clientes conectados
 *   RESOLVER "expresion"      -> Resuelve una expresion matematica
 *   CONTAR "texto"            -> Cuenta palabras, vocales y consonantes
 *   PROVINCIAS                -> Lista las 24 provincias de Argentina
 *   *NOMBRE "mensaje"         -> Envia un mensaje a un cliente especifico
 *   *ALL "mensaje"            -> Envia un mensaje a todos los clientes
 *   SALIR                     -> Cierra la conexion con el cliente
 */
public class Servidor {

    private static final int PUERTO = 6789;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String ARCHIVO_LOG = "servidor.log";
    
    // Gestor thread-safe de clientes conectados
    private static final ConcurrentHashMap<String, ClienteConectado> clientesConectados = new ConcurrentHashMap<>();
    private static volatile int contadorNombres = 1;
    
    private static final String[] PROVINCIAS = {
        "Buenos Aires", "Catamarca", "Chaco", "Chubut", "Cordoba",
        "Corrientes", "Entre Rios", "Formosa", "Jujuy", "La Pampa",
        "La Rioja", "Mendoza", "Misiones", "Neuquen", "Rio Negro",
        "Salta", "San Juan", "San Luis", "Santa Cruz", "Santa Fe",
        "Santiago del Estero", "Tierra del Fuego", "Tucuman",
        "Ciudad Autonoma de Buenos Aires"
    };
    
    // =========================================================================
    // Clase interna para representar un cliente conectado
    // =========================================================================
    private static class ClienteConectado {
        String nombre;
        String ip;
        PrintWriter salida;
        
        ClienteConectado(String nombre, String ip, PrintWriter salida) {
            this.nombre = nombre;
            this.ip = ip;
            this.salida = salida;
        }
    }
    
    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) throws IOException {
        // ServerSocket serverSocket = new ServerSocket(PUERTO);
		ServerSocket serverSocket = new ServerSocket(PUERTO, 50, InetAddress.getByName("0.0.0.0"));
        
        imprimirYLoguear("============================================");
        imprimirYLoguear("  SERVIDOR INICIADO EN EL PUERTO " + PUERTO);
        imprimirYLoguear("============================================");
        imprimirYLoguear("Esperando conexiones de clientes...");
        imprimirYLoguear("Nota: Puedes conectar clientes desde otras maquinas via ZeroTier");
        imprimirYLoguear("      usando la direccion IP virtual asignada por ZeroTier");
        imprimirYLoguear("Hora inicio: " + LocalDateTime.now().format(FORMATO_FECHA));
        imprimirYLoguear("");

        // Acepta clientes en bucle (un hilo por cliente)
        while (true) {
            try {
                Socket socketCliente = serverSocket.accept();
                String ipCliente = socketCliente.getInetAddress().getHostAddress();
                imprimirYLoguear("[CONEXION] Nuevo cliente desde IP: " + ipCliente);
                
                // Crear hilo para manejar este cliente
                Thread hilo = new Thread(() -> manejarCliente(socketCliente));
                hilo.setName("Cliente-" + ipCliente);
                hilo.start();
            } catch (IOException e) {
                imprimirYLoguear("[ERROR] Error al aceptar conexion: " + e.getMessage());
            }
        }
    }
    
    // =========================================================================
    // Manejo de cliente en su propio hilo
    // =========================================================================
    private static void manejarCliente(Socket socket) {
        String ip = socket.getInetAddress().getHostAddress();
        ClienteConectado cliente = null;
        Charset charset = Charset.defaultCharset();

        try (
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), charset));
            PrintWriter salida = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), charset), true)
        ) {
            // Esperar el primer mensaje del cliente (debe ser NOMBRE usuario)
            String primerMensaje = entrada.readLine();
            String nombreCliente;

            if (primerMensaje != null && primerMensaje.toUpperCase().startsWith("NOMBRE ")) {
                // El cliente envio su nombre
                String nombreSolicitado = primerMensaje.substring(7).trim();
                nombreCliente = asignarNombreUnico(nombreSolicitado, salida);
            } else {
                // Fallback: asignar nombre automatico
                nombreCliente = asignarNombreUnico("Usuario", salida);
                // Procesar el primer mensaje si no era NOMBRE
                if (primerMensaje != null && !primerMensaje.trim().isEmpty()) {
                    String respuesta = procesarComando(primerMensaje.trim(), nombreCliente);
                    salida.println(respuesta);
                }
            }

            cliente = new ClienteConectado(nombreCliente, ip, salida);
            clientesConectados.put(nombreCliente, cliente);

            imprimirYLoguear("[CLIENTE] " + nombreCliente + " (" + ip + ") - CONECTADO");

            // Mensaje de bienvenida
            salida.println("============================================");
            salida.println("  Bienvenido al Servidor Socket!");
            salida.println("  Tu usuario: " + nombreCliente);
            salida.println("============================================");
            mostrarMenu(salida);

            String mensajeRecibido;
            while ((mensajeRecibido = entrada.readLine()) != null && !mensajeRecibido.trim().isEmpty()) {
                mensajeRecibido = mensajeRecibido.trim();

                // Log en consola del servidor
                imprimirYLoguear("[LOG " + nombreCliente + "] >>> " + mensajeRecibido);

                // Procesador de comando
                if (mensajeRecibido.equalsIgnoreCase("SALIR")) {
                    salida.println("Hasta luego! Conexion cerrada correctamente.");
                    imprimirYLoguear("[CLIENTE] " + nombreCliente + " - DESCONECTADO");
                    break;
                } else {
                    String respuesta = procesarComando(mensajeRecibido, nombreCliente);
                    salida.println(respuesta);
                }
            }
            
        } catch (IOException e) {
            if (cliente != null) {
                imprimirYLoguear("[ERROR] Cliente " + cliente.nombre + " (" + ip + "): " + e.getMessage());
            } else {
                imprimirYLoguear("[ERROR] Cliente desconocido (" + ip + "): " + e.getMessage());
            }
        } finally {
            // Remover cliente de la lista
            if (cliente != null) {
                clientesConectados.remove(cliente.nombre);
                imprimirYLoguear("[CLIENTE] " + cliente.nombre + " - REMOVIDO DE LA LISTA");
            }
            try {
                socket.close();
            } catch (IOException e) {
                // Ignorar
            }
        }
    }
    
    // =========================================================================
    // Asignar nombre unico a cada cliente
    // =========================================================================
    private static String asignarNombreUnico(String nombreBase, PrintWriter salida) {
        synchronized (Servidor.class) {
            String nombreFinal = nombreBase;
            int contador = 1;
            
            while (clientesConectados.containsKey(nombreFinal)) {
                nombreFinal = nombreBase + contador;
                contador++;
            }
            
            contadorNombres = contador;
            return nombreFinal;
        }
    }
    
    // =========================================================================
    // Procesamiento de comandos
    // =========================================================================
    private static String procesarComando(String mensaje, String nombreCliente) {
        String upper = mensaje.toUpperCase();
        
        // Comandos locales
        if (upper.equals("MENU")) {
            return mostrarMenuTexto();
        }
        
        if (upper.equals("FECHA")) {
            return "Fecha y Hora: " + LocalDateTime.now().format(FORMATO_FECHA);
        }
        
        if (upper.equals("LISTA")) {
            return listarClientesConectados();
        }
        
        if (upper.startsWith("RESOLVER ")) {
            String argumento = mensaje.substring(9).trim();
            String expresion = extraerContenidoComillas(argumento);
            return resolverExpresion(expresion);
        }
        
        if (upper.startsWith("CONTAR ")) {
            String argumento = mensaje.substring(7).trim();
            String texto = extraerContenidoComillas(argumento);
            return contarTexto(texto);
        }
        
        if (upper.equals("PROVINCIAS")) {
            return listarProvincias();
        }
        
        // Comandos de mensajeria
        if (upper.startsWith("*ALL ")) {
            String contenido = mensaje.substring(5).trim();
            String textoMensaje = extraerContenidoComillas(contenido);
            return enviarMensajeATodos(nombreCliente, textoMensaje);
        }
        
        if (mensaje.startsWith("*")) {
            // Formato: *NOMBRE "mensaje"
            int primerEspacio = mensaje.indexOf(" ");
            if (primerEspacio == -1) {
                return "Error: Formato invalido. Uso: *NOMBRE \"mensaje\"";
            }
            
            String nombreDestino = mensaje.substring(1, primerEspacio);
            String contenido = mensaje.substring(primerEspacio + 1).trim();
            String textoMensaje = extraerContenidoComillas(contenido);
            
            return enviarMensajeACliente(nombreCliente, nombreDestino, textoMensaje);
        }
        
        return "Comando no reconocido. Escribe MENU para ver los comandos disponibles.";
    }
    
    // =========================================================================
    // Enviar mensaje a todos los clientes
    // =========================================================================
    private static String enviarMensajeATodos(String remitente, String mensaje) {
        if (mensaje.isEmpty()) {
            return "Error: Debes proporcionar un mensaje. Uso: *ALL \"tu mensaje\"";
        }
        
        int enviados = 0;
        String mensajeFormato = "[" + remitente + " -> TODOS] " + mensaje;
        
        for (ClienteConectado cliente : clientesConectados.values()) {
            if (!cliente.nombre.equals(remitente)) {
                cliente.salida.println(mensajeFormato);
                enviados++;
            }
        }
        
        imprimirYLoguear("[MENSAJE] " + remitente + " envio a TODOS: " + mensaje);
        return "Mensaje enviado a " + enviados + " cliente(s).";
    }
    
    // =========================================================================
    // Enviar mensaje a un cliente especifico
    // =========================================================================
    private static String enviarMensajeACliente(String remitente, String nombreDestino, String mensaje) {
        if (mensaje.isEmpty()) {
            return "Error: Debes proporcionar un mensaje. Uso: *NOMBRE \"tu mensaje\"";
        }
        
        if (!clientesConectados.containsKey(nombreDestino)) {
            imprimirYLoguear("[MENSAJE_ERROR] " + remitente + " intento enviar a " + nombreDestino + " (no existe)");
            return "Error: El cliente '" + nombreDestino + "' no existe o esta desconectado.";
        }
        
        if (remitente.equals(nombreDestino)) {
            return "Error: No puedes enviarte mensajes a ti mismo.";
        }
        
        ClienteConectado clienteDestino = clientesConectados.get(nombreDestino);
        String mensajeFormato = "[" + remitente + " -> " + nombreDestino + "] " + mensaje;
        clienteDestino.salida.println(mensajeFormato);
        
        imprimirYLoguear("[MENSAJE] " + remitente + " -> " + nombreDestino + ": " + mensaje);
        return "Mensaje enviado a '" + nombreDestino + "'.";
    }
    
    // =========================================================================
    // Listar clientes conectados
    // =========================================================================
    private static String listarClientesConectados() {
        if (clientesConectados.isEmpty()) {
            return "No hay clientes conectados en este momento (solo tu conexion).";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Clientes conectados (").append(clientesConectados.size()).append(" total):\n");
        
        int i = 1;
        for (ClienteConectado cliente : clientesConectados.values()) {
            sb.append(String.format("  %d. %s (IP: %s)%n", i++, cliente.nombre, cliente.ip));
        }
        
        return sb.toString().trim();
    }
    
    // =========================================================================
    // Mostrar menu de ayuda
    // =========================================================================
    private static void mostrarMenu(PrintWriter salida) {
        salida.println("\n--- Comandos disponibles ---");
        salida.println("  MENU                    - Muestra este menu");
        salida.println("  FECHA                   - Muestra fecha y hora");
        salida.println("  LISTA                   - Lista clientes conectados");
        salida.println("  RESOLVER \"expresion\"   - Ej: RESOLVER \"45*23/54+234\"");
        salida.println("  CONTAR \"texto\"         - Ej: CONTAR \"Hola mundo\"");
        salida.println("  PROVINCIAS              - Lista provincias de Argentina");
        salida.println("  *NOMBRE \"mensaje\"      - Envia mensaje a un cliente");
        salida.println("  *ALL \"mensaje\"         - Envia mensaje a todos");
        salida.println("  SALIR                   - Desconectarse");
        salida.println("-----------------------------\n");
    }
    
    private static String mostrarMenuTexto() {
        return "--- Comandos disponibles ---\n" +
               "  MENU                    - Muestra este menu\n" +
               "  FECHA                   - Muestra fecha y hora\n" +
               "  LISTA                   - Lista clientes conectados\n" +
               "  RESOLVER \"expresion\"   - Ej: RESOLVER \"45*23/54+234\"\n" +
               "  CONTAR \"texto\"         - Ej: CONTAR \"Hola mundo\"\n" +
               "  PROVINCIAS              - Lista provincias de Argentina\n" +
               "  *NOMBRE \"mensaje\"      - Envia mensaje a un cliente\n" +
               "  *ALL \"mensaje\"         - Envia mensaje a todos\n" +
               "  SALIR                   - Desconectarse\n" +
               "-----------------------------";
    }
    
    // =========================================================================
    // Extraer contenido entre comillas
    // =========================================================================
    private static String extraerContenidoComillas(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
    
    // =========================================================================
    // COMANDO: RESOLVER - Evaluador de expresiones matematicas
    // =========================================================================
    private static String resolverExpresion(String expr) {
        if (expr.isEmpty()) {
            return "Error: Debe proporcionar una expresion. Ej: RESOLVER \"45*23/54+234\"";
        }
        try {
            double resultado = evaluar(expr);
            if (resultado == Math.floor(resultado) && !Double.isInfinite(resultado)) {
                return "Resultado de [" + expr + "] = " + (long) resultado;
            }
            return "Resultado de [" + expr + "] = " + resultado;
        } catch (Exception e) {
            return "Error al resolver la expresion: " + e.getMessage();
        }
    }
    
    /**
     * Evaluador de expresiones matematicas sin librerias externas.
     * Soporta: +  -  *  /  parentesis  numeros decimales
     * Implementacion: analizador descendente recursivo
     */
    private static double evaluar(final String expr) {
        return new Object() {
            int pos = -1;
            int ch;

            void siguienteChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean consumir(int charBuscado) {
                while (ch == ' ') siguienteChar();
                if (ch == charBuscado) {
                    siguienteChar();
                    return true;
                }
                return false;
            }

            double parsear() {
                siguienteChar();
                double x = parseExpresion();
                if (pos < expr.length()) {
                    throw new RuntimeException("Caracter inesperado: '" + (char) ch + "'");
                }
                return x;
            }

            double parseExpresion() {
                double x = parseTerm();
                for (;;) {
                    if      (consumir('+')) x += parseTerm();
                    else if (consumir('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (consumir('*')) x *= parseFactor();
                    else if (consumir('/')) x /= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (consumir('+')) return parseFactor();
                if (consumir('-')) return -parseFactor();

                double x;
                int inicio = this.pos;

                if (consumir('(')) {
                    x = parseExpresion();
                    consumir(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') siguienteChar();
                    x = Double.parseDouble(expr.substring(inicio, this.pos));
                } else {
                    throw new RuntimeException(
                        "Caracter no valido: '" + (char) ch + "' en posicion " + pos);
                }
                return x;
            }
        }.parsear();
    }
    
    // =========================================================================
    // COMANDO: CONTAR - Palabras, vocales y consonantes
    // =========================================================================
    private static String contarTexto(String texto) {
        if (texto.isEmpty()) {
            return "Error: Debe proporcionar un texto. Ej: CONTAR \"Hola mundo\"";
        }

        String[] palabras = texto.trim().split("\\s+");
        int cantPalabras = palabras.length;

        final String VOCALES = "aeiouAEIOU";
        int vocales = 0;
        int consonantes = 0;

        for (char c : texto.toCharArray()) {
            if (Character.isLetter(c)) {
                if (VOCALES.indexOf(c) >= 0) {
                    vocales++;
                } else {
                    consonantes++;
                }
            }
        }

        return "Texto: \"" + texto + "\"\n"
             + "  -> Palabras:     " + cantPalabras + "\n"
             + "  -> Vocales:      " + vocales + "\n"
             + "  -> Consonantes:  " + consonantes;
    }
    
    // =========================================================================
    // COMANDO: PROVINCIAS - Lista las 24 provincias de Argentina
    // =========================================================================
    private static String listarProvincias() {
        StringBuilder sb = new StringBuilder();
        sb.append("Provincias de Argentina (").append(PROVINCIAS.length).append(" en total):\n");
        for (int i = 0; i < PROVINCIAS.length; i++) {
            sb.append(String.format("  %2d. %s%n", i + 1, PROVINCIAS[i]));
        }
        return sb.toString().trim();
    }
    
    // =========================================================================
    // Sistema de logging
    // =========================================================================
    private static void imprimirYLoguear(String mensaje) {
        String timestamp = LocalDateTime.now().format(FORMATO_FECHA);
        String linea = "[" + timestamp + "] " + mensaje;
        
        // Imprimir en consola
        System.out.println(linea);
        
        // Guardar en archivo
        try (FileWriter fw = new FileWriter(ARCHIVO_LOG, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(linea);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Error al escribir en el log: " + e.getMessage());
        }
    }
}
