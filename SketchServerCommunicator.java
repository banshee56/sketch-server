import java.awt.*;
import java.io.*;
import java.net.Socket;

/**
 * Handles communication between the server and one client, for SketchServer
 *
 * @author Chris Bailey-Kellogg, Dartmouth CS 10, Fall 2012; revised Winter 2014 to separate SketchServerCommunicator
 * 
 * @author Bansharee Ireen; Dartmouth CS 10, Winter 2021; added code to scaffold
 */
public class SketchServerCommunicator extends Thread {
	private Socket sock;					// to talk with client
	private BufferedReader in;				// from client
	private PrintWriter out;				// to client
	private SketchServer server;			// handling communication for

	private int id = -1;

	public SketchServerCommunicator(Socket sock, SketchServer server) {
		this.sock = sock;
		this.server = server;
	}

	/**
	 * Sends a message to the client
	 * @param msg
	 */
	public void send(String msg) {
		out.println(msg);
	}
	
	/**
	 * Keeps listening for and handling (your code) messages from the client
	 */
	public void run() {
		try {
			System.out.println("someone connected");

			// Communication channel
			in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			out = new PrintWriter(sock.getOutputStream(), true);

			// Tell the client the current state of the world
			// tells the new client to draw each of the shapes present in the current server
			for (int id0: server.getSketch().id2Shape.navigableKeySet()) {
				Shape shape = server.getSketch().id2Shape.get(id0);
				out.println("draw "+shape.toString()+" "+id0);
			}

			// Keep getting and handling messages from the client
			String line;
			while ((line = in.readLine()) != null) {
				String[] editLine = line.split(" ");

				// just here to ensure that the value of the id keeps increasing
				// by starting from the largest id number
				for (int id0: server.getSketch().id2Shape.navigableKeySet()) {
					id = id0;
				}

				// gives unique ids to shapes that are being drawn, i.e. have movingID = -1
				if (editLine[7].equals("-1")) {
					id++;
					editLine[7] = String.valueOf(id);	// changes movingID from -1 to new ID
				}

				// creates the message with the updated id
				String msg = "";
				for (String s: editLine) {
					msg = msg + " " + s;
				}

				msg = msg.trim();
				Message.readMessage(msg, server.getSketch());	// updates the sketch
				server.broadcast(msg);	// broadcasts the changes to all editor communicators
			}

			// Clean up -- note that also remove self from server's list so it doesn't broadcast here
			server.removeCommunicator(this);
			out.close();
			in.close();
			sock.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
