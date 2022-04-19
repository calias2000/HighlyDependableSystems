package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Transactions;
import pt.tecnico.bank.grpc.*;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
        int nonce = request.getNonce();

        try {
            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            String username = request.getUsername();
            String finalString1 = publicKey.toString() + username + nonce;
            if (clientList.containsKey(publicKey)) {
                Client client = clientList.get(publicKey);
                if (client.getEventList().contains(nonce)){
                    message = "Replay Attack";
                } else {
                    message = "Client already exists!";
                }
            }
            else if (crypto.verifySignature(finalString1, publicKey, request.getSignature().toByteArray())) {

                message = "valid";
                clientList.put(publicKey, new Client(username));

                clientList.get(publicKey).addEvent(nonce);
                saveHandler.saveState();

            } else {
                message = "Incorrect signature.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        int nonce1 = nonce + 1;

        String finalString = keyPair.getPublic().toString() + message + nonce1;
        byte[] signature = crypto.getSignature(finalString, keyPair.getPrivate());

        OpenAccountResponse response = OpenAccountResponse.newBuilder().setMessage(message)
                .setSignature(ByteString.copyFrom(signature))
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setNonce(nonce1)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {

        int nonce = request.getNonce();
        List<String> pending = new ArrayList<>();
        int balance = 0;
        String message = "";
        List<Transaction> transactions = new ArrayList<>();

        try {

            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());

            if (clientList.containsKey(publicKey)) {

                Client client = clientList.get(publicKey);

                balance = client.getBalance();

                if (!clientList.get(publicKey).getEventList().contains(nonce)) {

                        clientList.get(publicKey).addEvent(nonce);

                        for (Transactions transaction : client.getPending()){
                            transactions.add(Transaction.newBuilder()
                                    .setUsername(transaction.getUsername())
                                    .setAmount(transaction.getValue())
                                    .setPublicKey(ByteString.copyFrom(transaction.getPublicKey().getEncoded()))
                                    .setSignature(ByteString.copyFrom(transaction.getSignature()))
                                    .build());
                        }

                        message = "valid";
                } else {
                    message = "Replay attack!";
                }
            } else {
                message = "No account found with that username.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            message = "Something wrong with the keys!";
        }

        int nonce1 = nonce + 1;

        String finalString = keyPair.getPublic().toString() + balance + nonce1 + message + transactions;
        byte[] signature = crypto.getSignature(finalString, keyPair.getPrivate());

        CheckAccountResponse response = CheckAccountResponse.newBuilder()
                .setBalance(balance)
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setSignature(ByteString.copyFrom(signature))
                .setNonce(nonce1)
                .setMessage(message)
                .addAllTransactions(transactions)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public synchronized void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {

        String message = "";
        Transaction transaction = request.getTransaction();

        String username = transaction.getUsername();
        int amount = transaction.getAmount();
        byte [] transactionSignature = transaction.getSignature().toByteArray();

        int nonce = request.getNonce();

        try {
            PublicKey keySender = crypto.getPubKeyGrpc(transaction.getPublicKey().toByteArray());
            PublicKey keyReceiver = crypto.getPubKeyGrpc(request.getReceiverKey().toByteArray());

            String finalString = username + amount + keySender.toString() + Arrays.toString(transactionSignature) + keyReceiver.toString() + nonce;
            byte [] signature = request.getSignature().toByteArray();

            Client clientSender = clientList.get(keySender);

            if (crypto.verifySignature(finalString, keySender, signature) && !clientSender.getEventList().contains(nonce)) {

                input = finalString;
                adeb.reset();
                adeb.deliveredLatch = new CountDownLatch(1);
                adeb.echo(finalString);

                try {
                    adeb.deliveredLatch.await();
                } catch (InterruptedException e) {
                    System.out.println("Error");
                }

                System.out.println("ECHO " + adeb.echos + "\nREADY " + adeb.ready);

                clientSender.addEvent(nonce);

                Client clientReceiver = clientList.get(keyReceiver);

                if (clientSender.getBalance() < amount) {
                    message = "Sender account does not have enough balance.";
                } else if (0 >= amount) {
                    message = "Invalid amount, must be > 0.";
                } else {

                    message = "valid";

                    clientReceiver.addPending(new Transactions(username, amount, keySender, transactionSignature));

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
        byte [] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());

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

            if (crypto.verifySignature(finalString, publicKey, signature) && !client.getEventList().contains(nonce) && transfer + 1 <= client.getPending().size()) {

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
        byte [] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());

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

        List<String> history = new ArrayList<>();
        String message = "";
        int nonce = request.getNonce();

        try {
            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());

            if (clientList.containsKey(publicKey)) {

                Client client = clientList.get(publicKey);

                if (!clientList.get(publicKey).getEventList().contains(nonce)) {

                    clientList.get(publicKey).addEvent(nonce);

                    for (int i = 0; i < client.getHistory().size(); i++) {
                        if (client.getHistory().get(i).getValue() < 0) {
                            history.add(Math.abs(client.getHistory().get(i).getValue()) + " to " + client.getHistory().get(i).getUsername());
                        } else {
                            history.add(Math.abs(client.getHistory().get(i).getValue()) + " from " + client.getHistory().get(i).getUsername());
                        }
                    }

                    message = "valid";

                } else {
                    message = "Replay attack!";
                }
            } else {
                message = "No account found with that username.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            message = "Something wrong with the keys!";
        }

        int nonce1 = nonce + 1;

        String finalString = keyPair.getPublic().toString() + history + nonce1 + message;
        byte[] signature = crypto.getSignature(finalString, keyPair.getPrivate());

        AuditResponse response = AuditResponse.newBuilder()
                .addAllTransferHistory(history)
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setSignature(ByteString.copyFrom(signature))
                .setNonce(nonce1)
                .setMessage(message)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}