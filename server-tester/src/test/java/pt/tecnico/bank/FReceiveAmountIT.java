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
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class FReceiveAmountIT {

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
    public void ReceiveAmountTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String username = "goncalo";
        String password = "password1";
        int transfer = 0;
        KeyPair keyPair = Auxiliar.getKeyPair(username, password);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        String finalString = keyPair.getPublic().toString() + transfer + random;
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setTransfer(transfer)
                .setNonce(random)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        ReceiveAmountResponse response = frontend.receiveAmount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString1 = serverPubKey.toString() + response.getAck() + response.getNonce();

        assertTrue(Auxiliar.verifySignature(finalString1, serverPubKey, response.getSignature().toByteArray()));
        assertEquals(random + 1, response.getNonce());
        assertTrue(response.getAck());

        KeyPair keyPair3 = Auxiliar.getKeyPair("goncalo", "password1");

        String finalString3 = keyPair.getPublic().toString() + keyPair3.getPublic().toString();
        byte[] signature3 = Auxiliar.getSignature(finalString3, keyPair3.getPrivate());
        CheckAccountRequest request1 = CheckAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setSignature(ByteString.copyFrom(signature3))
                .setMyPublicKey(ByteString.copyFrom(keyPair3.getPublic().getEncoded()))
                .build();

        // Receiver check account
        assertEquals(550, frontend.checkAccount(request1).getBalance());

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
        assertEquals(450, frontend.checkAccount(request2).getBalance());
        // No Pending Transactions yet
        assertEquals(0, frontend.checkAccount(request2).getPendentTransfersCount());
    }

    @Test
    public void ReceiveAmountNonExistentTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String username = "goncalo";
        String password = "password1";
        int transfer = 5;
        KeyPair keyPair = Auxiliar.getKeyPair(username, password);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        String finalString = keyPair.getPublic().toString() + transfer + random;
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setTransfer(transfer)
                .setNonce(random)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertThrows(StatusRuntimeException.class, () -> frontend.receiveAmount(request), "Incorrect signature, repeated event or incorrect transaction id.");
    }
}
