package pt.tecnico.bank.app;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.ServerFrontend;
import pt.tecnico.bank.grpc.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
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

    // VERIFICAR SE USERNAME JA EXISTE
    public void openAccount(PublicKey publicKey, String username) {
        /*FileInputStream fin = new FileInputStream("Certificates/" + lastID + ".cer");
        CertificateFactory f = CertificateFactory.getInstance("X.509");
        X509Certificate certificate1 = (X509Certificate)f.generateCertificate(fin);
        PublicKey pk = certificate1.getPublicKey();
        System.out.println(pk);*/

        OpenAccountRequest request = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).setUsername(username).build();
        if (frontend.openAccount(request).getAck()) {
            System.out.println("\nAccount created successfully with username: " + username + "\n");
        }
    }

    public void checkAccount(PublicKey publicKey){
        try {

            CheckAccountRequest request = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).build();

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

    public void sendAmount(PublicKey senderPubK, PublicKey receiverPubK, int amount, PrivateKey senderPrivK){
        try {
            SendAmountRequest request = SendAmountRequest.newBuilder()
                    .setSenderKey(ByteString.copyFrom(senderPubK.getEncoded()))
                    .setReceiverKey(ByteString.copyFrom(receiverPubK.getEncoded()))
                    .setAmount(amount).build();

            if (frontend.sendAmount(request).getAck()) {
                System.out.println("\nPending transaction, waiting for approval.\n");
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }

    public void receiveAmount(PublicKey publicKey, int transfer, PrivateKey privateKey) {
        try {
            Signature dsaForSign = Signature.getInstance("SHA256withRSA");
            dsaForSign.initSign(privateKey);
            dsaForSign.update(String.valueOf(transfer).getBytes());
            byte[] signature = dsaForSign.sign();
            int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
            long timeMilli = new Date().getTime();
            ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                    .setSignature(ByteString.copyFrom(signature))
                    .setTransfer(transfer)
                    .setNonce(random)
                    .setTimestamp(timeMilli)
                    .build();
            if (frontend.receiveAmount(request).getAck()) {
                System.out.println("\nTransaction Accepted.\n");
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            System.out.println("ERROR while signing.\n");
        }
    }

    public void audit(PublicKey publicKey){
        try {
            AuditRequest request = AuditRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).build();
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
}
