package pt.tecnico.bank;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.commons.lang3.tuple.Pair;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Transactions;
import sun.misc.Signal;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class ServerMain {

	static List<Client> clientList = new ArrayList<>();

	public static void main(String[] args) throws IOException {
		System.out.println(ServerMain.class.getSimpleName());

		File file = new File("db.txt");

		BufferedReader br = new BufferedReader(new FileReader(file));
		String st, pubkey, pending, history;
		String[] arrayofStr, arrayofPending, arrayofPendingaux, arrayofHistory, arrayofHistoryaux;
		int balance;
		List<Transactions> pendingList, historyList;

		while ((st = br.readLine()) != null) {
			if (st.isBlank()){
				System.out.println("Empty DataBase.");
			}
			else{
				pendingList = new ArrayList<>();
				historyList = new ArrayList<>();
				arrayofStr = st.split(":");
				pubkey = arrayofStr[0];
				balance = Integer.parseInt(arrayofStr[1]);
				pending = arrayofStr[2];
				arrayofPending = pending.split(";");
				for (String p : arrayofPending){
					arrayofPendingaux = p.split(",");
					pendingList.add(new Transactions(arrayofPendingaux[0], Integer.parseInt(arrayofPendingaux[1])));
				}
				history = arrayofStr[3];
				arrayofHistory = history.split(";");
				for (String p : arrayofHistory){
					arrayofHistoryaux = p.split(",");
					historyList.add(new Transactions(arrayofHistoryaux[0], Integer.parseInt(arrayofHistoryaux[1])));
				}

				clientList.add(new Client(pubkey, balance, pendingList, historyList));
			}
		}

		for (Client client:clientList){
			System.out.println("PubKey: " + client.getPubKey() + "\nBalance: " + client.getBalance() + "\n\nTransactions");
			for (Transactions transaction : client.getPending()){
				System.out.println("From " + transaction.getPubkey() + " with value " + transaction.getValue());
			}
			System.out.println("\nHistory");
			for (Transactions transaction : client.getHistory()){
				System.out.println("From " + transaction.getPubkey() + " with value " + transaction.getValue());
			}
		}

		try {
			final BindableService impl = new ServerServiceImpl();

			Server server = ServerBuilder.forPort(8080).addService(impl).build();
			server.start();
			System.out.println("Server started");

			// Create new thread where we wait for the user input.
			new Thread(() -> {
				System.out.println("<Press enter to shutdown>");
				new Scanner(System.in).nextLine();

				try {
					FileWriter fw = new FileWriter("db.txt");
					String write = "";
					for (Client client: clientList){
						write += client.getPubKey() + ":" + client.getBalance() + ":";
						for (Transactions trans: client.getPending()){
							write += trans.getPubkey() + "," + trans.getValue() + ";";
						}
						write = write.substring(0, write.length() - 1) + ":";
						for (Transactions trans: client.getHistory()){
							write += trans.getPubkey() + "," + trans.getValue() + ";";
						}
						write = write.substring(0, write.length() - 1) + "\n";
					}
					write = write.substring(0, write.length() - 1);
					fw.write(write);
					fw.close();

				} catch (IOException e) {
					e.printStackTrace();
				}

				server.shutdown();
			}).start();

			// Catch SIGINT signal
			Signal.handle(new Signal("INT"), signal -> server.shutdown());

			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();

		} catch (Exception e) {
			System.out.println("Internal Server Error: " + e.getMessage());
		} finally {
			System.out.println("Server closed");
			System.exit(0);
		}
	}
}
