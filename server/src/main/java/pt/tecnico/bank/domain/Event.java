package pt.tecnico.bank.domain;

public class Event {

    private int nonce;
    private long timestamp;

    public Event(int nonce, long timestamp) {
        this.nonce = nonce;
        this.timestamp = timestamp;
    }

    public int getNonce() { return this.nonce; }
    public long getTimestamp() { return this.timestamp; }
}
