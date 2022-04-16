package pt.tecnico.bank;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.*;
import pt.tecnico.bank.grpc.*;

import java.security.*;

import static org.junit.jupiter.api.Assertions.*;

public class BOpenAccountIT {

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
    public void OpenAccountTest() throws InterruptedException {

        /* CREATE ACCOUNT
            - username: "diogo"
            - password: "password1"
        */
        String username = "diogo";
        String password = "password1";
        Auxiliar.generateStoreandCer(username, password);
        KeyPair keyPair = Auxiliar.getKeyPair(username, password);

        String finalString1 = keyPair.getPublic().toString() + username;
        byte[] signature = Auxiliar.getSignature(finalString1, keyPair.getPrivate());

        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setUsername(username)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        OpenAccountResponse response = frontend.openAccount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + response.getMessage();

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        assertEquals(response.getMessage(), "valid");
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
        KeyPair keyPair = Auxiliar.getKeyPair(username, password);

        String finalString1 = keyPair.getPublic().toString() + username;
        byte[] signature = Auxiliar.getSignature(finalString1, keyPair.getPrivate());

        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setUsername(username)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        OpenAccountResponse response = frontend.openAccount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + response.getMessage();

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        assertEquals(response.getMessage(), "valid");
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
        KeyPair keyPair = Auxiliar.getKeyPair(username, password);

        String finalString1 = keyPair.getPublic().toString() + username;
        byte[] signature = Auxiliar.getSignature(finalString1, keyPair.getPrivate());

        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setUsername(username)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        OpenAccountResponse response = frontend.openAccount(request);
        PublicKey serverPubKey = Auxiliar.getServerPubKey(response.getPublicKey().toByteArray());
        String finalString = serverPubKey.toString() + response.getMessage();

        assertTrue(Auxiliar.verifySignature(finalString, serverPubKey, response.getSignature().toByteArray()));
        assertEquals(response.getMessage(), "valid");
        Thread.sleep(1500);
        // CHECK LOGIN
        assertTrue(Auxiliar.checkCredentials(username, password));
        // WRONG CREDENTIALS
        assertFalse(Auxiliar.checkCredentials(username, "password2"));
        // ACCOUNT ALREADY EXISTS
        assertTrue(Auxiliar.existsAccount(username));
    }
}
