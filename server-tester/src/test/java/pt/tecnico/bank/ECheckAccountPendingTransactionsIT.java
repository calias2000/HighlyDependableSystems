package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.grpc.CheckAccountRequest;
import pt.tecnico.bank.grpc.OpenAccountRequest;
import pt.tecnico.bank.grpc.SendAmountRequest;

import java.security.*;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class ECheckAccountPendingTransactionsIT {

    private ServerFrontend frontend;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontend();
    }

    @AfterEach
    public void tearDown() {
        frontend.close();
        frontend = null;
    }

    @Test
    public void CheckAccountWithPendingTransactions() {
        String username = "goncalo";
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(username);
        CheckAccountRequest request = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).build();

        // Receiver check account
        assertEquals(500, frontend.checkAccount(request).getBalance());
        assertEquals("50 from diogo", frontend.checkAccount(request).getPendentTransfers(0));
        assertEquals(2, frontend.checkAccount(request).getPendentTransfersCount());

        String username2 = "diogo";
        PublicKey publicKey1 = Auxiliar.getPubKeyfromCert(username2);
        CheckAccountRequest request1 = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey1.getEncoded())).build();

        // Sender check account
        assertEquals(500, frontend.checkAccount(request1).getBalance());
        // No Pending Transactions yet
        assertEquals(0, frontend.checkAccount(request1).getPendentTransfersCount());
    }
}
