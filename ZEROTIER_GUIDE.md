# 🌐 Guía Completa: Configurar Conexiones Remotas con ZeroTier

## 1. ¿Qué es ZeroTier?

ZeroTier es una red virtual que conecta máquinas de forma segura a través de Internet, **como si estuvieran en la misma red local**. Es gratuito, fácil de usar y no requiere configuración complicada de puertos.

### Ventajas:
- ✅ Sin configuración de router (No necesitas portforwarding)
- ✅ Encriptación automática (AES-256)
- ✅ Funciona detrás de firewalls y NAT
- ✅ IP asignada automáticamente a cada dispositivo
- ✅ Gratuito para hasta 100 dispositivos

## 2. Instalación de ZeroTier

### En Windows

1. **Descarga el instalador:**
   - Ve a: https://www.zerotier.com/download/
   - Descarga la versión para Windows

2. **Ejecuta el instalador:**
   - Haz doble clic en `ZeroTier One installer`
   - Sigue los pasos del instalador
   - ZeroTier se ejecutará como servicio de fondo

3. **Verifica la instalación:**
   - Busca el icono de ZeroTier en la bandeja del sistema (esquina inferior derecha)
   - Haz clic para abrir el menú

### En Linux (Ubuntu/Debian)

```bash
# Agregar repositorio
curl https://install.zerotier.com | sudo bash

# Instalar
sudo apt-get install zerotier-one

# Iniciar el servicio
sudo systemctl start zerotier-one
sudo systemctl enable zerotier-one

# Verificar estado
sudo systemctl status zerotier-one
```

### En macOS

```bash
# Instala usando Homebrew
brew install zerotier-one

# O descarga directamente:
# https://www.zerotier.com/download/
```

## 3. Crear una Red ZeroTier

### Paso 1: Crear cuenta

1. Ve a https://my.zerotier.com/
2. Haz clic en "Sign Up"
3. Completa el registro con email y contraseña
4. Verifica tu email

### Paso 2: Crear la red

1. Inicia sesión en https://my.zerotier.com/
2. Haz clic en "Create a Network" (botón azul)
3. Se creará una red con un ID de 16 caracteres hexadecimales
4. **Copia este ID** - lo necesitarás en todas las máquinas

**Ejemplo de Network ID:**
```
a0996acf12345678
```

## 4. Conectarse a la Red

### En Windows

1. Haz clic derecho en el icono de ZeroTier en la bandeja del sistema
2. Selecciona "Join New Network"
3. Pega el Network ID que copiaste
4. Presiona Enter
5. Verás la opción para autorizar (check el recuadro)
6. Nota tu IP virtual asignada (ej: 192.168.196.42)

### En Linux

```bash
# Unirse a la red
sudo zerotier-cli join a0996acf12345678

# Verificar que estés conectado
sudo zerotier-cli status

# Ver la IP asignada
sudo zerotier-cli listnetworks

# Salir de la red (si necesitas)
sudo zerotier-cli leave a0996acf12345678
```

### En macOS

```bash
# Unirse a la red
sudo zerotier-cli join a0996acf12345678

# Ver estatus
sudo zerotier-cli status

# Ver IP asignada
sudo zerotier-cli listnetworks
```

## 5. Encontrar tu IP Virtual de ZeroTier

Después de conectarte a la red, necesitas encontrar tu IP virtual asignada.

### En Windows

**Opción 1 - Interfaz gráfica:**
1. Haz clic derecho en el icono de ZeroTier
2. Busca "Managed Addresses" o similar
3. Verás una IP tipo `192.168.xxx.xxx`

**Opción 2 - Línea de comandos:**
```cmd
zerotier-cli listnetworks
```

Salida esperada:
```
a0996acf12345678  My Network    OK PRIVATE    192.168.196.42/24
```

### En Linux

```bash
sudo zerotier-cli listnetworks
```

### En macOS

```bash
sudo zerotier-cli listnetworks
```

**Anota estas IPs para cada máquina:**
- Servidor: `192.168.196.42`
- Cliente 1: `192.168.196.50`
- Cliente 2: `192.168.196.51`

## 6. Autorizar Clientes en el Control Panel

Cuando alguien se conecta a tu red ZeroTier, **debes autorizarlo** en el panel de control.

1. Ve a https://my.zerotier.com/
2. Haz clic en el nombre de tu red
3. Baja hasta la sección "Members"
4. Verás nuevos dispositivos en la lista
5. Haz clic en el cuadrado azul para "Authorize" (autorizar)
6. Una vez autorizado, aparecerá una IP asignada

## 7. Configurar el Servidor Socket para Conexiones Remotas

### Paso 1: Modificar Servidor.java

Abre el archivo `Servidor.java` y busca esta línea (alrededor de la línea 63):

```java
ServerSocket serverSocket = new ServerSocket(PUERTO);
```

Reemplázala por:

```java
ServerSocket serverSocket = new ServerSocket(PUERTO, 50, InetAddress.getByName("0.0.0.0"));
```

