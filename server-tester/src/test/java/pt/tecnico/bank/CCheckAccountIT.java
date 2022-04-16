package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.grpc.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CCheckAccountIT {

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
    public void CheckAccountNotExistTest() {
        String username = "francisco";
        assertNull(Auxiliar.getPubKeyfromCert(username));
    }

    @Test
    public void CheckAccountTest() {
        String username = "diogo";
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(username);

        KeyPair keyPair = Auxiliar.getKeyPair("goncalo", "password1");

        String finalString1 = publicKey.toString() + keyPair.getPublic().toString();
        byte[] signature1 = Auxiliar.getSignature(finalString1, keyPair.getPrivate());
        CheckAccountRequest request = CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setSignature(ByteString.copyFrom(signature1))
                .setMyPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .build();


        CheckAccountResponse response = frontend.checkAccount(request);
        List<String> pending = response.getPendentTransfersList();

        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + response.getBalance() + response.getPendentTransfersList();

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        // Default account balance
        assertEquals(500, response.getBalance());
        // No Pending Transactions yet
        assertEquals(0, pending.size());
    }
}
