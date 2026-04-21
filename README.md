# Servidor Socket - Segunda Parte: Comunicación entre Clientes

## 📋 Descripción General

Este proyecto implementa un **servidor socket multicliente** en Java que permite:
- ✅ Atender múltiples clientes simultáneamente usando hilos
- ✅ Comunicación directa entre clientes
- ✅ Asignación automática de nombres únicos a cada cliente
- ✅ Sistema completo de logging de todas las operaciones
- ✅ Soporte para conexiones remotas vía ZeroTier
- ✅ Resolución de expresiones matemáticas
- ✅ Análisis de texto (palabras, vocales, consonantes)
- ✅ Listado de provincias de Argentina

## 🏗️ Arquitectura

### Servidor (Servidor.java)
**Características principales:**
- **Gestor de clientes concurrente**: Usa `ConcurrentHashMap` para almacenar clientes conectados
- **Asignación automática de nombres**: Genera nombres únicos (Usuario, Usuario1, Usuario2, etc.)
- **Comunicación inter-clientes**: Envía mensajes entre clientes o a todos simultáneamente
- **Sistema de logging**: Registra todas las operaciones en consola y archivo (`servidor.log`)
- **Manejo robusto de errores**: Valida mensajes, clientes inexistentes, formatos incorrectos

### Cliente (Cliente.java)
**Características principales:**
- **Interfaz interactiva**: Prompt claro para el usuario
- **Hilo lector asincrónico**: Recibe mensajes de otros clientes sin bloquear entrada
- **Validación de entrada**: Verifica formato de comandos antes de enviar
- **Soporte para conexiones remotas**: Facilita conexión vía ZeroTier

## 📡 Comandos Disponibles

### Comandos Locales (procesados por el servidor)

| Comando | Descripción | Ejemplo |
|---------|-------------|---------|
| `MENU` | Muestra la lista de comandos | `MENU` |
| `FECHA` | Muestra la fecha y hora actual | `FECHA` |
| `LISTA` | Lista los clientes conectados | `LISTA` |
| `RESOLVER "expresión"` | Resuelve expresiones matemáticas | `RESOLVER "45*23/54+234"` |
| `CONTAR "texto"` | Cuenta palabras, vocales y consonantes | `CONTAR "Hola mundo"` |
| `PROVINCIAS` | Muestra las 24 provincias de Argentina | `PROVINCIAS` |
| `SALIR` | Desconecta del servidor | `SALIR` |

### Comandos de Mensajería

| Comando | Descripción | Ejemplo |
|---------|-------------|---------|
| `*NOMBRE "mensaje"` | Envía un mensaje a un cliente específico | `*Usuario1 "Hola, ¿cómo estás?"` |
| `*ALL "mensaje"` | Envía un mensaje a todos los clientes | `*ALL "¿Alguien conectado?"` |

## 🚀 Cómo Usar

### Compilación

```bash
# En el directorio del proyecto
javac Servidor.java
javac Cliente.java
```

### Ejecución Local

**Terminal 1 - Iniciar el servidor:**
```bash
java Servidor
```

**Terminal 2+ - Conectar clientes:**
```bash
java Cliente
```

### Conexión Remota vía ZeroTier

#### Paso 1: Instalar y configurar ZeroTier

1. Descarga ZeroTier desde: https://www.zerotier.com/download/
2. Instala en todas las máquinas que necesites conectar
3. En cada máquina, únete a la misma red ZeroTier (debes tener el ID de red)

#### Paso 2: Obtener las direcciones IP virtuales

Después de unirte a la red ZeroTier, cada máquina recibe una IP virtual. Obtén estas IPs:

```bash
# En Windows
ipconfig /all | findstr "ZeroTier"

# En Linux/Mac
ip addr | grep zt

# O en ZeroTier UI
Ver en Settings -> Interface
```

#### Paso 3: Configurar el servidor para escuchar en todas las interfaces

**Abre `Servidor.java` y modifica esta línea:**

Cambiar esto:
```java
ServerSocket serverSocket = new ServerSocket(PUERTO);
```

Por esto (para escuchar en todas las interfaces):
```java
ServerSocket serverSocket = new ServerSocket(PUERTO, 50, InetAddress.getByName("0.0.0.0"));
```

#### Paso 4: Conectar clientes remotos

**En la máquina remota, abre `Cliente.java` y modifica:**

Cambiar esto:
```java
private static final String HOST = "localhost";
```

Por la IP virtual de ZeroTier del servidor:
```java
private static final String HOST = "192.168.x.xxx"; // IP virtual de ZeroTier del servidor
```

Luego compila y ejecuta:
```bash
javac Cliente.java
java Cliente
```

## 📝 Ejemplo de Ejecución

### Servidor

```
============================================
  SERVIDOR INICIADO EN EL PUERTO 6789
============================================
Esperando conexiones de clientes...
Nota: Puedes conectar clientes desde otras máquinas via ZeroTier
      usando la dirección IP virtual asignada por ZeroTier
Hora inicio: 16/04/2026 14:30:45

[CONEXION] Nuevo cliente desde IP: 127.0.0.1
[CLIENTE] Usuario (127.0.0.1) - CONECTADO
[LOG Usuario] >>> FECHA
[LOG Usuario] >>> *ALL "Hola a todos"
[MENSAJE] Usuario envió a TODOS: Hola a todos
[CONEXION] Nuevo cliente desde IP: 127.0.0.1
[CLIENTE] Usuario1 (127.0.0.1) - CONECTADO
[LOG Usuario1] >>> *Usuario "Hola Usuario"
[MENSAJE] Usuario1 -> Usuario: Hola Usuario
```

