package pt.tecnico.bank.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class Client implements Serializable {
    private String username;
    private int balance;
    private int pendent_balance;
    private List<Transactions> pending;
    private List<Transactions> history;
    private int wid;
    private int rid;
    private byte [] pair_signature;
    private HashSet<Integer> eventList;

    public Client(String username, byte [] pair_signature) {
        this.username = username;
        this.balance = 500;
        this.pendent_balance = 0;
        this.pending = new ArrayList<>();
        this.history = new ArrayList<>();
        this.wid = 0;
        this.rid = 0;
        this.pair_signature = pair_signature;
        this.eventList = new HashSet<>();
    }

    public String getUsername() { return this.username; }

    public int getBalance(){ return balance; }
    public void setBalance(int balance) { this.balance = balance; }

    public int getPendent_balance() { return this.pendent_balance; }
    public void addPendentBalance(int amount) { this.pendent_balance += amount; }

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

    public int getWid() { return this.wid; }
    public int getRid() { return this.rid; }

    public byte[] getPair_signature() { return pair_signature; }
    public void setPairSign(byte[] pair_signature) { this.pair_signature = pair_signature; }

    public void incrementWid() { this.wid++; }
    public void incrementRid() { this.rid++; }

    public HashSet<Integer> getEventList() { return this.eventList; }
    public void addEvent(int nonce) { this.eventList.add(nonce); }
}
