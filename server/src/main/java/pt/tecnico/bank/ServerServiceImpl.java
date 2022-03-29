package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Event;
import pt.tecnico.bank.domain.Transactions;
import pt.tecnico.bank.grpc.*;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static io.grpc.Status.INVALID_ARGUMENT;
import static pt.tecnico.bank.ServerMain.clientList;
import static pt.tecnico.bank.ServerMain.eventList;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    public void lastId(LastIdRequest request, StreamObserver<LastIdResponse> responseObserver){

        LastIdResponse response = LastIdResponse.newBuilder().setLastId(clientList.size()).build();
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
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            String username = request.getUsername();
            clientList.put(publicKey, new Client(username));

            OpenAccountResponse response = OpenAccountResponse.newBuilder().setAck(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys!");
        }
    }

    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        try {

            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            List<String> pending = new ArrayList<>();

            Client client = clientList.get(publicKey);

            for (int i = 0; i < client.getPending().size(); i++) {
                pending.add(client.getPending().get(i).getValue() + " from " + client.getPending().get(i).getUsername());
            }

            CheckAccountResponse response = CheckAccountResponse.newBuilder().setBalance(client.getBalance()).addAllPendentTransfers(pending).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys!");
        }
    }

    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        try {
            PublicKey keySender = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getSenderKey().toByteArray()));
            PublicKey keyReceiver = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getReceiverKey().toByteArray()));
            int ammount = request.getAmount();

            Client clientSender = clientList.get(keySender);
            Client clientReceiver = clientList.get(keyReceiver);

            if (clientSender.getBalance() < ammount) {
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Sender account does not have enough balance.").asRuntimeException());
            } else if (0 >= ammount) {
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid ammount, must be > 0.").asRuntimeException());
            } else {
                clientReceiver.addPending(new Transactions(clientSender.getUsername(), ammount, keySender));
                SendAmountResponse response = SendAmountResponse.newBuilder().setAck(true).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys!");
        }
    }

    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            int transfer = request.getTransfer();
            int nonce = request.getNonce();
            long timestamp = request.getTimestamp();
            Signature dsaForVerify = Signature.getInstance("SHA256withRSA");
            dsaForVerify.initVerify(publicKey);
            dsaForVerify.update(String.valueOf(transfer).getBytes());
            boolean verifies = dsaForVerify.verify(request.getSignature().toByteArray());
            boolean repeatedEvent = false;

            for (Event event : eventList){
                if (event.getNonce() == nonce && event.getTimestamp() == timestamp){
                    repeatedEvent = true;
                    break;
                }
            }

            Client client = clientList.get(publicKey);

            if (verifies && !repeatedEvent && transfer + 1 <= client.getPending().size()) {

                eventList.add(new Event(nonce, timestamp));

                Transactions transaction = client.getPending().get(transfer);
                client.setBalance(client.getBalance() + transaction.getValue());

                Client client2 = clientList.get(transaction.getPublicKey());
                client2.setBalance(client2.getBalance() - transaction.getValue());

                client.removePending(transfer);
                client.addHistory(new Transactions(transaction.getUsername(), transaction.getValue(), transaction.getPublicKey()));
                client2.addHistory(new Transactions(client.getUsername(), -transaction.getValue(), publicKey));


                ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder().setAck(true).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

            } else {
                responseObserver.onError(INVALID_ARGUMENT.withDescription("Incorrect signature, repeated event or incorrect transaction id.").asRuntimeException());
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | SignatureException e) {
            System.out.println("Something wrong with the keys!");
        }
    }

    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {

        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            List<String> history = new ArrayList<>();

            Client client = clientList.get(publicKey);

            for (int i = 0; i < client.getHistory().size(); i++) {
                if (client.getHistory().get(i).getValue() < 0) {
                    history.add(Math.abs(client.getHistory().get(i).getValue()) + " to " + client.getHistory().get(i).getUsername());
                } else {
                    history.add(Math.abs(client.getHistory().get(i).getValue()) + " from " + client.getHistory().get(i).getUsername());
                }
            }

            AuditResponse response = AuditResponse.newBuilder().addAllTransferHistory(history).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys!");
        }
    }
}