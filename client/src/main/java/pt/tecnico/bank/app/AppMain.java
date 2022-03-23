package pt.tecnico.bank.app;

import pt.tecnico.bank.ServerFrontend;

import java.util.*;

public class AppMain {
	
	public static void main(String[] args) throws InterruptedException {
		System.out.println(AppMain.class.getSimpleName());

		// Initialization of the HubFrontend, App and Tag HashMap
		ServerFrontend frontend = new ServerFrontend();
		App app = new App(frontend);

		// Initialization of the scanner to scan the input from the user/file
		Scanner scanner = new Scanner(System.in);
		String scanned, password, id;
		String[] tokens;
		String[] parts;

		boolean quit = false;
		boolean credentials = false;

		while (!quit) {
			System.out.print("> ");
			scanned = scanner.nextLine();
			tokens = scanned.split(" ");
			switch (tokens[0]) {
				case "open-account":
					if (tokens.length == 2) {
						app.openAccount(tokens[1]);
					} else {
						System.out.println("WARNING invalid input.");
					}
					System.out.println("Chose your password: \n> ");
					password = scanner.nextLine();
					break;

				case "check-account":
					if (tokens.length == 2) {
						app.checkAccount(tokens[1]);
					} else {
						System.out.println("WARNING invalid input.");
					}
					break;

				case "audit":
					if (tokens.length == 2) {
						app.audit(tokens[1]);
					} else {
						System.out.println("WARNING invalid input.");
					}
					break;

				case "send-amount":
					if (tokens.length == 4) {
						app.sendAmount(tokens[1], tokens[2], Integer.parseInt(tokens[3]));
					} else {
						System.out.println("WARNING invalid input.");
					}
					break;

				case "receive-amount":
					if (tokens.length == 3) {
						app.receiveAmount(tokens[1], Integer.parseInt(tokens[2]));
					} else {
						System.out.println("WARNING invalid input.");
					}
					break;

				case "ping":
					if (tokens.length == 1) {
						app.ping();
					} else {
						System.out.println("WARNING invalid input.");
					}
					break;

				case "quit":
					quit = true;
					System.out.println("Exiting the app.");
					frontend.close();
					break;

				case "help":
					System.out.println("Available commands: \n" +
							"- open-account X Y      (opens account with pub key X and balance Y) \n" +
							"- check-account X       (show balance and incoming pending transactions from account with pub key X) \n" +
							"- audit X               (shows the history of transactions from the account with pub key X) \n" +
							"- ping                  (returns Pong from the server) \n" +
							"- quit                  (logs out)");
					break;

				default:
					System.out.println("ERRO input invalido ou comentario.");
					break;
			}
		}
	}
}
