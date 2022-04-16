package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.grpc.AuditRequest;
import pt.tecnico.bank.grpc.AuditResponse;
import pt.tecnico.bank.grpc.CheckAccountRequest;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GAuditIT {

    private ServerFrontend frontend;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontend(0);
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
        KeyPair keyPair = Auxiliar.getKeyPair("goncalo", "password1");

        String finalString1 = publicKey.toString() + keyPair.getPublic().toString();
        byte[] signature1 = Auxiliar.getSignature(finalString1, keyPair.getPrivate());

        AuditRequest request = AuditRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setSignature(ByteString.copyFrom(signature1))
                .build();

        AuditResponse response = frontend.audit(request);
        List<String> history = response.getTransferHistoryList();

        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + history;

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        // Default account balance
        assertEquals("50 to goncalo", history.get(0));

        String username2 = "goncalo";
        PublicKey publicKey2 = Auxiliar.getPubKeyfromCert(username2);
        KeyPair keyPair2 = Auxiliar.getKeyPair("goncalo", "password1");

        String finalString2 = publicKey2.toString() + keyPair2.getPublic().toString();
        byte[] signature2 = Auxiliar.getSignature(finalString2, keyPair2.getPrivate());

        AuditRequest request2 = AuditRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey2.getEncoded()))
                .setMyPublicKey(ByteString.copyFrom(keyPair2.getPublic().getEncoded()))
                .setSignature(ByteString.copyFrom(signature2))
                .build();

        assertEquals("50 from diogo", frontend.audit(request2).getTransferHistory(0));
    }
}
