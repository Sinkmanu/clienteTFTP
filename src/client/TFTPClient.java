package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Scanner;

/**
 * @author Manuel Mancera
 * 
 */
public class TFTPClient {

    private static final String prompt = "Client>";
    private static final int s_port = 1888;
    private InetAddress s_addr;
    private DatagramSocket sock;

    /*
     * TFTP Packets! 2 bytes string 1 byte string 1 byte ------------------------------------------------ | Opcode |
     * Filename | 0 | Mode | 0 | ------------------------------------------------
     * 
     * OPCODE COMMAND 1 RRQ 2 WRQ 3 DATA 4 ACK 5 ERROR
     */

    TFTPClient(String s_addr) {
	try {
	    this.s_addr = InetAddress.getByName(s_addr);
	} catch (UnknownHostException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }

    public void setServerAddr(String s_addr) {
	try {
	    this.s_addr = InetAddress.getByName(s_addr);
	} catch (UnknownHostException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }

    /**
     * Genera el payload necesario para una petición RRQ o WRQ
     * 
     * @param opcode
     * @param file
     * @return
     */
    public byte[] genPayload(String opcode, String file) {
	if (opcode.equals("RRQ")) {
	    byte[] intro = { 0x00, 0x01 };
	    byte[] padd = { 0x00 };
	    byte[] b_file = file.getBytes();
	    byte[] b_mode = "netascii".getBytes();
	    byte[] payload = new byte[4 + b_mode.length + b_file.length];
	    System.arraycopy(intro, 0, payload, 0, intro.length);
	    System.arraycopy(b_file, 0, payload, intro.length, b_file.length);
	    System.arraycopy(padd, 0, payload, intro.length + b_file.length, padd.length);
	    System.arraycopy(b_mode, 0, payload, intro.length + b_file.length + padd.length,
		    b_mode.length);
	    System.arraycopy(padd, 0, payload, intro.length + b_file.length + padd.length
		    + b_mode.length, padd.length);
	    return payload;
	} else if (opcode.equals("WRQ")) {
	    byte[] intro = { 0x00, 0x02 };
	    byte[] padd = { 0x00 };
	    byte[] b_file = file.getBytes();
	    byte[] b_mode = "netascii".getBytes();
	    byte[] payload = new byte[4 + b_mode.length + b_file.length];
	    System.arraycopy(intro, 0, payload, 0, intro.length);
	    System.arraycopy(b_file, 0, payload, intro.length, b_file.length);
	    System.arraycopy(padd, 0, payload, intro.length + b_file.length, padd.length);
	    System.arraycopy(b_mode, 0, payload, intro.length + b_file.length + padd.length,
		    b_mode.length);
	    System.arraycopy(padd, 0, payload, intro.length + b_file.length + padd.length
		    + b_mode.length, padd.length);
	    return payload;
	} else {
	    return null;
	}
    }

    /**
     * Transforma un octeto en un entero, necesario para saber por que bloque va.
     * 
     * @param b
     * @return
     */
    public int octect2int(byte[] b) {
	int MASK = 0xFF;
	int result = 0;
	result = (b[0] & MASK) >> 0;
	result = result + (b[1] & MASK);
	return result;
    }

    /**
     * Genera el payload necesario para un bloque
     * 
     * @param block
     * @return
     */
    public byte[] genACKpayload(int block) {
	byte[] payload = new byte[4];
	// Esto es un poco hardcore...
	payload[0] = 0x00;
	payload[1] = 0x04;
	payload[2] = (byte) (block >> 8);
	payload[3] = (byte) (block >> 0);
	return payload;
    }

    /**
     * Genera el payload para enviar datos. (512 bytes de datos siempre, menos en el ultimo bloque)
     * 
     * @param block
     * @param data
     * @return
     */
    public byte[] genDATApayload(int block, byte[] data) {
	byte[] payload = new byte[4 + data.length];
	payload[0] = 0x00;
	payload[1] = 0x03;
	payload[2] = (byte) (block >> 8);
	payload[3] = (byte) (block >> 0);
	for (int i = 4; i < 4 + data.length; i++) {
	    payload[i] = data[i - 4];
	}
	return payload;
    }

    /**
     * Método para enviar un comando al servidor, además de llevar toda la logica relacionada con el comando.
     * 
     * @param command
     */
    public void sendRequest(String command) {
	try {
	    sock = new DatagramSocket();
	    sock.setSoTimeout(1000);
	    if (command.split(" ")[0].toUpperCase().equals("RRQ")) {
		// RRQ + file + mode netascii + 0
		byte[] payload = genPayload(command.split(" ")[0].toUpperCase(),
			command.split(" ")[1]);
		DatagramPacket packet = new DatagramPacket(payload, payload.length, s_addr, s_port);
		sock.send(packet);
		// Ahora debería de recibir el archvo en caso de que exista
		// Se conecta al puerto con el que me he conectado al servidor
		byte[] dataTotal = new byte[516];
		byte[] data;
		DatagramPacket r_packet = new DatagramPacket(dataTotal, dataTotal.length);
		sock.receive(r_packet); // r_packet = OPCODE,BLOCK,DATA
		int contador = 1;
		int block;
		FileOutputStream fileout;
		fileout = new FileOutputStream(command.split(" ")[2]);

		while (r_packet.getLength() == 516) { // OPCODE(2) + BLOCK(2) + DATA (512)
		    data = Arrays.copyOfRange(r_packet.getData(), 4, r_packet.getLength());
		    // System.out.print(new String(data,0,data.length));
		    fileout.write(data);
		    block = octect2int(new byte[] { r_packet.getData()[2], r_packet.getData()[3] });
		    // Tiene que enviar el ACK = OPCODE,BLOCK
		    payload = genACKpayload(contador);
		    packet = new DatagramPacket(payload, payload.length, s_addr, r_packet.getPort());
		    sock.send(packet);
		    if (contador == block)
			contador++; // Si recibio el block que le toca.

		    sock.receive(r_packet);
		}
		// �ltimo paquete
		byte[] dataFinal = Arrays.copyOfRange(r_packet.getData(), 4, r_packet.getLength());
		// System.out.print(new String(dataFinal,0,dataFinal.length));
		fileout.write(dataFinal);
		block = octect2int(new byte[] { r_packet.getData()[2], r_packet.getData()[3] });
		payload = genACKpayload(contador);
		packet = new DatagramPacket(payload, payload.length, s_addr, r_packet.getPort());
		sock.send(packet);
		// contador++;
		fileout.close();
		sock.close();
	    } else if (command.split(" ")[0].toUpperCase().equals("WRQ")) {
		// WRQ <remote file> <local file>
		// WRQ + file + mode netascii + 0
		byte[] payload = genPayload(command.split(" ")[0].toUpperCase(),
			command.split(" ")[1]);
		DatagramPacket packet = new DatagramPacket(payload, payload.length, s_addr, s_port);
		sock.send(packet);
		// Espera un ACK block = 0
		byte[] ACKpayload = new byte[4]; // OPCODE + BLOCK
		byte[] data2send; // DATA
		DatagramPacket r_packet = new DatagramPacket(ACKpayload, ACKpayload.length);
		sock.receive(r_packet); // r_packet = OPCODE,BLOCK,DATA
		int block;
		int contador = 0;
		// Debería comprobar si tiene permisos de escritura, etc.
		File file = new File(command.split(" ")[2]);
		FileInputStream f_upload = new FileInputStream(file);
		int pos_file = 0;
		boolean fin = false;
		while ((pos_file < file.length() && !fin)) { // OPCODE(2) + BLOCK(2)
		    ACKpayload = Arrays.copyOfRange(r_packet.getData(), 0, r_packet.getLength());
		    block = octect2int(new byte[] { r_packet.getData()[2], r_packet.getData()[3] });
		    if (block == contador) {
			contador++;
			if (file.length() - pos_file > 512) {
			    payload = new byte[516];
			    data2send = new byte[512];
			    f_upload.read(data2send, 0, 512);
			    pos_file = pos_file + 512;
			} else {
			    payload = new byte[4 + (int) file.length() - pos_file];
			    data2send = new byte[(int) file.length() - pos_file];
			    f_upload.read(data2send);
			    pos_file = (int) file.length();
			}
			// lee el archivo y envia (máximo 512 bytes)
			payload = genDATApayload(contador, data2send);
			// Creamos el paquete a enviar.
			packet = new DatagramPacket(payload, payload.length, s_addr,
				r_packet.getPort());
			sock.send(packet);

			sock.receive(r_packet);
		    }
		}
		System.out.println("File uploaded.");
		f_upload.close();
	    } else if (command.split(" ")[0].toUpperCase().equals("SERVER")) {
		this.setServerAddr(command.split(" ")[1]);
		System.out.println("Server: " + command.split(" ")[1]);
	    } else if (command.split(" ")[0].toUpperCase().equals("READ")) {
		// RRQ + file + mode netascii + 0
		byte[] payload = genPayload("RRQ", command.split(" ")[1]);
		DatagramPacket packet = new DatagramPacket(payload, payload.length, s_addr, s_port);
		sock.send(packet);
		// Ahora debería de recibir el archvo en caso de que exista
		// Se conecta al puerto con el que me he conectado al servidor
		byte[] dataTotal = new byte[516];
		byte[] data;
		DatagramPacket r_packet = new DatagramPacket(dataTotal, dataTotal.length);
		sock.receive(r_packet); // r_packet = OPCODE,BLOCK,DATA
		int contador = 1;
		int block;

		while (r_packet.getLength() == 516) { // OPCODE(2) + BLOCK(2) + DATA (512)
		    data = Arrays.copyOfRange(r_packet.getData(), 4, r_packet.getLength());
		    System.out.print(new String(data, 0, data.length));
		    block = octect2int(new byte[] { r_packet.getData()[2], r_packet.getData()[3] });
		    // Tiene que enviar el ACK = OPCODE,BLOCK
		    payload = genACKpayload(contador);
		    packet = new DatagramPacket(payload, payload.length, s_addr, r_packet.getPort());
		    sock.send(packet);
		    if (contador == block)
			contador++; // Si recibio el block que le toca.

		    sock.receive(r_packet);
		}
		// Último paquete
		byte[] dataFinal = Arrays.copyOfRange(r_packet.getData(), 4, r_packet.getLength());
		System.out.print(new String(dataFinal, 0, dataFinal.length));
		block = octect2int(new byte[] { r_packet.getData()[2], r_packet.getData()[3] });
		payload = genACKpayload(contador);
		packet = new DatagramPacket(payload, payload.length, s_addr, r_packet.getPort());
		sock.send(packet);
		// contador++;

		sock.close();
	    } else {
		System.out.println("Command not found.");
	    }
	} catch (SocketTimeoutException e) {
	    System.out.println("Timeout.");
	} catch (SocketException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} finally {
	    sock.close();
	}
    }

    /**
     * Menú para interactuar con el usuario desde el terminal.:)
     * 
     * @throws IOException
     */
    public void menu() throws IOException {
	System.out.println(""
		+ "*****************************************************************\n"
		+ "*\t\t\t TFTP Client  \t\t\t\t*\n"
		+ "*\t Comandos soportados: RRQ,WRQ,QUIT  \t\t\t*\n"
		+ "*\tEscriba '?' o 'help' para más informacion\t\t*\n"
		+ "*\t Selecciona servidor con server <address>\t\t*\n"
		+ "*****************************************************************");
	Scanner input = new Scanner(System.in);
	boolean salir = false;
	String command;
	while (!salir) {
	    System.out.print(prompt + " ");
	    command = input.nextLine();
	    if (command.split(" ")[0].toUpperCase().equals("help")
		    || command.split(" ")[0].toUpperCase().equals("?")) {
		System.out.println(help_menu());
	    } else if (command.split(" ")[0].toUpperCase().equals("QUIT")) {
		salir = true;
	    } else {
		sendRequest(command);
	    }
	}
	// Cierra sockets y termina.
	input.close();
	System.out.println("Cliente cerrado.");
    }

    /**
     * Display help menu
     */
    public String help_menu() {
	return (""
		+ "Comandos:\n"
		+ "SERVER <address>\t\t :Conecta con el servidor solicitado.\n"
		+ "RRQ <Remote File> <Local File>\t :Descarga un archivo desde el servidor.\n"
		+ "READ <Remote File> \t\t :Lee un archivo del servidor y lo muestra por pantalla.\n"
		+ "WRQ <Remote file> <Local file>\t :Sube un archivo al servidor.\n"
		+ "QUIT\t\t\t\t :Cierra el cliente");
    }

    public static void main(String[] args) {
	// TODO Auto-generated method stub
	TFTPClient cliente = new TFTPClient("");
	try {
	    cliente.menu();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (FTPException e) {
	    System.out.println(e);
	} finally {
	}
    }

}
