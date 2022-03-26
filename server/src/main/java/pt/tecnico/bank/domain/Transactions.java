package pt.tecnico.bank.domain;

import java.io.Serializable;
import java.security.PublicKey;

public class Transactions implements Serializable {
    private String username;
    private int value;
    private PublicKey publicKey;

    public Transactions(String username, int value, PublicKey publicKey){
        this.username = username;
        this.value = value;
        this.publicKey = publicKey;
    }

    public String getUsername() {
        return username;
    }

    public int getValue() {
        return value;
    }

    public PublicKey getPublicKey() { return this.publicKey; }
}
