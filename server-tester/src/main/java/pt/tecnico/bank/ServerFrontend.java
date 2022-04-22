package pt.tecnico.bank;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.grpc.*;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ServerFrontend implements AutoCloseable {

    private final List<ManagedChannel> channels;
    private final List<ServerServiceGrpc.ServerServiceStub> stubs;
    private final int quorum;
    private final int byzantine;
    private final Crypto crypto;

    public ServerFrontend(int value, Crypto crypto) {
        this.channels = new ArrayList<>();
        this.stubs = new ArrayList<>();
        int numberChannels = 3 * value + 1;
        this.byzantine = value;
        this.quorum = (numberChannels + this.byzantine) / 2 + 1;
        this.crypto = crypto;

        for (int i = 0; i < numberChannels; i++){
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080 + i).usePlaintext().build();
            channels.add(channel);
            stubs.add(ServerServiceGrpc.newStub(channel));
        }
    }

    /* ---------- Services ---------- */

    public RidResponse rid(RidRequest request) {
        RespCollector collector = new RespCollector();

        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).rid(request, new Observer<>(collector, finishLatch));
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }

        Iterator<Object> iterator = collector.responses.iterator();
        int counter = 0;
        int rid = -1;
        RidResponse bestResponse = null;

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                RidResponse response = (RidResponse) iterator.next();
                try {
                    PublicKey serverPubKey = crypto.getPubKeyGrpc(response.getServerPubKey().toByteArray());
                    String finalString = serverPubKey.toString() + response.getRid() + response.getMessage();
                    if (!crypto.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
                        iterator.remove();
                        counter++;
                    } else {
                        if (response.getRid() > rid){
                            bestResponse = response;
                        }
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        if (counter > this.byzantine) {
            return null;
        } else {
            return bestResponse;
        }
    }

    public PingResponse ping(PingRequest request) {
        RespCollector collector = new RespCollector();

        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).ping(request, new Observer<>(collector, finishLatch));
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }

        return (PingResponse) collector.responses.get(0);
    }

    public OpenAccountResponse openAccount(OpenAccountRequest request) {

        RespCollector collector = new RespCollector();

        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).openAccount(request, new Observer<>(collector, finishLatch));
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }

        Iterator<Object> iterator = collector.responses.iterator();
        int counter = 0;

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                OpenAccountResponse response = (OpenAccountResponse) iterator.next();
                try {
                    PublicKey serverPubKey = crypto.getPubKeyGrpc(response.getPublicKey().toByteArray());
                    String finalString = serverPubKey.toString() + response.getMessage();
                    if (!crypto.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
                        iterator.remove();
                        counter++;
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        if (counter > this.byzantine) {
            return null;
        } else {
            return (OpenAccountResponse) collector.responses.get(0);
        }
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {

        RespCollector collector = new RespCollector();

        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            try {
                stub.withDeadlineAfter(3, TimeUnit.SECONDS).checkAccount(request, new Observer<>(collector, finishLatch));
            } catch (StatusRuntimeException e) {
                System.out.println("Stub error");
            }
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }

        Iterator<Object> iterator = collector.responses.iterator();
        int counter = 0;
        int wid = -1;
        CheckAccountResponse bestResponse = null;

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                boolean fakeTransaction = false;
                CheckAccountResponse response = (CheckAccountResponse) iterator.next();
                try {
                    PublicKey serverPubKey = crypto.getPubKeyGrpc(response.getPublicKey().toByteArray());
                    String finalString = serverPubKey.toString() + response.getBalance()
                            + response.getWid() + Arrays.toString(response.getPairSign().toByteArray())
                            + response.getRid() + response.getMessage()
                            + response.getTransactionsList() + response.getNonce();

                    String pairSignString = String.valueOf(response.getBalance()) + response.getWid();

                    PublicKey otherPubK = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());

                    if (!crypto.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())
                            || request.getNonce() + 1 != response.getNonce()
                            || !crypto.verifySignature(pairSignString, otherPubK, response.getPairSign().toByteArray())
                            || request.getRid() != response.getRid()) {

                        iterator.remove();
                        counter++;

                    } else {
                        for (Transaction transaction : response.getTransactionsList()){
                            String transactionString = transaction.getSourceUsername() + transaction.getDestUsername()
                                    + transaction.getAmount() + crypto.getPubKeyGrpc(transaction.getSource().toByteArray())
                                    + crypto.getPubKeyGrpc(transaction.getDestination().toByteArray())
                                    + transaction.getWid();

                            PublicKey transactionPubK = crypto.getPubKeyGrpc(transaction.getSource().toByteArray());
                            if (!crypto.verifySignature(transactionString, transactionPubK, transaction.getSignature().toByteArray())) {
                                fakeTransaction = true;
                                break;
                            }
                        }

                        if (fakeTransaction) {
                            iterator.remove();
                            counter++;

                        } else {
                            if (response.getWid() > wid){
                                wid = response.getWid();
                                bestResponse = response;
                            }
                        }
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        if (counter > this.byzantine) {
            return null;
        } else {
            return bestResponse;
        }
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {

        RespCollector collector = new RespCollector();

        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).sendAmount(request, new Observer<>(collector, finishLatch));
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }

        Iterator<Object> iterator = collector.responses.iterator();
        int counter = 0;
        int wid = -1;
        SendAmountResponse bestResponse = null;

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                SendAmountResponse response = (SendAmountResponse) iterator.next();
                try {
                    PublicKey serverPubKey = crypto.getPubKeyGrpc(response.getPublicKey().toByteArray());
                    String finalString = serverPubKey.toString() + response.getMessage() + response.getWid();

                    if (!crypto.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
                        iterator.remove();
                        counter++;

                    } else {
                        if (response.getWid() > wid){
                            wid = response.getWid();
                            bestResponse = response;
                        }
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        if (counter > this.byzantine) {
            return null;
        } else {
            return bestResponse;
        }
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {

        RespCollector collector = new RespCollector();

        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).receiveAmount(request, new Observer<>(collector, finishLatch));
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }

        Iterator<Object> iterator = collector.responses.iterator();
        int counter = 0;
        int wid = -1;
        ReceiveAmountResponse bestResponse = null;

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                ReceiveAmountResponse response = (ReceiveAmountResponse) iterator.next();
                try {
                    PublicKey serverPubKey = crypto.getPubKeyGrpc(response.getPublicKey().toByteArray());
                    String finalString = serverPubKey.toString() + response.getMessage() + response.getWid();

                    if (!crypto.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
                        iterator.remove();
                        counter++;
                    } else {
                        if (response.getWid() > wid){
                            wid = response.getWid();
                            bestResponse = response;
                        }
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        if (counter > this.byzantine) {
            return null;
        } else {
            return bestResponse;
        }
    }

    public AuditResponse audit(AuditRequest request) {

        RespCollector collector = new RespCollector();

        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).audit(request, new Observer<>(collector, finishLatch));
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }

        Iterator<Object> iterator = collector.responses.iterator();
        int counter = 0;
        AuditResponse bestResponse = null;
        int size = -1;

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                boolean fakeTransaction = false;
                AuditResponse response = (AuditResponse) iterator.next();
                try {
                    PublicKey serverPubKey = crypto.getPubKeyGrpc(response.getPublicKey().toByteArray());
                    String finalString = serverPubKey.toString() + response.getTransactionsList() + response.getNonce() + response.getRid() + response.getMessage();

                    if (!crypto.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()) || request.getNonce() + 1 != response.getNonce()
                            || request.getRid() != response.getRid()) {

                        iterator.remove();
                        counter++;

                    } else {
                        for (Transaction transaction : response.getTransactionsList()){
                            String transactionString = transaction.getSourceUsername() + transaction.getDestUsername()
                                    + transaction.getAmount() + crypto.getPubKeyGrpc(transaction.getSource().toByteArray())
                                    + crypto.getPubKeyGrpc(transaction.getDestination().toByteArray()) + transaction.getWid();

                            PublicKey transactionPubK = crypto.getPubKeyGrpc(request.getPublicKey().toByteArray());
                            if (!crypto.verifySignature(transactionString, transactionPubK, transaction.getSignature().toByteArray())) {
                                fakeTransaction = true;
                                break;
                            }
                        }

                        if (fakeTransaction) {
                            iterator.remove();
                            counter++;

                        } else {
                            if (response.getTransactionsList().size() > size) {
                                size = response.getTransactionsList().size();
                                bestResponse = response;
                            }
                        }
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        if (counter > this.byzantine) {
            return null;
        } else {
            return bestResponse;
        }
    }

    public void checkWriteBack(CheckWriteBackRequest request) {

        RespCollector collector = new RespCollector();
        CountDownLatch finishLatch = new CountDownLatch(quorum);

        for (ServerServiceGrpc.ServerServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).checkWriteBack(request, new Observer<>(collector, finishLatch));
        }

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Error");
        }
    }

    @Override
    public final void close() {
        this.channels.forEach(ManagedChannel::shutdown);
    }
}