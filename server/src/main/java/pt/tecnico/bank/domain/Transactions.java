package pt.tecnico.bank.domain;

public class Transactions {
    private String pubkey;
    private int value;

    public Transactions(String pubkey, int value){
        this.pubkey = pubkey;
        this.value = value;
    }

    public String getPubkey() {
        return pubkey;
    }

    public int getValue() {
        return value;
    }
}
