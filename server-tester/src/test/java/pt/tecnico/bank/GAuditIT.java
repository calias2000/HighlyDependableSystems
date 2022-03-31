package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.grpc.AuditRequest;
import pt.tecnico.bank.grpc.CheckAccountRequest;

import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GAuditIT {

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
    public void AuditTest() {
        String username = "diogo";
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(username);
        AuditRequest request = AuditRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).build();
        // Default account balance
        assertEquals("50 to goncalo", frontend.audit(request).getTransferHistory(0));

        String username2 = "goncalo";
        PublicKey publicKey2 = Auxiliar.getPubKeyfromCert(username2);
        AuditRequest request2 = AuditRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey2.getEncoded())).build();
        // Default account balance
        assertEquals("50 from diogo", frontend.audit(request2).getTransferHistory(0));
    }
}
