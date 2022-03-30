package pt.tecnico.bank;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.bank.domain.Client;
import pt.tecnico.bank.domain.Event;
import sun.misc.Signal;

import java.io.*;
import java.security.PublicKey;
import java.util.*;


public class ServerMain implements Serializable{

	static HashMap<PublicKey,Client> clientList = new HashMap<>();
	static List<Event> eventList = new ArrayList<>();

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		System.out.println(ServerMain.class.getSimpleName());

		try{
			FileInputStream fileInput = new FileInputStream("db.txt");

			ObjectInputStream objectInput = new ObjectInputStream(fileInput);

			clientList = (HashMap<PublicKey, Client>)objectInput.readObject();

			objectInput.close();
			fileInput.close();


		} catch (EOFException e) {
			System.out.println("EMPTY DATABASE!!");
		}

		try {
			final BindableService impl = new ServerServiceImpl();

			Server server = ServerBuilder.forPort(8080).addService(impl).build();
			server.start();
			System.out.println("Server started");
			new Thread(() -> {
				System.out.println("<Press enter to shutdown>");
				new Scanner(System.in).nextLine();

				try {
					FileOutputStream myFileOutStream = new FileOutputStream("db.txt");
					ObjectOutputStream myObjectOutStream = new ObjectOutputStream(myFileOutStream);
					myObjectOutStream.writeObject(clientList);
					myObjectOutStream.close();
					myFileOutStream.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}

				server.shutdown();
			}).start();
			Signal.handle(new Signal("INT"), signal -> server.shutdown());
			server.awaitTermination();

		} catch (Exception e) {
			System.out.println("Internal Server Error: " + e.getMessage());
		} finally {
			System.out.println("Server closed");
			System.exit(0);
		}
	}
}
