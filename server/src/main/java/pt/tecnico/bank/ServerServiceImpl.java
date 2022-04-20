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

        try {
            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            String username = request.getUsername();
            int wid = request.getWid();
            int balance = request.getBalance();
            byte [] pair_signature = request.getPairSign().toByteArray();

            String finalString1 = publicKey.toString() + username + wid + balance + Arrays.toString(pair_signature);
            if (crypto.verifySignature(finalString1, publicKey, request.getSignature().toByteArray())) {

                message = "valid";
                clientList.put(publicKey, new Client(username, pair_signature));
                saveHandler.saveState();

            } else {
                message = "Incorrect signature.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        String finalString = keyPair.getPublic().toString() + message;
        byte[] signature = crypto.getSignature(finalString, keyPair.getPrivate());

        OpenAccountResponse response = OpenAccountResponse.newBuilder().setMessage(message)
                .setSignature(ByteString.copyFrom(signature))
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {

        int balance = 0;
        int wid = 0;

        String message = "";
        List<Transaction> transactions = new ArrayList<>();
        int rid = request.getRid();
        int nonce = request.getNonce();
        byte [] pairSignature = null;

        try {

            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            PublicKey mypublicKey = crypto.getPubKeyGrpc(request.getMyPublicKey().toByteArray());

            if (clientList.containsKey(publicKey)) {

                Client client = clientList.get(publicKey);
                Client me = clientList.get(mypublicKey);

                balance = client.getBalance();
                wid = client.getWid();
                pairSignature = me.getPair_signature();

                if (!clientList.get(publicKey).getEventList().contains(nonce)) {

                    clientList.get(publicKey).addEvent(nonce);

                    for (Transactions transaction : client.getPending()){
                        transactions.add(Transaction.newBuilder()
                                .setSourceUsername(transaction.getSenderUsername())
                                .setDestUsername(transaction.getDestUsername())
                                .setAmount(transaction.getValue())
                                .setSource(ByteString.copyFrom(transaction.getSourceKey().getEncoded()))
                                .setDestination(ByteString.copyFrom(transaction.getDestKey().getEncoded()))
                                .setWid(transaction.getWid())
                                .setSignature(ByteString.copyFrom(transaction.getSignature()))
                                .build());
                    }

                    /*me.incrementRid();
                    rid++;*/

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

        String finalString = keyPair.getPublic().toString() + balance + wid + Arrays.toString(pairSignature) + rid + message + transactions + nonce1;
        byte[] signature = crypto.getSignature(finalString, keyPair.getPrivate());

        CheckAccountResponse response = CheckAccountResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setBalance(balance)
                .setWid(wid)
                .setPairSign(ByteString.copyFrom(pairSignature))
                .setRid(rid)
                .setMessage(message)
                .addAllTransactions(transactions)
                .setNonce(nonce1)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public synchronized void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {

        String message = "";
        Transaction transaction = request.getTransaction();
        Client clientSender = null;

        String sourceUsername = transaction.getSourceUsername();
        String destUsername = transaction.getDestUsername();
        int amount = transaction.getAmount();
        byte [] transactionSignature = transaction.getSignature().toByteArray();

        int wid = transaction.getWid();
        int balance = request.getBalance();
        byte [] pairSign = request.getPairSign().toByteArray();

        try {
            PublicKey keySender = crypto.getPubKeyGrpc(transaction.getSource().toByteArray());
            PublicKey keyReceiver = crypto.getPubKeyGrpc(transaction.getDestination().toByteArray());

            String finalString = sourceUsername + destUsername + amount + keySender.toString() + keyReceiver.toString() + Arrays.toString(transactionSignature) + wid + balance + Arrays.toString(pairSign);
            byte [] signature = request.getSignature().toByteArray();

            clientSender = clientList.get(keySender);

            if (crypto.verifySignature(finalString, keySender, signature) && clientSender.getWid() < wid) {

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

                Client clientReceiver = clientList.get(keyReceiver);

                if (clientSender.getBalance() < amount || clientSender.getBalance() < clientSender.getPendent_balance()) {
                    message = "Sender account does not have enough balance.";
                } else if (0 >= amount) {
                    message = "Invalid amount, must be > 0.";
                } else {

                    message = "valid";

                    clientReceiver.addPending(new Transactions(sourceUsername, destUsername, amount, keySender, keyReceiver, wid, transactionSignature));

                    clientSender.addPendentBalance(amount);
                    clientSender.setPairSign(pairSign);

                    saveHandler.saveState();
                }
            } else {
                message = "Incorrect signature, repeated event or incorrect transaction id.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        String finalString1 = keyPair.getPublic().toString() + message + clientSender.getWid();
        byte [] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());

        SendAmountResponse response = SendAmountResponse.newBuilder()
                .setMessage(message)
                .setWid(clientSender.getWid())
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setSignature(ByteString.copyFrom(signature1))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {

        String message = "";
        int transfer = request.getTransfer();
        int wid = request.getWid();
        int amount = 0;
        int balance = 0;
        byte [] pairSign = null;

        try {
            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            String finalString = publicKey.toString() + transfer + wid;
            byte [] signature = request.getSignature().toByteArray();

            Client client = clientList.get(publicKey);

            if (crypto.verifySignature(finalString, publicKey, signature) && client.getWid() < wid && transfer + 1 <= client.getPending().size()) {

                message = "valid";

                Transactions transaction = client.getPending().get(transfer);
                client.setBalance(client.getBalance() + transaction.getValue());
                amount = transaction.getValue();

                Client client2 = clientList.get(transaction.getSourceKey());
                client2.setBalance(client2.getBalance() - transaction.getValue());
                client2.addPendentBalance(-transaction.getValue());

                client.removePending(transfer);
                client.addHistory(transaction);

                transaction.setValue(-transaction.getValue());
                client2.addHistory(transaction);

                saveHandler.saveState();

                client.incrementWid();
                wid++;
                balance = client.getBalance();
                pairSign = client.getPair_signature();

            } else {
                message = "Incorrect signature, repeated event or incorrect transaction id.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        String finalString1 = keyPair.getPublic().toString() + message + amount + balance + wid + Arrays.toString(pairSign);
        byte [] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());

        ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiveAmount(amount)
                .setBalance(balance)
                .setWid(wid)
                .setPairSign(ByteString.copyFrom(pairSign))
                .setMessage(message)
                .setSignature(ByteString.copyFrom(signature1))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {

        String message = "";
        int nonce = request.getNonce();
        int rid = request.getRid();
        List<Transaction> transactions = new ArrayList<>();

        try {
            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            PublicKey mypublicKey = crypto.getPubKeyGrpc(request.getMyPublicKey().toByteArray());

            if (clientList.containsKey(publicKey)) {

                Client client = clientList.get(publicKey);

                if (!clientList.get(publicKey).getEventList().contains(nonce)) {

                    clientList.get(publicKey).addEvent(nonce);

                    for (Transactions transaction : client.getHistory()){
                        transactions.add(Transaction.newBuilder()
                                .setSourceUsername(transaction.getSenderUsername())
                                .setDestUsername(transaction.getDestUsername())
                                .setAmount(transaction.getValue())
                                .setSource(ByteString.copyFrom(transaction.getSourceKey().getEncoded()))
                                .setDestination(ByteString.copyFrom(transaction.getDestKey().getEncoded()))
                                .setWid(transaction.getWid())
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

        String finalString = keyPair.getPublic().toString() + transactions + nonce1 + rid + message;
        byte[] signature = crypto.getSignature(finalString, keyPair.getPrivate());

        AuditResponse response = AuditResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .addAllTransactions(transactions)
                .setNonce(nonce1)
                .setRid(rid)
                .setMessage(message)
                .setSignature(ByteString.copyFrom(signature))
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}