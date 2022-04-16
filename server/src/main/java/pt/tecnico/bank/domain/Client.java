package pt.tecnico.bank.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class Client implements Serializable {
    private String username;
    private int balance;
    private List<Transactions> pending;
    private List<Transactions> history;
    private HashSet<Integer> eventList;

    public Client(String username) {
        this.username = username;
        this.balance = 500;
        this.pending = new ArrayList<>();
        this.history = new ArrayList<>();
        this.eventList = new HashSet<>();
    }

    public String getUsername() { return this.username; }

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

    public HashSet<Integer> getEventList() { return eventList; }
    public void addEvent (int nonce) {
        this.eventList.add(nonce);
    }
}
