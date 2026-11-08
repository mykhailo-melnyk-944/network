package utb.fai;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {

	public static void main(String[] args) {
		int port = 33000, max_conn = 2;

		if (args.length > 0) {
			if (args[0].startsWith("--help")) {
				System.out.printf("Usage: Server [PORT] [MAX_CONNECTIONS]\n" +
						"If PORT is not specified, default port %d is used\n" +
						"If MAX_CONNECTIONS is not specified, default number=%d is used\n", port, max_conn);
				return;
			}
			port = parseArgument(args[0], port, 1, 65535);
			if (args.length > 1)
				max_conn = parseArgument(args[1], max_conn, 1, Integer.MAX_VALUE / 2);
		}

		ServerSocket sSocket;
		try {
			sSocket = new ServerSocket(port);
		} catch (IOException e) {
			System.err.printf("Cannot listen on port %d: %s\n", port, e.getMessage());
			System.exit(1);
			return;
		}
		System.out.printf("IM server listening on port %d, maximum nr. of connections=%d...\n", port, max_conn);

		ExecutorService pool = Executors.newFixedThreadPool(2 * max_conn);
		ActiveHandlers activeHandlers = new ActiveHandlers();

		while (true) {
			Socket clientSocket = null;
			try {
				clientSocket = sSocket.accept();
				clientSocket.setKeepAlive(true);
				clientSocket.setTcpNoDelay(true);
				SocketHandler handler = new SocketHandler(clientSocket, activeHandlers);
				pool.execute(handler.inputHandler);
				pool.execute(handler.outputHandler);
			} catch (IOException e) {
				System.err.println("Failed to accept a client: " + e.getMessage());
				SocketHandler.closeQuietly(clientSocket);
			}
		}
	}

	private static int parseArgument(String value, int defaultValue, int min, int max) {
		try {
			int number = Integer.decode(value);
			if (number >= min && number <= max)
				return number;
		} catch (NumberFormatException ignored) {
		}
		System.err.printf("Argument %s is not valid, using default value %d\n", value, defaultValue);
		return defaultValue;
	}
}