### Cliente 1

```
============================================
  CLIENTE SOCKET
  Conectando a localhost:6789...
============================================
¡Conexión establecida con el servidor!

[SERVIDOR] ============================================
[SERVIDOR]   ¡Bienvenido al Servidor Socket!
[SERVIDOR]   Tu usuario: Usuario
[SERVIDOR] ============================================

[TU] > FECHA
[SERVIDOR] Fecha y Hora: 16/04/2026 14:30:50

[TU] > *ALL "Hola a todos"
[SERVIDOR] Mensaje enviado a 1 cliente(s).

[TU] > *Usuario1 "¿Cómo estás?"
[SERVIDOR] Mensaje enviado a 'Usuario1'.
```

### Cliente 2

```
============================================
  CLIENTE SOCKET
  Conectando a localhost:6789...
============================================
¡Conexión establecida con el servidor!

[SERVIDOR] ============================================
[SERVIDOR]   ¡Bienvenido al Servidor Socket!
[SERVIDOR]   Tu usuario: Usuario1
[SERVIDOR] ============================================

📨 [Usuario -> TODOS] Hola a todos

[TU] > LISTA
[SERVIDOR] Clientes conectados (2 total):
[SERVIDOR]   1. Usuario (IP: 127.0.0.1)
[SERVIDOR]   2. Usuario1 (IP: 127.0.0.1)

📨 [Usuario -> Usuario1] ¿Cómo estás?

[TU] > *Usuario "¡Estoy bien, gracias!"
[SERVIDOR] Mensaje enviado a 'Usuario'.
```

## 🔧 Detección y Manejo de Errores

El servidor valida y maneja los siguientes errores:

| Error | Acción |
|-------|--------|
| Cliente inexistente | Notifica al remitente que el cliente no existe |
| Formato de mensaje inválido | Retorna error de sintaxis |
| Expresión matemática inválida | Retorna error de cálculo |
| Cliente enviando mensaje a sí mismo | Rechaza la acción |
| Servidor desconectado | Cliente se cierra elegantemente |

## 📊 Sistema de Logging

El servidor mantiene un archivo `servidor.log` con todas las operaciones:

```
[16/04/2026 14:30:45] ============================================
[16/04/2026 14:30:45]   SERVIDOR INICIADO EN EL PUERTO 6789
[16/04/2026 14:30:46] [CONEXION] Nuevo cliente desde IP: 127.0.0.1
[16/04/2026 14:30:46] [CLIENTE] Usuario (127.0.0.1) - CONECTADO
[16/04/2026 14:30:51] [LOG Usuario] >>> FECHA
[16/04/2026 14:30:56] [LOG Usuario] >>> *ALL "Hola a todos"
[16/04/2026 14:30:56] [MENSAJE] Usuario envió a TODOS: Hola a todos
[16/04/2026 14:31:02] [CLIENTE] Usuario (127.0.0.1) - DESCONECTADO
```

## 🌐 Características de ZeroTier

**Ventajas de usar ZeroTier:**
- ✅ No requiere configuración de puertos en router
- ✅ Encriptación end-to-end
- ✅ Funciona detrás de firewalls y NAT
- ✅ Máquinas remotas se ven como si estuvieran en la misma LAN
- ✅ Gratuito para uso personal

**Cómo crear una red ZeroTier:**
1. Ve a https://my.zerotier.com/
2. Crea una cuenta
3. Haz clic en "Create a Network"
4. Copia el Network ID
5. En cada máquina, abre ZeroTier y únete a esa red

## 🔐 Seguridad

**Consideraciones de seguridad:**
- Los mensajes entre clientes viajan a través del servidor
- ZeroTier proporciona encriptación en tránsito
- El archivo `servidor.log` contiene información de todas las operaciones
- No hay autenticación (es un proyecto educativo)

## 📚 Tecnologías Utilizadas

- **Java 8+**: Lenguaje de programación
- **Sockets TCP**: Comunicación cliente-servidor
- **Hilos (Threads)**: Manejo concurrente de clientes
- **ConcurrentHashMap**: Almacenamiento thread-safe de clientes
- **BufferedReader/PrintWriter**: E/S en streams
- **ZeroTier**: Conectividad remota (opcional)

## 👤 Autor

**Juan Manuel Alonso López**

## 📄 Licencia

Proyecto educativo - Libre de usar y modificar

## 🤝 Notas Importantes

1. **Puerto 6789**: Asegúrate de que este puerto esté disponible
2. **Firewall**: Si usas ZeroTier, verifica que no bloquee la aplicación
3. **Encoding UTF-8**: El proyecto usa UTF-8 para soportar caracteres españoles
4. **Sincronización**: El gestor de clientes está sincronizado para ser thread-safe

## 📞 Soporte

Para más información sobre ZeroTier: https://www.zerotier.com/
Para preguntas sobre Java Sockets: https://docs.oracle.com/javase/tutorial/networking/sockets/
