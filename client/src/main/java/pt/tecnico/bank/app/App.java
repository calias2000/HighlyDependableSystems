package pt.tecnico.bank.app;

import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.ServerFrontend;
import pt.tecnico.bank.grpc.*;

public class App {

    ServerFrontend frontend;

    public App(ServerFrontend frontend) {
        this.frontend = frontend;
    }

    // App methods that send requests to the HubServiceImpl and returns responses to the user
    // Before each server request the method "checkServerConnection" is run to connect to an available hub


    /**
     * Prints to the console the result of a PingRequest.
     */

    public void ping() {
        PingRequest request = PingRequest.newBuilder().setInput("SEC").build();
        System.out.println(frontend.ping(request).getOutput());
    }
}
