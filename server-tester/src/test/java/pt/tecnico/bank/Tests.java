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
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class Tests {

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
    public void PingTest() {
        PingRequest request = PingRequest.newBuilder().setInput("Ping").build();
        PingResponse response = frontend.ping(request);
        assertEquals("PingPong", response.getOutput());
    }

    @Test
    public void OpenAccountTest() {
        String username = "diogo";
        String password = "password1";
        generateStoreandCer(username, password);
        PublicKey publicKey = getKeyPair(username, password).getPublic();
        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username).build();
        assertTrue(frontend.openAccount(request).getAck());
    }

    @Test
    public void LoginTest() {
        String username = "diogo";
        String password = "password1";
        assertTrue(checkCredentials(username, password));
    }

    @Test
    public void WrongCredentialsTest() {
        String username = "diogo";
        String password = "password2";
        assertFalse(checkCredentials(username, password));
    }

    @Test
    public void AccountAlreadyExistsTest() {
        String username = "diogo";
        assertTrue(existsAccount(username));
    }

    @Test
    public void CheckAccountTest() {
        String username = "diogo";
        PublicKey publicKey = getPubKeyfromCert(username);
        CheckAccountRequest request = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).build();

        // Default account balance
        assertEquals(500, frontend.checkAccount(request).getBalance());

        // No Pending Transactions yet
        assertEquals(0, frontend.checkAccount(request).getPendentTransfersCount());
    }

    @Test
    public void CheckAccountNotExistTest() {
        String username = "goncalo";
        assertNull(getPubKeyfromCert(username));
    }

    @Test
    public void OpenSecondAccountTest() {
        String username = "goncalo";
        String password = "password1";
        generateStoreandCer(username, password);
        PublicKey publicKey = getKeyPair(username, password).getPublic();
        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username).build();
        assertTrue(frontend.openAccount(request).getAck());
    }

    @Test
    public void OpenThirdAccountTest() {
        String username = "bernardo";
        String password = "password1";
        generateStoreandCer(username, password);
        PublicKey publicKey = getKeyPair(username, password).getPublic();
        OpenAccountRequest request = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setUsername(username).build();
        assertTrue(frontend.openAccount(request).getAck());
    }

    @Test
    public void SendAmountTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String senderUsername = "diogo";
        String senderPassword = "password1";
        String receiverUsername = "goncalo";
        int amount = 50;
        KeyPair keyPair = getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        long timeMilli = new Date().getTime();
        String finalString = keyPair.getPublic().toString() + amount + random + timeMilli + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setTimestamp(timeMilli)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertTrue(frontend.sendAmount(request).getAck());
    }

    @Test
    public void CheckAccountWithPendingTransactions() {
        String username = "goncalo";
        PublicKey publicKey = getPubKeyfromCert(username);
        CheckAccountRequest request = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey.getEncoded())).build();

        // Receiver check account
        assertEquals(500, frontend.checkAccount(request).getBalance());
        assertEquals("50 from diogo", frontend.checkAccount(request).getPendentTransfers(0));

        String username2 = "diogo";
        PublicKey publicKey1 = getPubKeyfromCert(username2);
        CheckAccountRequest request1 = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey1.getEncoded())).build();

        // Sender check account
        assertEquals(500, frontend.checkAccount(request1).getBalance());
        // No Pending Transactions yet
        assertEquals(0, frontend.checkAccount(request1).getPendentTransfersCount());
    }

    @Test
    public void SendAmountNotEnoughBalanceTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String senderUsername = "diogo";
        String senderPassword = "password1";
        String receiverUsername = "goncalo";
        int amount = 700;
        KeyPair keyPair = getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        long timeMilli = new Date().getTime();
        String finalString = keyPair.getPublic().toString() + amount + random + timeMilli + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setTimestamp(timeMilli)
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
        KeyPair keyPair = getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        long timeMilli = new Date().getTime();
        String finalString = keyPair.getPublic().toString() + amount + random + timeMilli + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKey.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setTimestamp(timeMilli)
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
        KeyPair keyPair = getKeyPair(senderUsername, senderPassword);
        PublicKey publicKey = getPubKeyfromCert(receiverUsername);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        long timeMilli = new Date().getTime();
        String finalString = keyPair.getPublic().toString() + amount + random + timeMilli + publicKey.toString();
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        String manInTheMiddleUsername = "bernardo";
        PublicKey publicKeyMIM = getPubKeyfromCert(manInTheMiddleUsername);

        //Bernardo intercepts the request and changes receiver public key
        SendAmountRequest request = SendAmountRequest.newBuilder()
                .setSenderKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(publicKeyMIM.getEncoded()))
                .setAmount(amount)
                .setNonce(random)
                .setTimestamp(timeMilli)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertThrows(StatusRuntimeException.class, () -> frontend.sendAmount(request), "Incorrect signature, repeated event or incorrect transaction id.");
    }

    @Test
    public void ReceiveAmountTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String username = "goncalo";
        String password = "password1";
        int transfer = 0;
        KeyPair keyPair = getKeyPair(username, password);

        int random = SecureRandom.getInstance("SHA1PRNG").nextInt();
        long timeMilli = new Date().getTime();
        String finalString = keyPair.getPublic().toString() + transfer + random + timeMilli;
        Signature dsaForSign = Signature.getInstance("SHA256withRSA");
        dsaForSign.initSign(keyPair.getPrivate());
        dsaForSign.update(finalString.getBytes());
        byte[] signature = dsaForSign.sign();

        ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .setTransfer(transfer)
                .setNonce(random)
                .setTimestamp(timeMilli)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertTrue(frontend.receiveAmount(request).getAck());
        CheckAccountRequest request1 = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded())).build();

        // Receiver check account
        assertEquals(550, frontend.checkAccount(request1).getBalance());

        String username2 = "diogo";
        PublicKey publicKey1 = getPubKeyfromCert(username2);
        CheckAccountRequest request2 = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(publicKey1.getEncoded())).build();

        // Sender check account
        assertEquals(450, frontend.checkAccount(request2).getBalance());
        // No Pending Transactions yet
        assertEquals(0, frontend.checkAccount(request1).getPendentTransfersCount());
    }



    public void generateStoreandCer(String username, String password) {

        try {
            String[] keystore_array = new String[14];
            keystore_array[0] = "keytool";
            keystore_array[1] = "-genkey";
            keystore_array[2] = "-alias";
            keystore_array[3] = username;
            keystore_array[4] = "-keyalg";
            keystore_array[5] = "RSA";
            keystore_array[6] = "-keystore";
            keystore_array[7] = "../client/Keystores/" + username + ".jks";
            keystore_array[8] = "-dname";
            keystore_array[9] = "CN=mqttserver.ibm.com, OU=ID, O=IBM, L=Hursley, S=Hantes, C=GB";
            keystore_array[10] = "-storepass";
            keystore_array[11] = password;
            keystore_array[12] = "-keypass";
            keystore_array[13] = password;

            ProcessBuilder builder = new ProcessBuilder(keystore_array);
            Process process = builder.start();
            process.waitFor();

            String[] certificate = new String[11];
            certificate[0] = "keytool";
            certificate[1] = "-v";
            certificate[2] = "-export";
            certificate[3] = "-alias";
            certificate[4] = username;
            certificate[5] = "-file";
            certificate[6] = "../client/Certificates/" + username + ".cer";
            certificate[7] = "-keystore";
            certificate[8] = "../client/Keystores/" + username + ".jks";
            certificate[9] = "-storepass";
            certificate[10] = password;

            builder.command(certificate).start();

        } catch (IOException | InterruptedException e) {
            System.out.println("ERROR while running Keytool commands.");
        }
    }

    public KeyPair getKeyPair(String username, String password) {
        try {
            FileInputStream is = new FileInputStream("../client/Keystores/" + username + ".jks");
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] passwd = password.toCharArray();
            keystore.load(is, passwd);
            Key key = keystore.getKey(username, passwd);
            Certificate cert = keystore.getCertificate(username);
            PublicKey publicKey = cert.getPublicKey();
            return new KeyPair(publicKey, (PrivateKey) key);

        } catch (IOException | CertificateException | UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            System.out.println("ERROR while retrieving keypair from keystore.");
            return null;
        }
    }

    public PublicKey getPubKeyfromCert(String username) {
        try {
            FileInputStream fin = new FileInputStream("../client/Certificates/" + username + ".cer");
            CertificateFactory f = CertificateFactory.getInstance("X.509");
            X509Certificate certificate1 = (X509Certificate) f.generateCertificate(fin);
            return certificate1.getPublicKey();
        } catch (FileNotFoundException | CertificateException e) {
            System.out.println("ERROR while retrieving public key from certificate.");
            return null;
        }
    }

    public boolean checkCredentials(String username, String password) {
        try {
            FileInputStream is = new FileInputStream("../client/Keystores/" + username + ".jks");
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] passwd = password.toCharArray();
            keystore.load(is, passwd);
            return true;
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e){
            System.out.println("\nWrong Credentials.");
            return false;
        }
    }

    public boolean existsAccount(String username) {
        try {
            new FileInputStream("../client/Certificates/" + username + ".cer");
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }
}
