package pt.tecnico.bank.app;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.ServerFrontend;
import pt.tecnico.bank.grpc.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
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
        PingResponse response = frontend.ping(request);
        if (response != null) {
            System.out.println("\n" + response.getOutput() + "\n");
        }
    }

    public void openAccount(PublicKey publicKey, String username, PrivateKey privateKey) {

        try {

            String finalString1 = publicKey.toString() + username;
            byte[] signature = getSignature(finalString1, privateKey);

            OpenAccountRequest request = OpenAccountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                    .setUsername(username)
                    .setSignature(ByteString.copyFrom(signature))
                    .build();

            OpenAccountResponse response = frontend.openAccount(request);

            if (response != null) {
                PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                String finalString = serverPubKey.toString() + response.getAck();

                if (verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()) && response.getAck()) {
                    System.out.println("\nAccount created successfully with username: " + username + "\n");
                } else {
                    System.out.println("Be careful, wrong signing from the server.");
                }
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e){
            System.out.println("Wrong algorithm.");
        }
    }

    public void checkAccount(PublicKey publicKey, KeyPair keyPair){
        try {

            String finalString1 = publicKey.toString() + keyPair.getPublic().toString();
            byte[] signature1 = getSignature(finalString1, keyPair.getPrivate());
            CheckAccountRequest request = CheckAccountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                    .setSignature(ByteString.copyFrom(signature1))
                    .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                    .build();

            CheckAccountResponse response = frontend.checkAccount(request);

            if (response != null) {

                List<String> pending = response.getPendentTransfersList();

                PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                String finalString = serverPubKey.toString() + response.getBalance() + response.getPendentTransfersList().toString();

                if (verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {

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
                } else {
                    System.out.println("Be careful, wrong signing from the server.");
                }

            }

        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Wrong algorithm.");
        }
    }

    public void sendAmount(PublicKey senderPubK, PublicKey receiverPubK, int amount, PrivateKey senderPrivK){
        try {

            int random = getSecureRandom();
            long timeMilli = getTimeMillis();
            byte [] signature = getSignature(senderPubK.toString() + amount + random + timeMilli + receiverPubK.toString(), senderPrivK);

            SendAmountRequest request = SendAmountRequest.newBuilder()
                    .setSenderKey(ByteString.copyFrom(senderPubK.getEncoded()))
                    .setReceiverKey(ByteString.copyFrom(receiverPubK.getEncoded()))
                    .setAmount(amount)
                    .setNonce(random)
                    .setTimestamp(timeMilli)
                    .setSignature(ByteString.copyFrom(signature))
                    .build();

            SendAmountResponse response = frontend.sendAmount(request);

            if (response != null) {
                PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                String finalString = serverPubKey.toString() + response.getAck() + response.getNonce() + response.getTimestamp();

                if (verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()) && random + 1 == response.getNonce() && timeMilli < response.getTimestamp() && response.getAck()) {
                    System.out.println("\nPending transaction, waiting for approval.\n");
                } else {
                    System.out.println("Be careful, wrong signing from the server.");
                }
            }

        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Wrong algorithm.");
        }
    }

    public void receiveAmount(PublicKey publicKey, int transfer, PrivateKey privateKey) {
        try {
            int random = getSecureRandom();
            long timeMilli = getTimeMillis();
            byte [] signature = getSignature(publicKey.toString() + transfer + random + timeMilli, privateKey);
            ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                    .setSignature(ByteString.copyFrom(signature))
                    .setTransfer(transfer)
                    .setNonce(random)
                    .setTimestamp(timeMilli)
                    .build();

            ReceiveAmountResponse response = frontend.receiveAmount(request);

            if (response != null) {
                PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                String finalString = serverPubKey.toString() + response.getAck() + response.getNonce() + response.getTimestamp();

                if (verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()) && random + 1 == response.getNonce() && timeMilli < response.getTimestamp() && response.getAck()) {
                    System.out.println("\nTransaction Accepted.\n");
                } else {
                    System.out.println("Be careful, wrong signing from the server.");
                }
            }

        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Wrong algorithm.");
        }
    }

    public void audit(PublicKey publicKey, KeyPair keyPair){
        try {

            String finalString1 = publicKey.toString() + keyPair.getPublic().toString();
            byte[] signature1 = getSignature(finalString1, keyPair.getPrivate());

            AuditRequest request = AuditRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                    .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                    .setSignature(ByteString.copyFrom(signature1))
                    .build();

            AuditResponse response = frontend.audit(request);

            if (response != null) {
                List<String> history = response.getTransferHistoryList();

                PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                String finalString = serverPubKey.toString() + history.toString();

                if (verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
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
                    System.out.println("Be careful, wrong signing from the server.");
                }
            }

        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Wrong algorithm.");
        }
    }

    public int getSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG").nextInt();
        } catch (NoSuchAlgorithmException e){
            System.out.println("Wrong Algorithm.");
            return 0;
        }
    }

    public long getTimeMillis() {
        return new Date().getTime();
    }

    public byte[] getSignature(String finalString, PrivateKey privateKey) {
        try {
            Signature dsaForSign = Signature.getInstance("SHA256withRSA");
            dsaForSign.initSign(privateKey);
            dsaForSign.update(finalString.getBytes());
            return dsaForSign.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            System.out.println("Something went wrong while signing.");
            return null;
        }
    }

    public boolean verifySignature(String finalString, PublicKey publicKey, byte[] signature){
        try {
            Signature dsaForVerify = Signature.getInstance("SHA256withRSA");
            dsaForVerify.initVerify(publicKey);
            dsaForVerify.update(finalString.getBytes());
            return dsaForVerify.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e){
            System.out.println("Signatures don't match.");
            return false;
        }
    }
}