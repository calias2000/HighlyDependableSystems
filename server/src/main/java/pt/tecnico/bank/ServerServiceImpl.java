package pt.tecnico.bank;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.grpc.*;

import static io.grpc.Status.INVALID_ARGUMENT;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        String input = request.getInput();

        if (input.isBlank()) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Input cannot be empty!").asRuntimeException());
            return;
        }

        String output = "OK: " + input;
        PingResponse response = PingResponse.newBuilder().setOutput(output).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void openAccount(OpenAccountRequest request, StreamObserver<OpenAccountResponse> responseObserver) {
        // TODO
    }

    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        // TODO
    }

    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        // TODO
    }

    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
        // TODO
    }

    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        // TODO
    }

}