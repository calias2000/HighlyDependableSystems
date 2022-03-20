package pt.tecnico.bank.domain;

import java.util.List;

public class Client {
    private final String pubKey;
    private int balance;
    List<Transactions> pending;
    List<Transactions> history;

    public Client(String pubKey, int balance, List<Transactions> pending, List<Transactions> history) {
        this.pubKey = pubKey;
        this.balance = balance;
        this.pending = pending;
        this.history = history;
    }

    public String getPubKey() { return pubKey; }

    public int getBalance(){ return balance; }
    public void setBalance(int balance) { this.balance = balance; }

    public List<Transactions> getPending() { return pending; }
    public void removePending (int index) {
        this.pending.remove(index);
    }
    public void addPending (Transactions transaction) {
        this.pending.add(transaction);
    }

    public List<Transactions> getHistory() { return history; }
    public void addHistory (Transactions transaction) {
        this.history.add(transaction);
    }
}
