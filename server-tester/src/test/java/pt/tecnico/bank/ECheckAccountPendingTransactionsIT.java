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
        KeyPair keyPair = Auxiliar.getKeyPair("diogo", "password1");

        String finalString1 = publicKey.toString() + keyPair.getPublic().toString();
        byte[] signature1 = Auxiliar.getSignature(finalString1, keyPair.getPrivate());
        CheckAccountRequest request = CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setSignature(ByteString.copyFrom(signature1))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .build();

        // Receiver check account
        assertEquals(500, frontend.checkAccount(request).getBalance());
        assertEquals("50 from diogo", frontend.checkAccount(request).getPendentTransfers(0));
        assertEquals(2, frontend.checkAccount(request).getPendentTransfersCount());

        String username2 = "diogo";
        PublicKey publicKey1 = Auxiliar.getPubKeyfromCert(username2);
        KeyPair keyPair1 = Auxiliar.getKeyPair("goncalo", "password1");

        String finalString2 = publicKey1.toString() + keyPair1.getPublic().toString();
        byte[] signature2 = Auxiliar.getSignature(finalString2, keyPair1.getPrivate());
        CheckAccountRequest request2 = CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey1.getEncoded()))
                .setSignature(ByteString.copyFrom(signature2))
                .setMyPublicKey(ByteString.copyFrom(keyPair1.getPublic().getEncoded()))
                .build();

        // Sender check account
        assertEquals(500, frontend.checkAccount(request2).getBalance());
        // No Pending Transactions yet
        assertEquals(0, frontend.checkAccount(request2).getPendentTransfersCount());
    }
}
