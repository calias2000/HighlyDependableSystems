package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Transactions;
import pt.tecnico.bank.grpc.*;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.grpc.Status.INVALID_ARGUMENT;
import static pt.tecnico.bank.ServerMain.*;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    private ADEBInstanceManager adebInstanceManager;
    private ADEB adeb;

    public ServerServiceImpl(ADEB adeb, ADEBInstanceManager adebInstanceManager){
        this.adebInstanceManager = adebInstanceManager;
        this.adeb = adeb;
    }


    public void rid(RidRequest request, StreamObserver<RidResponse> responseObserver){
        String message = "";
        int rid = 0;

        try {
            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            rid = clientList.get(publicKey).getRid();
            message = "valid";
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            message = "Something wrong with the keys!";
        }

        String finalString = keyPair.getPublic().toString() + rid + message;
        byte[] signature = crypto.getSignature(finalString, keyPair.getPrivate());

        RidResponse response = RidResponse.newBuilder()
                .setServerPubKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setRid(rid)
                .setMessage(message)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

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

        OpenAccountResponse response = OpenAccountResponse.newBuilder()
                .setMessage(message)
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
        byte [] pairSignature = new byte[0];

        try {

            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            PublicKey mypublicKey = crypto.getPubKeyGrpc(request.getMyPublicKey().toByteArray());

            if (clientList.containsKey(publicKey)) {

                Client client = clientList.get(publicKey);
                Client me = clientList.get(mypublicKey);

                balance = client.getBalance();
                wid = client.getWid();
                pairSignature = client.getPair_signature();

                if (!me.getEventList().contains(nonce) && rid > me.getRid()) {

                    me.addEvent(nonce);

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

                    me.incrementRid();

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

    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {

        String message = "";
        Transaction transaction = request.getTransaction();
        Client clientSender = null;

        String sourceUsername = transaction.getSourceUsername();
        String destUsername = transaction.getDestUsername();
        int amount = transaction.getAmount();
        byte [] transactionSignature = transaction.getSignature().toByteArray();

        int new_balance = request.getNewBalance();
        int wid = transaction.getWid();
        byte [] pairSign = request.getPairSign().toByteArray();

        try {
            PublicKey keySender = crypto.getPubKeyGrpc(transaction.getSource().toByteArray());
            PublicKey keyReceiver = crypto.getPubKeyGrpc(transaction.getDestination().toByteArray());

            String finalString = sourceUsername + destUsername + amount
                    + keySender.toString() + keyReceiver.toString()
                    + Arrays.toString(transactionSignature) + wid + Arrays.toString(pairSign) + new_balance;

            byte [] signature = request.getSignature().toByteArray();

            clientSender = clientList.get(keySender);

            if (crypto.verifySignature(finalString, keySender, signature)) {

                System.out.println("ADDEB STARTING");
                ADEBInstance instance = adebInstanceManager.getInstance(finalString);
                adeb.echo(finalString);
                instance.await();
                System.out.println("ADEB finished!");

                if (clientSender.getWid() < wid && clientSender.getBalance() - amount == new_balance) {

                    Client clientReceiver = clientList.get(keyReceiver);

                    if (clientSender.getBalance() < amount) {
                        message = "Sender account does not have enough balance.";
                    } else if (0 >= amount) {
                        message = "Invalid amount, must be > 0.";
                    } else {

                        message = "valid";

                        clientReceiver.addPending(new Transactions(sourceUsername, destUsername, amount, keySender, keyReceiver, wid, transactionSignature));

                        clientSender.addHistory(new Transactions(sourceUsername, destUsername, -amount, keySender, keyReceiver, wid, transactionSignature));
                        clientSender.setBalance(new_balance);
                        clientSender.incrementWid();
                        clientSender.setPairSign(pairSign);

                        saveHandler.saveState();
                    }
                } else {
                    message = "Wrong balance or wid.";
                }
            } else {
                message = "Incorrect signature or incorrect transaction id.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        String finalString1 = keyPair.getPublic().toString() + message + wid;
        byte [] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());

        SendAmountResponse response = SendAmountResponse.newBuilder()
                .setMessage(message)
                .setWid(wid)
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
        int new_balance = request.getFutureBalance();
        byte [] pairSign = request.getPairSign().toByteArray();
        Transaction toAuditTransaction = request.getToAuditTransaction();

        try {
            PublicKey publicKey = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
            String finalString = publicKey.toString() + new_balance + wid + Arrays.toString(pairSign) + transfer + toAuditTransaction;
            byte [] signature = request.getSignature().toByteArray();

            Client client = clientList.get(publicKey);

            if (crypto.verifySignature(finalString, publicKey, signature)) {


                //ADEB

                Transactions transaction = client.getPending().get(transfer);

                if (client.getWid() < wid && transfer + 1 <= client.getPending().size() && transaction.getValue() + client.getBalance() == new_balance) {
                    message = "valid";

                    client.setBalance(new_balance);

                    client.removePending(transfer);
                    client.addHistory(new Transactions(toAuditTransaction.getSourceUsername(),
                            toAuditTransaction.getDestUsername(),
                            toAuditTransaction.getAmount(),
                            crypto.getPubKeyGrpc(toAuditTransaction.getSource().toByteArray()),
                            crypto.getPubKeyGrpc(toAuditTransaction.getDestination().toByteArray()),
                            toAuditTransaction.getWid(), toAuditTransaction.getSignature().toByteArray()));

                    client.incrementWid();
                    client.setPairSign(pairSign);
                    wid++;

                    saveHandler.saveState();
                }

            } else {
                message = "Incorrect signature, repeated event or incorrect transaction id.";
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            message = "Something wrong with the keys!";
        }

        String finalString1 = keyPair.getPublic().toString() + message + wid;
        byte [] signature1 = crypto.getSignature(finalString1, keyPair.getPrivate());

        ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setWid(wid)
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
                Client me = clientList.get(mypublicKey);

                if (!me.getEventList().contains(nonce) && rid > me.getRid()) {

                    me.addEvent(nonce);

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

                    me.incrementRid();

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