package pt.tecnico.bank.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Client implements Serializable {
    private int balance;
    List<Transactions> pending;
    List<Transactions> history;

    public Client() {
        this.balance = 500;
        this.pending = new ArrayList<>();
        this.history = new ArrayList<>();
    }


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
