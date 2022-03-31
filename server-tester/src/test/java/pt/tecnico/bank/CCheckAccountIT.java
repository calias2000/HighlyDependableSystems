package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.grpc.CheckAccountRequest;
import pt.tecnico.bank.grpc.OpenAccountRequest;
import pt.tecnico.bank.grpc.ReceiveAmountRequest;
import pt.tecnico.bank.grpc.SendAmountRequest;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class CCheckAccountIT {

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
    public void CheckAccountNotExistTest() {
        String username = "francisco";
        assertNull(Auxiliar.getPubKeyfromCert(username));
    }

    @Test
    public void CheckAccountTest() {
        String username = "diogo";
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(username);
        CheckAccountRequest request = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).build();
        // Default account balance
        assertEquals(500, frontend.checkAccount(request).getBalance());
        // No Pending Transactions yet
        assertEquals(0, frontend.checkAccount(request).getPendentTransfersCount());
    }
}
