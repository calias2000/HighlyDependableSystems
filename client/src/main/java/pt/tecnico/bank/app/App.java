package pt.tecnico.bank.app;

import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.ServerFrontend;
import pt.tecnico.bank.grpc.*;

import java.util.List;

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
    public int lastId() {
        LastIdRequest request = LastIdRequest.newBuilder().build();
        return frontend.lastId(request).getLastId() + 1;
    }


    public void ping() {
        PingRequest request = PingRequest.newBuilder().setInput("Ping").build();
        System.out.println("\n" + frontend.ping(request).getOutput() + "\n");
    }

    public void openAccount(String pubkey) {
        OpenAccountRequest request = OpenAccountRequest.newBuilder().setPublicKey(pubkey).build();
        if (frontend.openAccount(request).getAck()){
            System.out.println("\nAccount created successfuly with ID: " + lastId() + ".\n");
        }
    }

    public void checkAccount(String pubkey){
        try {
            CheckAccountRequest request = CheckAccountRequest.newBuilder().setPublicKey(pubkey).build();
            List<String> pending = frontend.checkAccount(request).getPendentTransfersList();

            if (pending.isEmpty()) {
                System.out.println("\nAvailable Balance: " + frontend.checkAccount(request).getBalance() + "\n\nNo pending transactions.\n");
            } else {
                System.out.println("\nAvailable Balance: " + frontend.checkAccount(request).getBalance() + "\n\nPending Transactions:");
                int i = 1;
                for (String p : pending) {
                    System.out.println(i + ") " + p);
                    i++;
                }
                System.out.println();
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }

    public void audit(String pubkey){
        try {
            AuditRequest request = AuditRequest.newBuilder().setPublicKey(pubkey).build();
            List<String> history = frontend.audit(request).getTransferHistoryList();

            if (history.isEmpty()) {
                System.out.println("\nNo history to be shown.\n");
            } else {
                System.out.println("\nHistory:");

                for (String p : history) {
                    System.out.println(p);
                }

                System.out.println();
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }

    public void sendAmount(String keySender, String keyReceiver, int value){
        try {
            SendAmountRequest request = SendAmountRequest.newBuilder().setSenderKey(keySender).setReceiverKey(keyReceiver).setAmount(value).build();
            if (frontend.sendAmount(request).getAck()) {
                System.out.println("\nPending transaction, waiting for approval.\n");
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }

    public void receiveAmount(String pubkey, int transfer) {
        try {
            ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder().setPublicKey(pubkey).setTransfer(transfer).build();
            if (frontend.receiveAmount(request).getAck()) {
                System.out.println("\nTransaction Accepted.\n");
            }
        } catch (StatusRuntimeException e) {
            System.out.println("WARNING " + e.getStatus().getDescription() + "\n");
        }
    }
}
