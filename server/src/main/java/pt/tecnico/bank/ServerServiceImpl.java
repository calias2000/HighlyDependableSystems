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
        String pubkey = request.getPublicKey();


        clientList.put(pubkey,new Client());

        OpenAccountResponse response = OpenAccountResponse.newBuilder().setAck(true).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        String keySender = request.getSenderKey();
        String keyReceiver = request.getReceiverKey();
        int ammount = request.getAmount();

        Client clientSender = clientList.get(keySender);
        Client clientReceiver = clientList.get(keyReceiver);

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
            clientReceiver.addPending(new Transactions(keySender, ammount));
            SendAmountResponse response = SendAmountResponse.newBuilder().setAck(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        String pubkey = request.getPublicKey();
        List<String> pending = new ArrayList<>();

        Client client = clientList.get(pubkey);

        if (client == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Account does not exist.").asRuntimeException());
        } else {

            for (int i = 0; i < client.getPending().size(); i++){
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

        Client client = clientList.get(pubkey);

        if (client == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Account does not exist.").asRuntimeException());
        } else {
            Transactions transaction = client.getPending().get(transfer);
            client.setBalance(client.getBalance() + transaction.getValue());

            Client client2 = clientList.get(transaction.getPubkey());
            client2.setBalance(client2.getBalance() - transaction.getValue());

            client.removePending(transfer);
            client.addHistory(new Transactions(transaction.getPubkey(), transaction.getValue()));
            client2.addHistory(new Transactions(pubkey, -transaction.getValue()));


            ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder().setAck(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        String pubkey = request.getPublicKey();
        List<String> history = new ArrayList<>();

        Client client = clientList.get(pubkey);

        if (client == null){
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Account does not exist.").asRuntimeException());
        } else {

            for (int i = 0; i < client.getHistory().size(); i++){
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