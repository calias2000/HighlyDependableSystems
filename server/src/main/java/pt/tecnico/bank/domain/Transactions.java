package pt.tecnico.bank.domain;

import java.io.Serializable;
import java.security.PublicKey;

public class Transactions implements Serializable {
    private String username;
    private int value;
    private PublicKey publicKey;
    private byte[] signature;

    public Transactions(String username, int value, PublicKey publicKey){
        this.username = username;
        this.value = value;
        this.publicKey = publicKey;
    }
    public Transactions(String username, int value, PublicKey publicKey, byte[] signature){
        this.username = username;
        this.value = value;
        this.publicKey = publicKey;
        this.signature = signature;
    }

    public String getUsername() {
        return username;
    }

    public int getValue() {
        return value;
    }

    public PublicKey getPublicKey() { return this.publicKey; }

    public byte[] getSignature() { return this.signature; }
}