Esto hace que el servidor escuche en **todas las interfaces de red**, incluyendo ZeroTier.

### Paso 2: Recompilar

```bash
javac Servidor.java
```

### Paso 3: Ejecutar el servidor

```bash
java Servidor
```

El servidor ahora escuchará en:
- `127.0.0.1:6789` (local)
- `192.168.196.42:6789` (ZeroTier, si ese es tu IP)
- Cualquier otra interfaz de red

## 8. Configurar Clientes Remotos

### En la máquina cliente (remota)

1. **Asegúrate de estar en la misma red ZeroTier**
   - Únete a la red con el mismo Network ID

2. **Obtén la IP del servidor**
   - Ejecuta el comando correspondiente a tu SO (ver paso 5)
   - Anota la IP de ZeroTier del servidor (ej: `192.168.196.42`)

3. **Modifica Cliente.java**

Abre `Cliente.java` y busca esta línea:

```java
private static final String HOST = "localhost";
```

Reemplázala con la IP ZeroTier del servidor:

```java
private static final String HOST = "192.168.196.42";
```

4. **Recompila y ejecuta**

```bash
javac Cliente.java
java Cliente
```

## 9. Prueba de Conectividad

Antes de ejecutar la aplicación, prueba que puedas alcanzar el servidor:

### En Windows

```cmd
ping 192.168.196.42
```

### En Linux/macOS

```bash
ping -c 4 192.168.196.42
```

Espera una respuesta como:

```
PING 192.168.196.42 (192.168.196.42) 56(84) bytes of data.
64 bytes from 192.168.196.42: icmp_seq=1 ttl=64 time=25.3 ms
```

Si no hay respuesta:
1. Verifica que ambas máquinas estén en la misma red ZeroTier
2. Comprueba que el cliente esté autorizado en el panel de control
3. Reinicia ZeroTier en ambas máquinas

## 10. Ejemplo Completo de Configuración

### Máquina A (Servidor)
```
Network ID: a0996acf12345678
IP ZeroTier: 192.168.196.42
Ejecutar: java Servidor
```

### Máquina B (Cliente 1)
```
Network ID: a0996acf12345678
IP ZeroTier: 192.168.196.50
En Cliente.java: HOST = "192.168.196.42"
Ejecutar: java Cliente
```

### Máquina C (Cliente 2)
```
Network ID: a0996acf12345678
IP ZeroTier: 192.168.196.51
En Cliente.java: HOST = "192.168.196.42"
Ejecutar: java Cliente
```

## 11. Solución de Problemas

### ❌ "No se pudo conectar al servidor"

**Causas posibles:**
1. El servidor no está corriendo
2. Diferente Network ID
3. Cliente no autorizado en el panel
4. Firewall bloqueando ZeroTier

**Soluciones:**
```bash
# 1. Verificar que el servidor esté corriendo
ps aux | grep Servidor

# 2. Verificar conexión a la red
sudo zerotier-cli status

# 3. Ver miembros autorizados
sudo zerotier-cli listnetworks

# 4. Reiniciar ZeroTier
sudo systemctl restart zerotier-one  # Linux
```

### ❌ "Autorización pendiente"

El servidor ha recibido tu conexión pero no está autorizado:
1. Ve a https://my.zerotier.com/
2. Abre tu red
3. Busca tu dispositivo en "Members"
4. Haz clic en el cuadrado para autorizarlo

### ❌ ZeroTier no aparece en la bandeja del sistema (Windows)

```bash
# Reinicia el servicio ZeroTier
net stop ZeroTierOneService
net start ZeroTierOneService
```

### ❌ Permiso denegado en Linux

```bash
# Agrégale permisos al usuario
sudo usermod -a -G zerotier $USER
# Cierra sesión y vuelve a iniciar
```

## 12. Comandos Útiles

### Ver estado de ZeroTier

```bash
# Linux/macOS
sudo zerotier-cli status

# Ver redes unidas
sudo zerotier-cli listnetworks

# Ver identidad (debe cambiar a 16 caracteres)
sudo zerotier-cli info
```

### Desconectarse de una red

```bash
sudo zerotier-cli leave a0996acf12345678
```

### Ver logs de ZeroTier

```bash
# Linux
sudo journalctl -u zerotier-one -f

# macOS
log stream --predicate 'process == "zerotier-one"'
```

## 13. Considera esta Arquitectura

```
        INTERNET
            |
            |
    ┌───────┴────────┐
    |                |
  ZT Network (a0996acf12345678)
    |                |
    |                |
┌───▼───┐      ┌────▼───┐      ┌────────┐
│ IP:42 │      │ IP:50  │      │ IP:51  │
│Server │◄─────┤Client1 │      │Client2 │
│       │      │        │      │        │
└───────┘      └────────┘      └────────┘
```

---

**Listo para conectar máquinas remotas seguras y sin complicaciones!** 🚀
