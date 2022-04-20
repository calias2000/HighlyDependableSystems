package pt.tecnico.bank;

import io.grpc.netty.shaded.io.grpc.netty.InternalNettySocketSupport;

import java.util.HashMap;

public class ADEBInstanceManager {

    private HashMap<String, ADEBInstance> instances;

    public ADEBInstanceManager() {
        this.instances = new HashMap<String, ADEBInstance>();
    }

    public synchronized ADEBInstance getInstance(String input) {
        if (instances.containsKey(input)){
            return instances.get(input);
        }
        ADEBInstance adebInstance = new ADEBInstance();
        instances.put(input, adebInstance);
        return adebInstance;
    }

    public synchronized void deliver(String input) {
        if (instances.containsKey(input)) {
            ADEBInstance instance = instances.get(input);
            instance.deliver();
            instances.remove(input);
        }
    }

    public void test() {
        for (String p : instances.keySet()){
            System.out.println("KEY " + p);
        }
        System.out.println("SIZE " + instances.size());
    }
}
