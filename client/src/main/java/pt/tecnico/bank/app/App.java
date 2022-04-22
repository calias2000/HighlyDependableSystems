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
    int rid;

    public App(ServerFrontend frontend, Crypto crypto) {
        this.frontend = frontend;
        this.crypto = crypto;
        this.balance = 500;
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

    public void getRid(PublicKey pubKey) {

        RidResponse response = frontend.rid(RidRequest.newBuilder().setPublicKey(ByteString.copyFrom(pubKey.getEncoded())).build());

        if (response == null) {
            System.out.println("No quorum achieved when getting rid value!");
        }
        else if (response.getMessage().equals("valid")) {
            this.rid = response.getRid();
            System.out.println("OBTAINED RID " + this.rid);
        } else {
            System.out.println(response.getMessage());
        }
    }

    public boolean openAccount(PublicKey publicKey, String username, PrivateKey privateKey) {

        String pairSignatureString = String.valueOf(this.balance) + 0;
        byte [] pairSignature = crypto.getSignature(pairSignatureString, privateKey);

        String finalString1 = publicKey.toString() + username + 0 + this.balance + Arrays.toString(pairSignature);
        byte[] signature = crypto.getSignature(finalString1, privateKey);

        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username)
                .setWid(0)
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
                .setRid(this.rid + 1)
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

            try {

                byte[] pairSign = response.getPairSign().toByteArray();
                PublicKey publicKey1 = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());

                String writeBackString = String.valueOf(response.getBalance()) + pending + response.getWid() + Arrays.toString(pairSign)
                        + response.getRid() + publicKey1.toString() + keyPair.getPublic().toString();

                byte[] writeBackSignature = crypto.getSignature(writeBackString, keyPair.getPrivate());

                CheckWriteBackRequest request1 = CheckWriteBackRequest.newBuilder()
                        .addAllTransactions(pending)
                        .setBalance(response.getBalance())
                        .setWid(response.getWid())
                        .setPairSign(ByteString.copyFrom(pairSign))
                        .setRid(response.getRid())
                        .setPublicKey(ByteString.copyFrom(publicKey1.getEncoded()))
                        .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                        .setSignature(ByteString.copyFrom(writeBackSignature))
                        .build();

                frontend.checkWriteBack(request1);

            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                System.out.println("Something wrong with the algorithm!");
            }

        } else {
            System.out.println(response.getMessage());
        }
    }

    public void sendAmount(PublicKey senderPubK, PublicKey receiverPubK, int amount, PrivateKey senderPrivK, String sourceUsername, String destUsername) {

        int nonce = crypto.getSecureRandom();

        CheckAccountResponse response1 = frontend.checkAccount(CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(senderPubK.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(senderPubK.getEncoded()))
                .setRid(this.rid + 1)
                .setNonce(nonce)
                .build());

        if (response1 == null) {
            System.out.println("No quorum achieved!");
        }
        else if (response1.getMessage().equals("valid")) {

            this.rid++;

            int new_wid = response1.getWid() + 1;
            int new_balance = response1.getBalance() - amount;

            String transactionString = sourceUsername + destUsername + amount + senderPubK.toString() + receiverPubK.toString() + new_wid;
            byte[] signatureTrans = crypto.getSignature(transactionString, senderPrivK);
            Transaction transaction = Transaction.newBuilder()
                    .setSourceUsername(sourceUsername)
                    .setDestUsername(destUsername)
                    .setAmount(amount)
                    .setSource(ByteString.copyFrom(senderPubK.getEncoded()))
                    .setDestination(ByteString.copyFrom(receiverPubK.getEncoded()))
                    .setWid(new_wid)
                    .setSignature(ByteString.copyFrom(signatureTrans))
                    .build();

            String pairSignatureString = String.valueOf(new_balance) + new_wid;
            byte[] pairSignature = crypto.getSignature(pairSignatureString, senderPrivK);

            String finalString = sourceUsername + destUsername + amount
                    + senderPubK + receiverPubK + Arrays.toString(signatureTrans)
                    + new_wid + Arrays.toString(pairSignature) + new_balance;

            byte[] signature = crypto.getSignature(finalString, senderPrivK);

            SendAmountRequest request = SendAmountRequest.newBuilder()
                    .setTransaction(transaction)
                    .setNewBalance(new_balance)
                    .setPairSign(ByteString.copyFrom(pairSignature))
                    .setSignature(ByteString.copyFrom(signature))
                    .build();

            SendAmountResponse response = frontend.sendAmount(request);

            if (response == null) {
                System.out.println("No quorum achieved!");
            } else if (response.getMessage().equals("valid")) {
                System.out.println("\nPending transaction, waiting for approval.\n");
            } else {
                System.out.println(response.getMessage());
            }
        } else {
            System.out.println(response1.getMessage());
        }
    }

    public void receiveAmount(PublicKey publicKey, int transfer, PrivateKey privateKey) {

        int nonce = crypto.getSecureRandom();

        CheckAccountResponse response1 = frontend.checkAccount(CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setRid(this.rid + 1)
                .setNonce(nonce)
                .build());

        if (response1 == null) {
            System.out.println("No quorum achieved!");
        }
        else if (response1.getMessage().equals("valid")) {
            try {
                Transaction auxTransaction = response1.getTransactions(transfer);
                int future_balance = response1.getBalance() + auxTransaction.getAmount();

                this.rid++;

                int new_wid = response1.getWid() + 1;
                String pairSignString = String.valueOf(future_balance) + new_wid;
                byte[] pairSign = crypto.getSignature(pairSignString, privateKey);

                try {

                    PublicKey sourceKey = crypto.getPubKeyGrpc(auxTransaction.getSource().toByteArray());
                    PublicKey destKey = crypto.getPubKeyGrpc(auxTransaction.getDestination().toByteArray());
                    String transactionString = auxTransaction.getSourceUsername() + auxTransaction.getDestUsername()
                            + auxTransaction.getAmount() + sourceKey.toString() + destKey.toString() + new_wid;

                    byte[] signatureTrans = crypto.getSignature(transactionString, privateKey);

                    Transaction toAuditTransaction = Transaction.newBuilder()
                            .setSourceUsername(auxTransaction.getSourceUsername())
                            .setDestUsername(auxTransaction.getDestUsername())
                            .setAmount(auxTransaction.getAmount())
                            .setSource(ByteString.copyFrom(sourceKey.getEncoded()))
                            .setDestination(ByteString.copyFrom(destKey.getEncoded()))
                            .setWid(new_wid)
                            .setSignature(ByteString.copyFrom(signatureTrans))
                            .build();

                    byte[] signature = crypto.getSignature(publicKey.toString() + future_balance + new_wid + Arrays.toString(pairSign) + transfer + toAuditTransaction, privateKey);

                    ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                            .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                            .setFutureBalance(future_balance)
                            .setWid(new_wid)
                            .setPairSign(ByteString.copyFrom(pairSign))
                            .setTransfer(transfer)
                            .setToAuditTransaction(toAuditTransaction)
                            .setSignature(ByteString.copyFrom(signature))
                            .build();

                    ReceiveAmountResponse response = frontend.receiveAmount(request);

                    if (response == null) {
                        System.out.println("No quorum achieved!");
                    } else if (response.getMessage().equals("valid")) {
                        System.out.println("\nTransaction Accepted.\n");
                    } else {
                        System.out.println("\n" + response.getMessage());
                    }
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Invalid transaction id.");
                }
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                System.out.println("Something went wrong while getting keys...");
            }
        } else {
            System.out.println(response1.getMessage());
        }
    }

    public void audit(PublicKey publicKey, KeyPair keyPair, String username){

        int random = crypto.getSecureRandom();

        AuditRequest request = AuditRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setNonce(random)
                .setRid(this.rid + 1)
                .build();

        AuditResponse response = frontend.audit(request);

        if (response == null) {
            System.out.println("No quorum achieved!");
        } else if (response.getMessage().equals("valid")) {
            this.rid++;
            List<Transaction> history = response.getTransactionsList();

            try {

                PublicKey publicKey1 = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());

                String auditBackString = String.valueOf(response.getRid()) + history + publicKey1.toString() + keyPair.getPublic().toString();

                byte[] writeBackSignature = crypto.getSignature(auditBackString, keyPair.getPrivate());

                AuditWriteBackRequest request1 = AuditWriteBackRequest.newBuilder()
                        .addAllTransactions(history)
                        .setRid(response.getRid())
                        .setPublicKey(ByteString.copyFrom(publicKey1.getEncoded()))
                        .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                        .setSignature(ByteString.copyFrom(writeBackSignature))
                        .build();

                frontend.auditWriteBack(request1);

            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                System.out.println("Something wrong with the algorithm!");
            }

            if (history.isEmpty()) {
                System.out.println("\nNo history to be shown.\n");
            } else {

                for (Transaction transaction : history) {
                    if (transaction.getSourceUsername().equals(username)) {
                        System.out.println(transaction.getAmount() + " to " + transaction.getDestUsername());
                    } else {
                        System.out.println(transaction.getAmount() + " from " + transaction.getSourceUsername());
                    }
                }
                System.out.println();
            }
        } else {
            System.out.println(response.getMessage());
        }
    }
}