package pt.tecnico.bank;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.bank.grpc.*;

import java.io.Closeable;

public class ServerFrontend implements Closeable {

    private final ManagedChannel channel;
    private final ServerServiceGrpc.ServerServiceBlockingStub stub;

    public ServerFrontend() {
        this.channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
        this.stub = ServerServiceGrpc.newBlockingStub(this.channel);
    }

    /* ---------- Services ---------- */

    public PingResponse ping(PingRequest request) { return stub.ping(request); }

    public LastIdResponse lastId(LastIdRequest request) {
        return stub.lastId(request);
    }

    public OpenAccountResponse openAccount(OpenAccountRequest request) {
        return stub.openAccount(request);
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {
        return stub.checkAccount(request);
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {
        return stub.sendAmount(request);
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {
        return stub.receiveAmount(request);
    }

    public AuditResponse audit(AuditRequest request) {
        return stub.audit(request);
    }

    @Override
    public final void close() {
        channel.shutdown();
    }
}