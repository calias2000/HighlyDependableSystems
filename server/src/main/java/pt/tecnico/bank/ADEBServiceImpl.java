package pt.tecnico.bank;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.grpc.ADEBServiceGrpc;
import pt.tecnico.bank.grpc.EchoRequest;
import pt.tecnico.bank.grpc.EchoResponse;

import java.security.*;
import java.security.spec.InvalidKeySpecException;

import static pt.tecnico.bank.ServerMain.*;

public class ADEBServiceImpl extends ADEBServiceGrpc.ADEBServiceImplBase {

    public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {

        try {
            PublicKey otherServerPubKey = crypto.getPubKeyGrpc(request.getServerPubkey().toByteArray());
            String finalString = request.getInput() + request.getNonce() + request.getServerName();

            if (crypto.verifySignature(finalString, otherServerPubKey, request.getSignature().toByteArray())){
                if (input.equals(request.getInput())){
                    System.out.println("Input from server " + request.getServerName() + " is same as mine!");
                    adeb.echos++;
                } else {
                    System.out.println("Input from server " + request.getServerName() + " is different than mine!");
                }
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys.");
        }
    }
}
