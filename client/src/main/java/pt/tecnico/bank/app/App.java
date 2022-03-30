package pt.tecnico.bank.app;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.ServerFrontend;
import pt.tecnico.bank.grpc.*;
import java.security.*;
import java.util.Date;
import java.util.List;

public class App {

    ServerFrontend frontend;

    public App(ServerFrontend frontend) {
        this.frontend = frontend;
    }

    // App methods that send requests to the ServerServiceImpl and returns responses to the user


    public void ping() {
        PingRequest request = PingRequest.newBuilder().setInput("Ping").build();
        System.out.println("\n" + frontend.ping(request).getOutput() + "\n");
    }

    public void openAccount(PublicKey publicKey, String username) {
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
            int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
            long timeMilli = new Date().getTime();
            String finalString = senderPubK.toString() + amount + random + timeMilli + receiverPubK.toString();
            Signature dsaForSign = Signature.getInstance("SHA256withRSA");
            dsaForSign.initSign(senderPrivK);
            dsaForSign.update(finalString.getBytes());
            byte[] signature = dsaForSign.sign();
            SendAmountRequest request = SendAmountRequest.newBuilder()
                    .setSenderKey(ByteString.copyFrom(senderPubK.getEncoded()))
                    .setReceiverKey(ByteString.copyFrom(receiverPubK.getEncoded()))
                    .setAmount(amount)
                    .setNonce(random)
                    .setTimestamp(timeMilli)
                    .setSignature(ByteString.copyFrom(signature))
                    .build();

            if (frontend.sendAmount(request).getAck()) {
                System.out.println("\nPending transaction, waiting for approval.\n");
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            System.out.println("ERROR while signing.\n");
        }
    }

    public void receiveAmount(PublicKey publicKey, int transfer, PrivateKey privateKey) {
        try {
            int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
            long timeMilli = new Date().getTime();
            String finalString = publicKey.toString() + transfer + random + timeMilli;
            Signature dsaForSign = Signature.getInstance("SHA256withRSA");
            dsaForSign.initSign(privateKey);
            dsaForSign.update(finalString.getBytes());
            byte[] signature = dsaForSign.sign();
            ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
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