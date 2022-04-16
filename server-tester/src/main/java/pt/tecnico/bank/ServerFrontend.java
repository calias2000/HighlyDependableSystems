package pt.tecnico.bank;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.grpc.*;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ServerFrontend implements AutoCloseable {

    private final List<ManagedChannel> channels;
    private final List<ServerServiceGrpc.ServerServiceStub> stubs;
    private final int quorum;

    public ServerFrontend(int value) {
        this.channels = new ArrayList<>();
        this.stubs = new ArrayList<>();
        int numberChannels = 3 * value + 1;
        this.quorum = 2 * value + 1;

        for (int i = 0; i < numberChannels; i++){
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080 + i).usePlaintext().build();
            channels.add(channel);
            stubs.add(ServerServiceGrpc.newStub(channel));
        }
    }

    /* ---------- Services ---------- */

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

        synchronized (collector.responses) {
            for (Object objResponse : collector.responses) {
                OpenAccountResponse response = (OpenAccountResponse) objResponse;
                try {
                    PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                    String finalString = serverPubKey.toString() + response.getMessage();
                    if (!verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
                        collector.responses.remove(objResponse);
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        return (OpenAccountResponse) collector.responses.get(0);
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

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                CheckAccountResponse response = (CheckAccountResponse) iterator.next();
                try {
                    PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                    String finalString = serverPubKey.toString() + response.getBalance() + response.getPendentTransfersList();

                    if (!verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
                        iterator.remove();
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        return (CheckAccountResponse) collector.responses.get(0);
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

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                SendAmountResponse response = (SendAmountResponse) iterator.next();
                try {
                    PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                    String finalString = serverPubKey.toString() + response.getMessage() + response.getNonce();

                    if (!verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()) || request.getNonce() + 1 != response.getNonce()) {
                        iterator.remove();
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        return (SendAmountResponse) collector.responses.get(0);
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

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                ReceiveAmountResponse response = (ReceiveAmountResponse) iterator.next();
                try {
                    PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                    String finalString = serverPubKey.toString() + response.getMessage() + response.getNonce();

                    if (!verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()) || request.getNonce() + 1 != response.getNonce()) {
                        iterator.remove();
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        return (ReceiveAmountResponse) collector.responses.get(0);
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

        synchronized (collector.responses) {
            while (iterator.hasNext()) {
                AuditResponse response = (AuditResponse) iterator.next();
                try {
                    PublicKey serverPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(response.getPublicKey().toByteArray()));
                    String finalString = serverPubKey.toString() + response.getTransferHistoryList();

                    if (!verifySignature(finalString, serverPubKey, response.getSignature().toByteArray())) {
                        iterator.remove();
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    System.out.println("Something wrong with the algorithm!");
                }
            }
        }

        return (AuditResponse) collector.responses.get(0);
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

    @Override
    public final void close() {
        this.channels.forEach(ManagedChannel::shutdown);
    }
}