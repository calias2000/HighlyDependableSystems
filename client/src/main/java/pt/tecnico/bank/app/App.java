package pt.tecnico.bank.app;

import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.ServerFrontend;
import pt.tecnico.bank.grpc.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.List;

public class App {

    ServerFrontend frontend;

    public App(ServerFrontend frontend) {
        this.frontend = frontend;
    }

    // App methods that send requests to the HubServiceImpl and returns responses to the user
    // Before each server request the method "checkServerConnection" is run to connect to an available hub


    /**
     * Prints to the console the result of a PingRequest.
     */
    public int lastId() {
        LastIdRequest request = LastIdRequest.newBuilder().build();
        return frontend.lastId(request).getLastId() + 1;
    }


    public void ping() {
        PingRequest request = PingRequest.newBuilder().setInput("Ping").build();
        System.out.println("\n" + frontend.ping(request).getOutput() + "\n");
    }


    public void openAccount(String password) throws IOException, InterruptedException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        int lastID = lastId();
        System.out.println(lastID);

        String[] keystore_array = new String[14];
        keystore_array[0] = "keytool";
        keystore_array[1] = "-genkey";
        keystore_array[2] = "-alias";
        keystore_array[3] = String.valueOf(lastID);
        keystore_array[4] = "-keyalg";
        keystore_array[5] = "RSA";
        keystore_array[6] = "-keystore";
        keystore_array[7] = "Keystores/" + lastID + ".jks";
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
        certificate[4] = String.valueOf(lastID);
        certificate[5] = "-file";
        certificate[6] = "Certificates/" + lastID + ".cer";
        certificate[7] = "-keystore";
        certificate[8] = "Keystores/" + lastID + ".jks";
        certificate[9] = "-storepass";
        certificate[10] = password;

        builder.command(certificate).start();

        FileInputStream is = new FileInputStream("Keystores/" + lastID + ".jks");

        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] passwd = password.toCharArray();
        keystore.load(is, passwd);
        String alias = String.valueOf(lastID);
        Key key = keystore.getKey(alias, passwd);
        if (key instanceof PrivateKey) {
            Certificate cert = keystore.getCertificate(alias);
            PublicKey publicKey = cert.getPublicKey();

            String test = "Gonçalo";
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            cipher.update(test.getBytes());
            final byte[] result = cipher.doFinal();

            System.out.println("Message: " + test);
            System.out.println("Encrypted: " + Base64.getEncoder().encodeToString(result));

            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decryptedB = cipher.doFinal(result);
            String decrypted = new String(decryptedB, StandardCharsets.UTF_8);
            System.out.println(decrypted);
        }


        OpenAccountRequest request = OpenAccountRequest.newBuilder().setPublicKey(password).build();
        if (frontend.openAccount(request).getAck()) {
            System.out.println("\nAccount created successfuly with ID: " + lastId() + ".\n");
        }
    }

    public void checkAccount(String pubkey){
        try {
            CheckAccountRequest request = CheckAccountRequest.newBuilder().setPublicKey(pubkey).build();
            List<String> pending = frontend.checkAccount(request).getPendentTransfersList();

            if (pending.isEmpty()) {
                System.out.println("\nAvailable Balance: " + frontend.checkAccount(request).getBalance() + "\n\nNo pending transactions.\n");
            } else {
                System.out.println("\nAvailable Balance: " + frontend.checkAccount(request).getBalance() + "\n\nPending Transactions:");
                int i = 1;
                for (String p : pending) {
                    System.out.println(i + ") " + p);
                    i++;
                }
                System.out.println();
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }

    public void audit(String pubkey){
        try {
            AuditRequest request = AuditRequest.newBuilder().setPublicKey(pubkey).build();
            List<String> history = frontend.audit(request).getTransferHistoryList();

            if (history.isEmpty()) {
                System.out.println("\nNo history to be shown.\n");
            } else {
                System.out.println("\nHistory:");

                for (String p : history) {
                    System.out.println(p);
                }

                System.out.println();
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }

    public void sendAmount(String keySender, String keyReceiver, int value){
        try {
            SendAmountRequest request = SendAmountRequest.newBuilder().setSenderKey(keySender).setReceiverKey(keyReceiver).setAmount(value).build();
            if (frontend.sendAmount(request).getAck()) {
                System.out.println("\nPending transaction, waiting for approval.\n");
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }

    public void receiveAmount(String pubkey, int transfer) {
        try {
            ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder().setPublicKey(pubkey).setTransfer(transfer).build();
            if (frontend.receiveAmount(request).getAck()) {
                System.out.println("\nTransaction Accepted.\n");
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }
}
