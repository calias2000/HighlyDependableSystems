package pt.tecnico.bank;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Event;
import sun.misc.Signal;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.*;


public class ServerMain implements Serializable{

	static HashMap<PublicKey,Client> clientList = new HashMap<>();
	static KeyPair keyPair = null;

	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
		System.out.println(ServerMain.class.getSimpleName());

		File keystore = new File("server.jks");

		if (keystore.exists()){
			keyPair = getKeyPair();
			System.out.println("KeyPair obtained from Server KeyStore.");
		} else {
			System.out.println("Creating server KeyStore...");
			generateStore();
			Thread.sleep(1500);
			keyPair = getKeyPair();
		}

		try{
			FileInputStream fileInput = new FileInputStream("database/db.txt");

			ObjectInputStream objectInput = new ObjectInputStream(fileInput);

			clientList = (HashMap<PublicKey, Client>)objectInput.readObject();

			objectInput.close();
			fileInput.close();


		} catch (EOFException e) {
			System.out.println("EMPTY DATABASE!!");
		}

		try {
			final BindableService impl = new ServerServiceImpl();

			Server server = ServerBuilder.forPort(8080).addService(impl).build();
			server.start();
			System.out.println("Server started");
			new Thread(() -> {
				System.out.println("<Press enter to shutdown>");
				new Scanner(System.in).nextLine();

				server.shutdown();
			}).start();
			Signal.handle(new Signal("INT"), signal -> server.shutdown());
			server.awaitTermination();

		} catch (Exception e) {
			System.out.println("Internal Server Error: " + e.getMessage());
		} finally {
			System.out.println("Server closed");
			System.exit(0);
		}
	}

	public static void generateStore() {

		try {
			String[] keystore_array = new String[14];
			keystore_array[0] = "keytool";
			keystore_array[1] = "-genkey";
			keystore_array[2] = "-alias";
			keystore_array[3] = "server";
			keystore_array[4] = "-keyalg";
			keystore_array[5] = "RSA";
			keystore_array[6] = "-keystore";
			keystore_array[7] = "server.jks";
			keystore_array[8] = "-dname";
			keystore_array[9] = "CN=mqttserver.ibm.com, OU=ID, O=IBM, L=Hursley, S=Hantes, C=GB";
			keystore_array[10] = "-storepass";
			keystore_array[11] = "server123";
			keystore_array[12] = "-keypass";
			keystore_array[13] = "server123";

			ProcessBuilder builder = new ProcessBuilder(keystore_array);
			builder.start();

		} catch (IOException e) {
			System.out.println("ERROR while running Keytool commands.");
		}
	}

	public static KeyPair getKeyPair() {
		try {
			FileInputStream is = new FileInputStream("server.jks");
			String password = "server123";
			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			char[] passwd = password.toCharArray();
			keystore.load(is, passwd);
			Key key = keystore.getKey("server", passwd);
			Certificate cert = keystore.getCertificate("server");
			PublicKey publicKey = cert.getPublicKey();
			return new KeyPair(publicKey, (PrivateKey) key);
		} catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException | IOException e){
			System.out.println("Error while getting keypair from keystore.");
			return null;
		}
	}
}
