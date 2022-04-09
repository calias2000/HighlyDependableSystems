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

import static org.junit.jupiter.api.Assertions.*;

public class DSendAmountIT {

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
    public void SendAmountTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String senderUsername = "diogo";
        String senderPassword = "password1";
        String receiverUsername = "goncalo";
        int amount = 50;
        KeyPair keyPair = Auxiliar.getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        String finalString = keyPair.getPublic().toString() + amount + random + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        SendAmountResponse response = frontend.sendAmount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString1 = serverPubKey.toString() + response.getAck() + response.getNonce();

        assertTrue(Auxiliar.verifySignature(finalString1, serverPubKey, response.getSignature().toByteArray()));
        assertEquals(random + 1, response.getNonce());
        assertTrue(response.getAck());
    }

    @Test
    public void SendAmountNotEnoughBalanceTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String senderUsername = "diogo";
        String senderPassword = "password1";
        String receiverUsername = "goncalo";
        int amount = 700;
        KeyPair keyPair = Auxiliar.getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        String finalString = keyPair.getPublic().toString() + amount + random + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertThrows(StatusRuntimeException.class, () -> frontend.sendAmount(request), "Sender account does not have enough balance.");
    }

    @Test
    public void SendAmountReplayAttackTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String senderUsername = "diogo";
        String senderPassword = "password1";
        String receiverUsername = "goncalo";
        int amount = 50;
        KeyPair keyPair = Auxiliar.getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        String finalString = keyPair.getPublic().toString() + amount + random + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        frontend.sendAmount(request);

        assertThrows(StatusRuntimeException.class, () -> frontend.sendAmount(request), "Incorrect signature, repeated event or incorrect transaction id.");
    }

    @Test
    public void SendAmountManInTheMiddleTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String senderUsername = "diogo";
        String senderPassword = "password1";
        String receiverUsername = "goncalo";
        int amount = 50;
        KeyPair keyPair = Auxiliar.getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = Auxiliar.getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        String finalString = keyPair.getPublic().toString() + amount + random + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        String manInTheMiddleUsername = "bernardo";
        PublicKey publicKeyMIM = Auxiliar.getPubKeyfromCert(manInTheMiddleUsername);

        //Bernardo intercepts the request and changes receiver public key
        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKeyMIM.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertThrows(StatusRuntimeException.class, () -> frontend.sendAmount(request), "Incorrect signature, repeated event or incorrect transaction id.");
    }
}
