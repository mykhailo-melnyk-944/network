package utb.fai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

public class SocketHandler {
	static final String DEFAULT_ROOM = "public";
	static final String COMMANDS = "#setMyName <name>, #sendPrivate <name> <message>, #join <room>, #leave <room>, #groups";
	private static final String STOP = new String("STOP");

	Socket mySocket;
	String clientID;
	ActiveHandlers activeHandlers;
	volatile String name;
	final Set<String> rooms = new CopyOnWriteArraySet<>();
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);
	CountDownLatch startSignal = new CountDownLatch(2);

	OutputHandler outputHandler = new OutputHandler();
	InputHandler inputHandler = new InputHandler();

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;
		rooms.add(DEFAULT_ROOM);
	}

	void send(String message) {
		if (!messages.offer(message))
			System.err.printf("Client %s message queue is full, dropping the message!\n", clientID);
	}

	boolean sharesRoomWith(SocketHandler other) {
		for (String room : other.rooms)
			if (rooms.contains(room))
				return true;
		return false;
	}

	static void closeQuietly(Socket socket) {
		if (socket == null)
			return;
		try {
			socket.close();
		} catch (IOException ignored) {
		}
	}

	class OutputHandler implements Runnable {
		public void run() {
			try {
				startSignal.countDown();
				startSignal.await();
				Writer writer = new OutputStreamWriter(mySocket.getOutputStream(), StandardCharsets.UTF_8);
				while (true) {
					String m = messages.take();
					if (m == STOP)
						break;
					writer.write(m + "\r\n");
					writer.flush();
				}
			} catch (IOException | InterruptedException ignored) {
			} finally {
				closeQuietly(mySocket);
			}
		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try {
				startSignal.countDown();
				startSignal.await();
				activeHandlers.add(SocketHandler.this);
				System.out.println("Client " + clientID + " connected");
				send("Welcome to the IM server! Enter your name (without spaces):");
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(mySocket.getInputStream(), StandardCharsets.UTF_8));
				String line;
				while ((line = reader.readLine()) != null)
					processLine(clean(line));
			} catch (IOException | InterruptedException ignored) {
			} finally {
				activeHandlers.remove(SocketHandler.this);
				closeQuietly(mySocket);
				messages.clear();
				messages.offer(STOP);
				System.out.println("Client " + clientID + (name != null ? " (" + name + ")" : "") + " disconnected");
			}
		}
	}

	private void processLine(String line) {
		if (line.isEmpty()) {
			if (name == null)
				send("Enter your name:");
		} else if (line.startsWith("#")) {
			processCommand(line);
		} else if (name == null) {
			changeName(line);
		} else if (rooms.isEmpty()) {
			send("You are not in any room, use #join <room>.");
		} else {
			String message = "[" + name + "] >> " + line;
			System.out.println(message + "   (rooms: " + String.join(",", rooms) + ")");
			activeHandlers.sendToRooms(this, message);
		}
	}

	private void processCommand(String line) {
		String[] parts = line.split("\\s+", 2);
		String command = parts[0];
		String argument = parts.length > 1 ? parts[1] : "";

		switch (command) {
			case "#setMyName":
				if (argument.isEmpty())
					send("Usage: #setMyName <name>");
				else
					changeName(argument);
				break;
			case "#sendPrivate":
				sendPrivate(argument);
				break;
			case "#join":
				if (argument.isEmpty())
					send("Usage: #join <room>");
				else if (rooms.add(argument))
					send("You joined the room " + argument + ".");
				else
					send("You are already in the room " + argument + ".");
				break;
			case "#leave":
				if (argument.isEmpty())
					send("Usage: #leave <room>");
				else if (rooms.remove(argument))
					send("You left the room " + argument + ".");
				else
					send("You are not in the room " + argument + ".");
				break;
			case "#groups":
				send(String.join(",", rooms));
				break;
			default:
				send("Unknown command " + command + ". Commands: " + COMMANDS);
		}
	}

	private void changeName(String newName) {
		boolean firstName = (name == null);
		if (newName.chars().anyMatch(Character::isWhitespace)) {
			send("The name must not contain spaces." + (firstName ? " Enter your name:" : ""));
		} else if (!activeHandlers.rename(this, newName)) {
			send("The name " + newName + " is already taken." + (firstName ? " Enter another name:" : ""));
		} else if (firstName) {
			System.out.println("Client " + clientID + " is now " + newName);
			send("Hello " + newName + "! You can chat now.");
			send("Commands: " + COMMANDS);
		} else {
			System.out.println("Client " + clientID + " changed the name to " + newName);
			send("Your name is now " + newName + ".");
		}
	}

	private void sendPrivate(String argument) {
		String[] parts = argument.split("\\s+", 2);
		if (name == null) {
			send("Enter your name first:");
		} else if (parts.length < 2) {
			send("Usage: #sendPrivate <name> <message>");
		} else if (!activeHandlers.sendPrivate(parts[0], "[" + name + "] >> " + parts[1])) {
			send("User " + parts[0] + " is not connected.");
		} else {
			System.out.println("[" + name + "] >> " + parts[1] + "   (private to " + parts[0] + ")");
		}
	}

	private static String clean(String line) {
		StringBuilder sb = new StringBuilder(line.length());
		for (char c : line.toCharArray()) {
			if (c == '\b' || c == 127) {
				if (sb.length() > 0)
					sb.setLength(sb.length() - 1);
			} else if (!Character.isISOControl(c)) {
				sb.append(c);
			}
		}
		return sb.toString().strip();
	}
}
