package pt.tecnico.bank;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Transactions;
import pt.tecnico.bank.grpc.*;

import java.util.ArrayList;
import java.util.List;

import static io.grpc.Status.INVALID_ARGUMENT;
import static pt.tecnico.bank.ServerMain.clientList;


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
        String pubkey = request.getPublicKey();
        int balance = request.getBalance();

        // Auxiliary list to delimiter transaction
        List<Transactions> auxPending = new ArrayList<>();
        auxPending.add(new Transactions("initialize", -1));
        List<Transactions> auxHistory = new ArrayList<>();
        auxHistory.add(new Transactions("initialize", -1));

        clientList.add(new Client(pubkey, balance, auxPending, auxHistory));

        OpenAccountResponse response = OpenAccountResponse.newBuilder().setAck(true).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        String keySender = request.getSenderKey();
        String keyReceiver = request.getReceiverKey();
        int ammount = request.getAmount();

        Client clientSender = clientList.stream().filter(p -> p.getPubKey().equals(keySender)).findFirst().orElse(null);
        Client clientReceiver = clientList.stream().filter(p -> p.getPubKey().equals(keyReceiver)).findFirst().orElse(null);

        if (clientSender == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Sender account does not exist.").asRuntimeException());
        }
        else if (clientReceiver == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Receiver account does not exist.").asRuntimeException());
        }
        else if (clientSender.getBalance() < ammount) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Sender account does not have enough balance.").asRuntimeException());
        }
        else if (0 >= ammount){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid ammount, must be > 0.").asRuntimeException());
        } else{
            clientReceiver.getPending().add(new Transactions(keySender, ammount));
            SendAmountResponse response = SendAmountResponse.newBuilder().setAck(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        String pubkey = request.getPublicKey();
        List<String> pending = new ArrayList<>();

        Client client = clientList.stream().filter(p -> p.getPubKey().equals(pubkey)).findFirst().orElse(null);

        if (client == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Account does not exist.").asRuntimeException());
        } else {

            for (int i = 1; i < client.getPending().size(); i++){
                pending.add(client.getPending().get(i).getValue() + " from " + client.getPending().get(i).getPubkey());
            }

            CheckAccountResponse response = CheckAccountResponse.newBuilder().setBalance(client.getBalance()).addAllPendentTransfers(pending).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
        String pubkey = request.getPublicKey();
        int transfer = request.getTransfer();

        Client client = clientList.stream().filter(p -> p.getPubKey().equals(pubkey)).findFirst().orElse(null);

        if (client == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Account does not exist.").asRuntimeException());
        } else {
            Transactions transaction = client.getPending().get(transfer);
            client.setBalance(client.getBalance() + transaction.getValue());

            Client client2 = clientList.stream().filter(p -> p.getPubKey().equals(transaction.getPubkey())).findFirst().orElse(null);
            client2.setBalance(client2.getBalance() - transaction.getValue());

            client.removePending(transfer);
            client.addHistory(new Transactions(transaction.getPubkey(), transaction.getValue()));
            client2.addHistory(new Transactions(client.getPubKey(), -transaction.getValue()));

            ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder().setAck(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        String pubkey = request.getPublicKey();
        List<String> history = new ArrayList<>();

        Client client = clientList.stream().filter(p -> p.getPubKey().equals(pubkey)).findFirst().orElse(null);

        if (client == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Account does not exist.").asRuntimeException());
        } else {

            for (int i = 1; i < client.getHistory().size(); i++){
                if (client.getHistory().get(i).getValue() < 0) {
                    history.add(Math.abs(client.getHistory().get(i).getValue())+ " to " + client.getHistory().get(i).getPubkey());
                } else {
                    history.add(Math.abs(client.getHistory().get(i).getValue()) + " from " + client.getHistory().get(i).getPubkey());
                }
            }

            AuditResponse response = AuditResponse.newBuilder().addAllTransferHistory(history).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}