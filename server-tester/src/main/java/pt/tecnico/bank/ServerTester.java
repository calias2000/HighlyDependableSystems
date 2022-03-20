package pt.tecnico.bank;


import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.grpc.*;

public class ServerTester {
	
	public static void main(String[] args) {
		System.out.println(ServerTester.class.getSimpleName());

		ServerFrontend frontend = new ServerFrontend();

		try {
			PingRequest request = PingRequest.newBuilder().setInput("friend").build();
			PingResponse response = frontend.ping(request);
			System.out.println(response.getOutput());
		} catch (StatusRuntimeException sre) {
			System.err.println("ERROR: " + sre.getMessage());
		}

		frontend.close();

	}
	
}