package pt.tecnico.bank.app;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslClientHelloHandler;
import pt.tecnico.bank.Crypto;
import pt.tecnico.bank.ServerFrontend;
import pt.tecnico.bank.grpc.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;

public class App {

    ServerFrontend frontend;
    Crypto crypto;

    public App(ServerFrontend frontend, Crypto crypto) {
        this.frontend = frontend;
        this.crypto = crypto;
    }

    // App methods that send requests to the ServerServiceImpl and returns responses to the user


    public void ping() {
        PingRequest request = PingRequest.newBuilder().setInput("Ping").build();
        PingResponse response = frontend.ping(request);
        if (response != null) {
            System.out.println("\n" + response.getOutput() + "\n");
        }
    }

    public boolean openAccount(PublicKey publicKey, String username, PrivateKey privateKey) {

        int random = crypto.getSecureRandom();
        String finalString1 = publicKey.toString() + username + random;
        byte[] signature = crypto.getSignature(finalString1, privateKey);

        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username)
                .setSignature(ByteString.copyFrom(signature))
                .setNonce(random)
                .build();

        OpenAccountResponse response = frontend.openAccount(request);

        if (response == null) {
            System.out.println("No quorum achieved!");
            return false;
        } else if (response.getMessage().equals("valid")) {
            System.out.println("\nAccount created successfully with username: " + username + "\n");
            return true;
        } else {
            System.out.println(response.getMessage());
            return false;
        }
    }

    public void checkAccount(PublicKey publicKey, KeyPair keyPair){

        int random = crypto.getSecureRandom();

        String finalString1 = publicKey.toString() + keyPair.getPublic().toString() + random;
        byte[] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());
        CheckAccountRequest request = CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setSignature(ByteString.copyFrom(signature1))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setNonce(random)
                .build();

        CheckAccountResponse response = frontend.checkAccount(request);


        if (response == null) {
            System.out.println("No quorum achieved!");
        }
        else if (response.getMessage().equals("valid")) {
            List<String> pending = response.getPendentTransfersList();

            if (pending.isEmpty()) {
                System.out.println("\nAvailable Balance: " + response.getBalance() + "\n\nNo pending transactions.\n");
            } else {
                System.out.println("\nAvailable Balance: " + response.getBalance() + "\n\nPending Transactions:");
                int i = 1;
                for (String p : pending) {
                    System.out.println(i + ") " + p);
                    i++;
                }
                System.out.println();
            }
        } else {
            System.out.println(response.getMessage());
        }
    }

    public void sendAmount(PublicKey senderPubK, PublicKey receiverPubK, int amount, PrivateKey senderPrivK){

        int random = crypto.getSecureRandom();
        byte [] signature = crypto.getSignature(senderPubK.toString() + amount + random + receiverPubK.toString(), senderPrivK);

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(senderPubK.getEncoded()))
                .setReceiverKey(ByteString.copyFrom(receiverPubK.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        SendAmountResponse response = frontend.sendAmount(request);

        if (response == null) {
            System.out.println("No quorum achieved!");
        }
        else if (response.getMessage().equals("valid")) {
            System.out.println("\nPending transaction, waiting for approval.\n");
        } else {
            System.out.println(response.getMessage());
        }
    }

    public void receiveAmount(PublicKey publicKey, int transfer, PrivateKey privateKey) {

        int random = crypto.getSecureRandom();
        byte [] signature = crypto.getSignature(publicKey.toString() + transfer + random, privateKey);

        ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setSignature(ByteString.copyFrom(signature))
                .setTransfer(transfer)
                .setNonce(random)
                .build();

        ReceiveAmountResponse response = frontend.receiveAmount(request);

        if (response == null) {
            System.out.println("No quorum achieved!");
        }
        else if (response.getMessage().equals("valid")) {
            System.out.println("\nTransaction Accepted.\n");
        } else {
            System.out.println("\n" + response.getMessage());
        }
    }

    public void audit(PublicKey publicKey, KeyPair keyPair){

        int random = crypto.getSecureRandom();
        String finalString1 = publicKey.toString() + keyPair.getPublic().toString() + random;
        byte[] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());

        AuditRequest request = AuditRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setSignature(ByteString.copyFrom(signature1))
                .setNonce(random)
                .build();

        AuditResponse response = frontend.audit(request);

        if (response == null) {
            System.out.println("No quorum achieved!");
        } else if (response.getMessage().equals("valid")) {
            List<String> history = response.getTransferHistoryList();

            if (history.isEmpty()) {
                System.out.println("\nNo history to be shown.\n");
            } else {
                System.out.println("\nHistory:");
                for (String p : history) {
                    System.out.println(p);
                }
                System.out.println();
            }
        } else {
            System.out.println(response.getMessage());
        }
    }
}