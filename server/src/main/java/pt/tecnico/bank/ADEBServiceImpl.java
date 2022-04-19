package pt.tecnico.bank;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.grpc.*;

import java.security.*;
import java.security.spec.InvalidKeySpecException;

import static pt.tecnico.bank.ServerMain.*;

public class ADEBServiceImpl extends ADEBServiceGrpc.ADEBServiceImplBase {

    public synchronized void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {

        try {
            PublicKey otherServerPubKey = crypto.getPubKeyGrpc(request.getServerPubkey().toByteArray());
            String finalString = request.getInput() + request.getNonce() + request.getServerName();

            if (crypto.verifySignature(finalString, otherServerPubKey, request.getSignature().toByteArray())){
                if (input.equals(request.getInput())){
                    System.out.println("ECHO Input from server " + request.getServerName() + " is same as mine!");
                    adeb.echos++;
                } else {
                    System.out.println("ECHO Input from server " + request.getServerName() + " is different than mine!");
                }
                if (adeb.echos >= adeb.quorum && !adeb.readyFlag) {
                    adeb.readyFlag = true;
                    adeb.ready();
                }
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys.");
        }
    }

    public synchronized void ready(ReadyRequest request, StreamObserver<ReadyResponse> responseObserver) {

        try {
            PublicKey otherServerPubKey = crypto.getPubKeyGrpc(request.getServerPubkey().toByteArray());
            String finalString = request.getInput() + request.getNonce() + request.getServerName();

            if (crypto.verifySignature(finalString, otherServerPubKey, request.getSignature().toByteArray())){
                if (input.equals(request.getInput())){
                    System.out.println("READY Input from server " + request.getServerName() + " is same as mine!");
                    adeb.ready++;
                } else {
                    System.out.println("READY Input from server " + request.getServerName() + " is different than mine!");
                }
                if (adeb.ready > adeb.byzantine && !adeb.readyFlag) {
                    adeb.readyFlag = true;
                    adeb.ready();
                } else if (adeb.ready > 2 * adeb.byzantine && !adeb.delivered) {
                    adeb.delivered = true;
                    adeb.deliveredLatch.countDown();
                }
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys.");
        }
    }
}
