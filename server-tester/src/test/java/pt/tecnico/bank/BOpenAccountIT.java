package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.*;
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

public class BOpenAccountIT {

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
    public void OpenAccountTest() throws InterruptedException {

        /* CREATE ACCOUNT
            - username: "diogo"
            - password: "password1"
        */
        String username = "diogo";
        String password = "password1";
        Auxiliar.generateStoreandCer(username, password);
        PublicKey publicKey = Auxiliar.getKeyPair(username, password).getPublic();
        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username).build();

        OpenAccountResponse response = frontend.openAccount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + response.getAck();

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        assertTrue(response.getAck());
        Thread.sleep(1500);
        // CHECK LOGIN
        assertTrue(Auxiliar.checkCredentials(username, password));
        // WRONG CREDENTIALS
        assertFalse(Auxiliar.checkCredentials(username, "password2"));
        // ACCOUNT ALREADY EXISTS
        assertTrue(Auxiliar.existsAccount(username));
    }

    @Test
    public void OpenSecondAccount() throws InterruptedException {
        /* CREATE ACCOUNT
            - username: "goncalo"
            - password: "password1"
        */
        String username = "goncalo";
        String password = "password1";
        Auxiliar.generateStoreandCer(username, password);
        PublicKey publicKey = Auxiliar.getKeyPair(username, password).getPublic();
        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username).build();

        OpenAccountResponse response = frontend.openAccount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + response.getAck();

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        assertTrue(response.getAck());
        Thread.sleep(1500);
        // CHECK LOGIN
        assertTrue(Auxiliar.checkCredentials(username, password));
        // WRONG CREDENTIALS
        assertFalse(Auxiliar.checkCredentials(username, "password2"));
        // ACCOUNT ALREADY EXISTS
        assertTrue(Auxiliar.existsAccount(username));

    }

    @Test
    public void OpenThirdAccount() throws InterruptedException {
        /* CREATE ACCOUNT
            - username: "bernardo"
            - password: "password1"
        */
        String username = "bernardo";
        String password = "password1";
        Auxiliar.generateStoreandCer(username, password);
        PublicKey publicKey = Auxiliar.getKeyPair(username, password).getPublic();
        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username).build();

        OpenAccountResponse response = frontend.openAccount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + response.getAck();

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        assertTrue(response.getAck());
        // ACCOUNT SUCCESSFULLY CREATED
        Thread.sleep(1500);
        // CHECK LOGIN
        assertTrue(Auxiliar.checkCredentials(username, password));
        // WRONG CREDENTIALS
        assertFalse(Auxiliar.checkCredentials(username, "password2"));
        // ACCOUNT ALREADY EXISTS
        assertTrue(Auxiliar.existsAccount(username));
    }
}
