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
		String scanned;
		String[] tokens;

		boolean quit = false;

		while (!quit) {
			System.out.print("> ");
			System.out.flush();
			scanned = scanner.nextLine();
			tokens = scanned.split(" ");
			switch (tokens[0]) {
				case "quit":
					quit = true;
					System.out.println("Exiting the app.");
					frontend.close();
					break;

				case "ping":
					if (tokens.length == 1){
						app.ping();
					} else {
						System.out.println("ERRO input invalido.");
					}
					break;

				case "help":
					System.out.println("Estes sao os comandos disponiveis: \n" +
							"- balance       (retorna o balance do utilizador) \n" +
							"- top-up X      (acrescenta X em BIC na conta do utilizador) \n" +
							"- info X        (mostra informacao sobre a estacao X) \n" +
							"- scan X        (mostra as X estacoes mais proximas do utilizador) \n" +
							"- bike-up X     (levanta uma bicicleta da estacao X) \n" +
							"- bike-down X   (devolve uma bicileta na estacao X) \n" +
							"- at            (apresenta um link do google maps com as coordenadas do utilizador) \n" +
							"- tag X Y Z     (cria uma tag nas coordenadas (X,Y) com o nome Z) \n" +
							"- move X        (move o utilizador para a tag com nome X) \n" +
							"- move X Y      (move o utilizador para as coordenadas (X,Y)) \n" +
							"- sys_status    (mostra os servidores que existem e se estao UP ou DOWN) \n" +
							"- ping          (retorna uma mensagem Pong. do servidor) \n" +
							"- quit          (fecha a aplicacao)");
					break;

				default:
					System.out.println("ERRO input invalido ou comentario.");
					break;
			}
		}
	}
}
