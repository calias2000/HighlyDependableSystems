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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class App {

    ServerFrontend frontend;
    Crypto crypto;
    int balance;
    int wid;
    int rid;

    public App(ServerFrontend frontend, Crypto crypto) {
        this.frontend = frontend;
        this.crypto = crypto;
        this.balance = 500;
        this.wid = 0;
        this.rid = 0;
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

        String pairSignatureString = String.valueOf(wid) + balance;
        byte [] pairSignature = crypto.getSignature(pairSignatureString, privateKey);

        String finalString1 = publicKey.toString() + username + this.wid + this.balance + Arrays.toString(pairSignature);
        byte[] signature = crypto.getSignature(finalString1, privateKey);

        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username)
                .setWid(this.wid)
                .setBalance(this.balance)
                .setPairSign(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(signature))
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

        int nonce = crypto.getSecureRandom();

        CheckAccountRequest request = CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setRid(this.rid)
                .setNonce(nonce)
                .build();

        CheckAccountResponse response = frontend.checkAccount(request);

        if (response == null) {
            System.out.println("No quorum achieved!");
        }
        else if (response.getMessage().equals("valid")) {

            this.rid++;
            List<Transaction> pending = response.getTransactionsList();

            if (pending.isEmpty()) {
                System.out.println("\nAvailable Balance: " + response.getBalance() + "\n\nNo pending transactions.\n");
            } else {
                System.out.println("\nAvailable Balance: " + response.getBalance() + "\n\nPending Transactions:");
                int i = 1;
                for (Transaction transaction : pending) {
                    System.out.println(i + ") " + transaction.getAmount() + " from " + transaction.getSourceUsername());
                    i++;
                }
                System.out.println();
            }
        } else {
            System.out.println(response.getMessage());
        }
    }

    public void sendAmount(PublicKey senderPubK, PublicKey receiverPubK, int amount, PrivateKey senderPrivK, String sourceUsername, String destUsername){

        String transactionString = sourceUsername + destUsername + amount + senderPubK.toString() + receiverPubK.toString() + this.wid;
        byte [] signatureTrans = crypto.getSignature(transactionString, senderPrivK);
        Transaction transaction = Transaction.newBuilder()
                .setSourceUsername(sourceUsername)
                .setDestUsername(destUsername)
                .setAmount(amount)
                .setSource(ByteString.copyFrom(senderPubK.getEncoded()))
                .setDestination(ByteString.copyFrom(receiverPubK.getEncoded()))
                .setWid(this.wid)
                .setSignature(ByteString.copyFrom(signatureTrans))
                .build();

        String pairSignatureString = String.valueOf(wid) + balance;
        byte [] pairSignature = crypto.getSignature(pairSignatureString, senderPrivK);

        String finalString = sourceUsername + destUsername + amount + senderPubK +  receiverPubK + Arrays.toString(signatureTrans) + this.wid + this.balance + Arrays.toString(pairSignature);
        byte [] signature = crypto.getSignature(finalString, senderPrivK);

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setTransaction(transaction)
                .setBalance(this.balance)
                .setPairSign(ByteString.copyFrom(pairSignature))
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

        byte [] signature = crypto.getSignature(publicKey.toString() + transfer + this.wid, privateKey);

        ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setWid(this.wid)
                .setTransfer(transfer)
                .setSignature(ByteString.copyFrom(signature))
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

        AuditRequest request = AuditRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setNonce(random)
                .setRid(this.rid)
                .build();

        AuditResponse response = frontend.audit(request);

        if (response == null) {
            System.out.println("No quorum achieved!");
        } else if (response.getMessage().equals("valid")) {
            List<Transaction> history = response.getTransactionsList();

            if (history.isEmpty()) {
                System.out.println("\nNo history to be shown.\n");
            } else {

                for (Transaction transaction : history) {
                    if (transaction.getAmount() < 0) {
                        System.out.println(Math.abs(transaction.getAmount()) + " to " + transaction.getDestUsername());
                    } else {
                        System.out.println(Math.abs(transaction.getAmount()) + " from " + transaction.getSourceUsername());
                    }
                }
                System.out.println();

            }
        } else {
            System.out.println(response.getMessage());
        }
    }
}