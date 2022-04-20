package pt.tecnico.bank;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class Crypto {

    public Crypto() { }

    public int getSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG").nextInt();
        } catch (NoSuchAlgorithmException e){
            System.out.println("Wrong Algorithm.");
            return 0;
        }
    }

    public byte[] getSignature(String finalString, PrivateKey privateKey) {
        try {
            Signature dsaForSign = Signature.getInstance("SHA256withRSA");
            dsaForSign.initSign(privateKey);
            dsaForSign.update(finalString.getBytes());
            return dsaForSign.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            System.out.println("Something went wrong while signing.");
            return null;
        }
    }

    public boolean verifySignature(String finalString, PublicKey publicKey, byte[] signature){
        try {
            Signature dsaForVerify = Signature.getInstance("SHA256withRSA");
            dsaForVerify.initVerify(publicKey);
            dsaForVerify.update(finalString.getBytes());
            return dsaForVerify.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e){
            System.out.println("Signatures don't match.");
            return false;
        }
    }

    public PublicKey getPubKeyGrpc(byte[] pubKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKey));
    }

}
