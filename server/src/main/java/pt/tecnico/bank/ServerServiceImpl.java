package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Transactions;
import pt.tecnico.bank.grpc.*;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static io.grpc.Status.INVALID_ARGUMENT;
import static pt.tecnico.bank.ServerMain.*;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        String input = request.getInput();

        if (input.isBlank()) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Input cannot be empty!").asRuntimeException());
            return;
        }

        String output = input + "Pong";
        PingResponse response = PingResponse.newBuilder().setOutput(output).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void openAccount(OpenAccountRequest request, StreamObserver<OpenAccountResponse> responseObserver) {
        String message = "";
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            String username = request.getUsername();
            String finalString1 = publicKey.toString() + username;
            if (verifySignature(finalString1, publicKey, request.getSignature().toByteArray())) {

                message = "valid";
                clientList.put(publicKey, new Client(username));
                saveHandler.saveState();

            } else {
                message = "Incorrect signature.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        String finalString = keyPair.getPublic().toString() + message;
        byte[] signature = getSignature(finalString);

        OpenAccountResponse response = OpenAccountResponse.newBuilder().setMessage(message)
                .setSignature(ByteString.copyFrom(signature))
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded())).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        try {

            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            PublicKey mypublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getMyPublicKey().toByteArray()));

            String finalString1 = publicKey.toString() + mypublicKey.toString();

            if (verifySignature(finalString1, mypublicKey, request.getSignature().toByteArray())) {

                List<String> pending = new ArrayList<>();
                Client client = clientList.get(publicKey);

                for (int i = 0; i < client.getPending().size(); i++) {
                    pending.add(client.getPending().get(i).getValue() + " from " + client.getPending().get(i).getUsername());
                }

                String finalString = keyPair.getPublic().toString() + client.getBalance() + pending;
                byte[] signature = getSignature(finalString);

                CheckAccountResponse response = CheckAccountResponse.newBuilder()
                        .setBalance(client.getBalance())
                        .addAllPendentTransfers(pending)
                        .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                        .setSignature(ByteString.copyFrom(signature))
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

            } else {
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Incorrect signature.").asRuntimeException());
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Something wrong with the keys!").asRuntimeException());
        }
    }

    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {

        String message = "";
        int amount = request.getAmount();
        int nonce = request.getNonce();

        try {
            PublicKey keySender = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getSenderKey().toByteArray()));
            PublicKey keyReceiver = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getReceiverKey().toByteArray()));

            String finalString = keySender.toString() + amount + nonce + keyReceiver.toString();
            byte [] signature = request.getSignature().toByteArray();

            Client clientSender = clientList.get(keySender);

            if (verifySignature(finalString, keySender, signature) && clientSender.getEventList().get(nonce) == null) {

                clientSender.addEvent(nonce);

                Client clientReceiver = clientList.get(keyReceiver);

                if (clientSender.getBalance() < amount) {
                    message = "Sender account does not have enough balance.";
                } else if (0 >= amount) {
                    message = "Invalid amount, must be > 0.";
                } else {

                    message = "valid";

                    clientReceiver.addPending(new Transactions(clientSender.getUsername(), amount, keySender, signature));

                    saveHandler.saveState();
                }
            } else {
                message = "Incorrect signature, repeated event or incorrect transaction id.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        int nonce1 = nonce + 1;

        String finalString1 = keyPair.getPublic().toString() + message + nonce1;
        byte [] signature1 = getSignature(finalString1);

        SendAmountResponse response = SendAmountResponse.newBuilder()
                .setMessage(message)
                .setNonce(nonce1)
                .setSignature(ByteString.copyFrom(signature1))
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {

        String message = "";
        int transfer = request.getTransfer();
        int nonce = request.getNonce();

        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            String finalString = publicKey.toString() + transfer + nonce;
            byte [] signature = request.getSignature().toByteArray();

            Client client = clientList.get(publicKey);

            if (verifySignature(finalString, publicKey, signature) && client.getEventList().get(nonce) == null && transfer + 1 <= client.getPending().size()) {

                message = "valid";
                client.addEvent(nonce);

                Transactions transaction = client.getPending().get(transfer);
                client.setBalance(client.getBalance() + transaction.getValue());

                Client client2 = clientList.get(transaction.getPublicKey());
                client2.setBalance(client2.getBalance() - transaction.getValue());

                client.removePending(transfer);
                client.addHistory(new Transactions(transaction.getUsername(), transaction.getValue(), transaction.getPublicKey()));
                client2.addHistory(new Transactions(client.getUsername(), -transaction.getValue(), publicKey));

                saveHandler.saveState();

            } else {
                message = "Incorrect signature, repeated event or incorrect transaction id.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        int nonce1 = nonce + 1;

        String finalString1 = keyPair.getPublic().toString() + message + nonce1;
        byte [] signature1 = getSignature(finalString1);

        ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder()
                .setMessage(message)
                .setNonce(nonce1)
                .setSignature(ByteString.copyFrom(signature1))
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {

        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            PublicKey mypublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getMyPublicKey().toByteArray()));

            String finalString1 = publicKey.toString() + mypublicKey.toString();

            if (verifySignature(finalString1, mypublicKey, request.getSignature().toByteArray())) {

                List<String> history = new ArrayList<>();
                Client client = clientList.get(publicKey);

                for (int i = 0; i < client.getHistory().size(); i++) {
                    if (client.getHistory().get(i).getValue() < 0) {
                        history.add(Math.abs(client.getHistory().get(i).getValue()) + " to " + client.getHistory().get(i).getUsername());
                    } else {
                        history.add(Math.abs(client.getHistory().get(i).getValue()) + " from " + client.getHistory().get(i).getUsername());
                    }
                }

                String finalString = keyPair.getPublic().toString() + history.toString();
                byte[] signature = getSignature(finalString);

                AuditResponse response = AuditResponse.newBuilder()
                        .addAllTransferHistory(history)
                        .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                        .setSignature(ByteString.copyFrom(signature))
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

            } else {
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Incorrect signature.").asRuntimeException());
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Something wrong with the keys!").asRuntimeException());
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

    public byte[] getSignature(String finalString) {
        try {
            Signature dsaForSign = Signature.getInstance("SHA256withRSA");
            dsaForSign.initSign(keyPair.getPrivate());
            dsaForSign.update(finalString.getBytes());
            return dsaForSign.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            System.out.println("Something went wrong while signign.");
            return null;
        }
    }
}