package pt.tecnico.bank;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.grpc.*;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

public class ServerFrontend implements Closeable {

    private final ManagedChannel channel;
    private final ServerServiceGrpc.ServerServiceBlockingStub stub;

    public ServerFrontend() {
        this.channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
        this.stub = ServerServiceGrpc.newBlockingStub(this.channel);
    }

    /* ---------- Services ---------- */

    public PingResponse ping(PingRequest request) {
        PingResponse response = null;
        while (response == null) {
            response = stub.withDeadlineAfter(3, TimeUnit.SECONDS).ping(request);
        }
        return response;
    }

    public OpenAccountResponse openAccount(OpenAccountRequest request) {
        OpenAccountResponse response = null;
        while (response == null) {
            response = stub.withDeadlineAfter(3, TimeUnit.SECONDS).openAccount(request);
        }
        return response;
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {
        CheckAccountResponse response = null;
        while (response == null) {
            response = stub.withDeadlineAfter(3, TimeUnit.SECONDS).checkAccount(request);
        }
        return response;
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {
        SendAmountResponse response = null;
        while (response == null) {
            response = stub.withDeadlineAfter(3, TimeUnit.SECONDS).sendAmount(request);
        }
        return response;
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {
        ReceiveAmountResponse response = null;
        while (response == null) {
            response = stub.withDeadlineAfter(3, TimeUnit.SECONDS).receiveAmount(request);
        }
        return response;
    }

    public AuditResponse audit(AuditRequest request) {
        AuditResponse response = null;
        while (response == null) {
            response = stub.withDeadlineAfter(3, TimeUnit.SECONDS).audit(request);
        }
        return response;
    }

    @Override
    public final void close() {
        channel.shutdown();
    }
}