package pt.tecnico.bank.app;

import pt.tecnico.bank.ServerFrontend;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

public class AppMain {
	
	public static void main(String[] args) throws IOException, InterruptedException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		System.out.println(AppMain.class.getSimpleName());

		// Initialization of the HubFrontend, App and Tag HashMap
		ServerFrontend frontend = new ServerFrontend();
		App app = new App(frontend);

		// Initialization of the scanner to scan the input from the user/file
		Scanner scanner = new Scanner(System.in);
		String scanned, receiverUsername, username;
		String accountUsername = "";
		String accountPassword = "";
		int amount, transactionNumber;
		KeyPair keyPair = null;

		boolean logout = true;
		boolean login = false;

		while (!login) {
			System.out.print("" +
					"\n1) Open Account" +
					"\n2) Load Account" +
					"\n3) Quit" +
					"\nSelect Operation: ");

			scanned = scanner.nextLine();

			switch (scanned) {
				case "1":
					System.out.print("\nChoose your account username: ");
					accountUsername = scanner.nextLine();
					System.out.print("Choose your account password: ");
					accountPassword = scanner.nextLine();
					if (!existsAccount(accountUsername)) {
						generateStoreandCer(accountUsername, accountPassword);
						keyPair = getKeyPair(accountUsername, accountPassword);
						app.openAccount(keyPair.getPublic(), accountUsername);
						logout = false;
					} else {
						System.out.println("\nAccount already exists.");
					}
					break;

				case "2":
					System.out.print("\nAccount username: ");
					accountUsername = scanner.nextLine();
					System.out.print("Account password: ");
					accountPassword = scanner.nextLine();
					if (checkCredentials(accountUsername, accountPassword)) {
						keyPair = getKeyPair(accountUsername, accountPassword);
						logout = false;
						System.out.println("\nSuccessfully logged in.");
					}
					break;

				case "3":
					login = true;
					System.out.println("Exiting the app.");
					frontend.close();
					break;

				default:
					System.out.println("WARNING invalid input.");
					break;
			}

			while (!logout) {

				System.out.print("" +
						"\n1) Check Account" +
						"\n2) Send Amount" +
						"\n3) Receive Amount" +
						"\n4) Audit" +
						"\n5) Ping" +
						"\n6) Logout" +
						"\nSelect Operation: ");

				scanned = scanner.nextLine();

				switch (scanned) {

					case "1":
						System.out.print("\nAccount username: ");
						username = scanner.nextLine();
						if (existsAccount(username)){
							app.checkAccount(getPubkKeyfromCert(username));
						} else {
							System.out.println("No account found with that username.");
						}
						break;

					case "2":
						System.out.print("\nReceiver username: ");
						receiverUsername = scanner.nextLine();
						System.out.print("\nAmount: ");
						amount = Integer.parseInt(scanner.nextLine());
						if (existsAccount(receiverUsername)) {
							app.sendAmount(keyPair.getPublic(), getPubkKeyfromCert(receiverUsername), amount, keyPair.getPrivate());
						} else {
							System.out.println("No account found with that username.");
						}
						break;

					case "3":
						System.out.print("\nTransaction number: ");
						transactionNumber = Integer.parseInt(scanner.nextLine()) - 1;
						app.receiveAmount(keyPair.getPublic(), transactionNumber, keyPair.getPrivate());
						break;

					case "4":
						System.out.print("\nAccount username: ");
						username = scanner.nextLine();
						if (existsAccount(username)) {
							app.audit(getPubkKeyfromCert(username));
						} else {
							System.out.println("No account found with that username.");
						}
						break;

					case "5":
						app.ping();
						break;

					case "6":
						logout = true;
						System.out.println("Logging out.");
						break;

					default:
						System.out.println("WARNING invalid input.");
						break;
				}
			}
		}
	}

	public static void generateStoreandCer(String username, String password) throws IOException, InterruptedException {

		String[] keystore_array = new String[14];
		keystore_array[0] = "keytool";
		keystore_array[1] = "-genkey";
		keystore_array[2] = "-alias";
		keystore_array[3] = username;
		keystore_array[4] = "-keyalg";
		keystore_array[5] = "RSA";
		keystore_array[6] = "-keystore";
		keystore_array[7] = "Keystores/" + username + ".jks";
		keystore_array[8] = "-dname";
		keystore_array[9] = "CN=mqttserver.ibm.com, OU=ID, O=IBM, L=Hursley, S=Hantes, C=GB";
		keystore_array[10] = "-storepass";
		keystore_array[11] = password;
		keystore_array[12] = "-keypass";
		keystore_array[13] = password;

		ProcessBuilder builder = new ProcessBuilder(keystore_array);
		Process process = builder.start();
		process.waitFor();

		String[] certificate = new String[11];
		certificate[0] = "keytool";
		certificate[1] = "-v";
		certificate[2] = "-export";
		certificate[3] = "-alias";
		certificate[4] = username;
		certificate[5] = "-file";
		certificate[6] = "Certificates/" + username + ".cer";
		certificate[7] = "-keystore";
		certificate[8] = "Keystores/" + username + ".jks";
		certificate[9] = "-storepass";
		certificate[10] = password;

		builder.command(certificate).start();
	}

	public static KeyPair getKeyPair(String username, String password) throws IOException, CertificateException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {

		FileInputStream is = new FileInputStream("Keystores/" + username + ".jks");
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		char[] passwd = password.toCharArray();
		keystore.load(is, passwd);
		Key key = keystore.getKey(username, passwd);
		Certificate cert = keystore.getCertificate(username);

		// Get public key
		PublicKey publicKey = cert.getPublicKey();

		// Return a key pair
		return new KeyPair(publicKey, (PrivateKey) key);

        /*String test = "Gonçalo";
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        cipher.update(test.getBytes());
        final byte[] result = cipher.doFinal();

        System.out.println("Message: " + test);
        System.out.println("Encrypted: " + Base64.getEncoder().encodeToString(result));

        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedB = cipher.doFinal(result);
        String decrypted = new String(decryptedB, StandardCharsets.UTF_8);
        System.out.println(decrypted);*/
	}

	public static PublicKey getPubkKeyfromCert(String username) throws FileNotFoundException, CertificateException {
		FileInputStream fin = new FileInputStream("Certificates/" + username + ".cer");
		CertificateFactory f = CertificateFactory.getInstance("X.509");
		X509Certificate certificate1 = (X509Certificate)f.generateCertificate(fin);
		return certificate1.getPublicKey();
	}

	public static boolean checkCredentials(String username, String password) {
		try {
			FileInputStream is = new FileInputStream("Keystores/" + username + ".jks");
			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			char[] passwd = password.toCharArray();
			keystore.load(is, passwd);
			return true;
		} catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e){
			System.out.println("\nWrong Credentials.");
			return false;
		}
	}
	
	public static boolean existsAccount(String username) {
		try {
			new FileInputStream("Certificates/" + username + ".cer");
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}
}
