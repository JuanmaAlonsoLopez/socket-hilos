import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Scanner;

public class Cliente {

    private static final String HOST   = "192.168.194.119";
    private static final int PUERTO = 6789;
    private static volatile boolean desconectado = false;

    public static void main(String[] args) {
        // Usar charset del sistema para compatibilidad con la consola
        Charset charset = Charset.defaultCharset();
        Scanner scannerInicial = new Scanner(System.in, charset);

        System.out.println("============================================");
        System.out.println("  CLIENTE SOCKET");
        System.out.println("============================================");

        // Pedir nombre de usuario antes de conectar
        String nombreUsuario;
        do {
            System.out.print("Ingrese su nombre de usuario: ");
            nombreUsuario = scannerInicial.nextLine().trim();
            if (nombreUsuario.isEmpty()) {
                System.out.println("[ERROR] El nombre de usuario no puede estar vacio.");
            }
        } while (nombreUsuario.isEmpty());

        System.out.println("\nBienvenido, " + nombreUsuario + "!");
        System.out.println("Conectando a " + HOST + ":" + PUERTO + "...\n");

        try (
            Socket socket = new Socket(HOST, PUERTO);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), charset));
            PrintWriter salida = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), charset), true);
            Scanner scanner = new Scanner(System.in, charset)
        ) {
            System.out.println("Conexion establecida con el servidor!");

            // Enviar nombre de usuario al servidor como identificacion
            salida.println("NOMBRE " + nombreUsuario);
            System.out.println("Registrado como: " + nombreUsuario + "\n");

            // Variable final para usar en el hilo
            final String usuario = nombreUsuario;

            // ----------------------------------------------------------------
            // Hilo lector: imprime en pantalla todo lo que llega del servidor
            // ----------------------------------------------------------------
            Thread hiloLector = new Thread(() -> {
                try {
                    String linea;
                    while ((linea = entrada.readLine()) != null) {
                        // Diferenciar tipos de mensajes
                        if (linea.contains("->")) {
                            // Es un mensaje de otro cliente
                            System.out.println("\n[MENSAJE] " + linea);
                        } else if (linea.contains("[ERROR]")) {
                            System.out.println("[ERROR] " + linea);
                        } else if (linea.startsWith("---")) {
                            System.out.println(linea);
                        } else {
                            System.out.println("[SERVIDOR] " + linea);
                        }

                        // Si no fue un mensaje de otro cliente, mostrar el prompt nuevamente
                        if (!linea.contains("->")) {
                            System.out.print("[" + usuario + "] > ");
                            System.out.flush();
                        }
                    }
                } catch (IOException e) {
                    // El servidor cerro la conexion
                    if (!desconectado) {
                        System.out.println("\n[INFO] Conexion con el servidor finalizada.");
                    }
                }
            });
            hiloLector.setDaemon(true);
            hiloLector.start();

            // ----------------------------------------------------------------
            // Bucle principal: lee input del usuario y lo envia al servidor
            // ----------------------------------------------------------------
            String input;
            do {
                System.out.print("[" + usuario + "] > ");
                input = scanner.nextLine();

                if (input.trim().isEmpty()) {
                    continue;
                }

                // Validaciones basicas del cliente
                if (input.trim().startsWith("*")) {
                    if (!input.contains(" ")) {
                        System.out.println("[ERROR] Formato invalido. Uso: *NOMBRE \"mensaje\" o *ALL \"mensaje\"");
                        continue;
                    }
                    if (!input.contains("\"")) {
                        System.out.println("[ERROR] El mensaje debe ir entre comillas. Uso: *NOMBRE \"mensaje\"");
                        continue;
                    }
                }

                // Enviar al servidor
                salida.println(input.trim());

                // Pausa corta para que la respuesta del servidor llegue
                // antes de que aparezca el proximo prompt
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            } while (!input.trim().equalsIgnoreCase("SALIR"));

            desconectado = true;
            System.out.println("\n[INFO] Sesion finalizada. Hasta luego!");

        } catch (ConnectException e) {
            System.out.println("[ERROR] No se pudo conectar al servidor.");
            System.out.println("        Verifique que el Servidor este en ejecucion en " + HOST + ":" + PUERTO + ".");
            System.out.println("\n        Nota: Si deseas conectarte desde otra maquina via ZeroTier:");
            System.out.println("        - Reemplaza 'localhost' con la IP virtual de ZeroTier del servidor");
            System.out.println("        - Asegurate de que ambas maquinas esten en la misma red ZeroTier");
        } catch (IOException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }
}
