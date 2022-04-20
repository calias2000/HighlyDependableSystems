package pt.tecnico.bank;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.grpc.*;

import java.security.*;
import java.security.spec.InvalidKeySpecException;

import static pt.tecnico.bank.ServerMain.*;

public class ADEBServiceImpl extends ADEBServiceGrpc.ADEBServiceImplBase {

    private ADEBInstanceManager adebInstanceManager;
    private ADEB adeb;

    public ADEBServiceImpl(ADEB adeb, ADEBInstanceManager adebInstanceManager){
        this.adeb = adeb;
        this.adebInstanceManager = adebInstanceManager;
    }

    public synchronized void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {

        try {
            PublicKey otherServerPubKey = crypto.getPubKeyGrpc(request.getServerPubkey().toByteArray());
            String finalString = request.getInput() + request.getNonce() + request.getServerName();

            if (crypto.verifySignature(finalString, otherServerPubKey, request.getSignature().toByteArray())){
                ADEBInstance instance = adebInstanceManager.getInstance(finalString);
                synchronized (instance) {
                    instance.addEcho();
                    System.out.println("Received echo.");
                    if (instance.getEchos() >= adeb.getQuorum()){
                        if (!instance.hasSentReady()){
                            instance.setHasSentReady();
                            adeb.ready(finalString);
                        }
                    }
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
                ADEBInstance instance = adebInstanceManager.getInstance(finalString);
                synchronized (instance) {
                    instance.addReady();
                    System.out.println("Received ready.");
                    if (instance.getReadys() >= adeb.getQuorum()){
                        if (!instance.hasSentReady()){
                            instance.setHasSentReady();
                            adeb.ready(finalString);
                        }
                        if (!instance.hasDelivered()){
                            adebInstanceManager.deliver(finalString);
                        }
                    }
                }
            }

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Something wrong with the keys.");
        }
    }
}
