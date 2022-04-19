package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.bank.grpc.ADEBServiceGrpc;
import pt.tecnico.bank.grpc.EchoRequest;
import pt.tecnico.bank.grpc.ReadyRequest;
import pt.tecnico.bank.grpc.ServerServiceGrpc;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static pt.tecnico.bank.ServerMain.*;

public class ADEB implements AutoCloseable {

    int byzantine;
    private final List<ManagedChannel> channels;
    private final List<ADEBServiceGrpc.ADEBServiceStub> stubs;
    private int nServers;
    private String serverName;
    int echos = 0;
    int ready = 0;
    boolean echoFlag = false;
    boolean readyFlag = false;
    boolean delivered = false;
    private String input;
    int quorum;
    CountDownLatch deliveredLatch = null;

    public ADEB(int byzantine, String serverName) {
        this.byzantine = byzantine;
        this.channels = new ArrayList<>();
        this.stubs = new ArrayList<>();
        this.nServers = 3*byzantine + 1;
        this.serverName = serverName;
        this.quorum = 2 * byzantine + 1;

        for (int i = 0; i < nServers; i++){
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080 + i).usePlaintext().build();
            channels.add(channel);
            stubs.add(ADEBServiceGrpc.newStub(channel));
        }
    }

    public void echo(String input) {

        if (!echoFlag) {

            System.out.println("\nSTARTING ADEB" +
                    "\nECHOS " + echos +
                    "\nREADY " + ready);

            echoFlag = true;

            this.input = input;

            int nonce = crypto.getSecureRandom();

            String finalString = input + nonce + serverName;

            EchoRequest request = EchoRequest.newBuilder()
                    .setSignature(ByteString.copyFrom(crypto.getSignature(finalString, keyPair.getPrivate())))
                    .setServerPubkey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                    .setNonce(nonce)
                    .setServerName(serverName)
                    .setInput(input)
                    .build();

            for (ADEBServiceGrpc.ADEBServiceStub stub : this.stubs) {
                stub.withDeadlineAfter(3, TimeUnit.SECONDS).echo(request, new ObserverADEB<>());
            }
        }
    }

    public void ready() {

        int nonce = crypto.getSecureRandom();

        String finalString = input + nonce + serverName;

        ReadyRequest request = ReadyRequest.newBuilder()
                .setSignature(ByteString.copyFrom(crypto.getSignature(finalString, keyPair.getPrivate())))
                .setServerPubkey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setNonce(nonce)
                .setServerName(serverName)
                .setInput(input)
                .build();

        for (ADEBServiceGrpc.ADEBServiceStub stub : this.stubs) {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS).ready(request, new ObserverADEB<>());
        }
    }

    @Override
    public final void close() {
        this.channels.forEach(ManagedChannel::shutdown);
    }

    public void reset() {
        this.echos = 0;
        this.ready = 0;
        this.echoFlag = false;
        this.readyFlag = false;
        this.delivered = false;
        this.deliveredLatch = null;
    }
}
