import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;

/**
 * Client-server graphical editor
 * 
 * @author Chris Bailey-Kellogg, Dartmouth CS 10, Fall 2012; loosely based on CS 5 code by Tom Cormen
 * @author CBK, winter 2014, overall structure substantially revised
 * @author Travis Peters, Dartmouth CS 10, Winter 2015; remove EditorCommunicatorStandalone (use echo server for testing)
 * @author CBK, spring 2016 and Fall 2016, restructured Shape and some of the GUI
 * 
 * @author Bansharee Ireen; Dartmouth CS 10, Winter 2021; added code to scaffold
 */

public class Editor extends JFrame {	
	private static String serverIP = "localhost";			// IP address of sketch server
	// "localhost" for your own machine;
	// or ask a friend for their IP address

	private static final int width = 800, height = 800;		// canvas size

	// Current settings on GUI
	public enum Mode {
		DRAW, MOVE, RECOLOR, DELETE
	}
	private Mode mode = Mode.DRAW;				// drawing/moving/recoloring/deleting objects
	private String shapeType = "ellipse";		// type of object to add
	private Color color = Color.black;			// current drawing color

	// Drawing state
	// these are remnants of my implementation; take them as possible suggestions or ignore them
	private Shape curr = null;					// current shape (if any) being drawn
	private Sketch sketch;						// holds and handles all the completed objects
	private int movingId = -1;					// current shape id (if any; else -1) being moved
	private Point drawFrom = null;				// where the drawing started
	private Point moveFrom = null;				// where object is as it's being dragged


	// Communication
	private EditorCommunicator comm;			// communication with the sketch server

	public Editor() {
		super("Graphical Editor");

		sketch = new Sketch();

		// Connect to server
		comm = new EditorCommunicator(serverIP, this);
		comm.start();

		// Helpers to create the canvas and GUI (buttons, etc.)
		JComponent canvas = setupCanvas();
		JComponent gui = setupGUI();

		// Put the buttons and canvas together into the window
		Container cp = getContentPane();
		cp.setLayout(new BorderLayout());
		cp.add(canvas, BorderLayout.CENTER);
		cp.add(gui, BorderLayout.NORTH);

		// Usual initialization
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		pack();
		setVisible(true);
	}

	/**
	 * Creates a component to draw into
	 */
	private JComponent setupCanvas() {
		JComponent canvas = new JComponent() {
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				drawSketch(g);
			}
		};
		
		canvas.setPreferredSize(new Dimension(width, height));

		canvas.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent event) {
				handlePress(event.getPoint());
			}

			public void mouseReleased(MouseEvent event) {
				handleRelease();
			}
		});		

		canvas.addMouseMotionListener(new MouseAdapter() {
			public void mouseDragged(MouseEvent event) {
				handleDrag(event.getPoint());
			}
		});
		
		return canvas;
	}

	/**
	 * Creates a panel with all the buttons
	 */
	private JComponent setupGUI() {
		// Select type of shape
		String[] shapes = {"ellipse", "rectangle", "segment"};
		JComboBox<String> shapeB = new JComboBox<String>(shapes);
		shapeB.addActionListener(e -> shapeType = (String)((JComboBox<String>)e.getSource()).getSelectedItem());

		// Select drawing/recoloring color
		// Following Oracle example
		JButton chooseColorB = new JButton("choose color");
		JColorChooser colorChooser = new JColorChooser();
		JLabel colorL = new JLabel();
		colorL.setBackground(Color.black);
		colorL.setOpaque(true);
		colorL.setBorder(BorderFactory.createLineBorder(Color.black));
		colorL.setPreferredSize(new Dimension(25, 25));
		JDialog colorDialog = JColorChooser.createDialog(chooseColorB,
				"Pick a Color",
				true,  //modal
				colorChooser,
				e -> { color = colorChooser.getColor(); colorL.setBackground(color); },  // OK button
				null); // no CANCEL button handler
		chooseColorB.addActionListener(e -> colorDialog.setVisible(true));

		// Mode: draw, move, recolor, or delete
		JRadioButton drawB = new JRadioButton("draw");
		drawB.addActionListener(e -> mode = Mode.DRAW);
		drawB.setSelected(true);
		JRadioButton moveB = new JRadioButton("move");
		moveB.addActionListener(e -> mode = Mode.MOVE);
		JRadioButton recolorB = new JRadioButton("recolor");
		recolorB.addActionListener(e -> mode = Mode.RECOLOR);
		JRadioButton deleteB = new JRadioButton("delete");
		deleteB.addActionListener(e -> mode = Mode.DELETE);
		ButtonGroup modes = new ButtonGroup(); // make them act as radios -- only one selected
		modes.add(drawB);
		modes.add(moveB);
		modes.add(recolorB);
		modes.add(deleteB);
		JPanel modesP = new JPanel(new GridLayout(1, 0)); // group them on the GUI
		modesP.add(drawB);
		modesP.add(moveB);
		modesP.add(recolorB);
		modesP.add(deleteB);

		// Put all the stuff into a panel
		JComponent gui = new JPanel();
		gui.setLayout(new FlowLayout());
		gui.add(shapeB);
		gui.add(chooseColorB);
		gui.add(colorL);
		gui.add(modesP);
		return gui;
	}

	/**
	 * Getter for the sketch instance variable
	 */
	public Sketch getSketch() {
		return sketch;
	}

	/**
	 * Draws all the shapes in the sketch,
	 * along with the object currently being drawn in this editor (not yet part of the sketch)
	 */
	public void drawSketch(Graphics g) {
		if (!sketch.id2Shape.isEmpty()) {
			TreeMap<Integer, Shape> shapeMap = sketch.id2Shape;	// grab the map of shapes

			// traverse the map from the lowest ID to the highest ID to draw older shapes first
			// draws all the shapes in sketch
			for (int id: shapeMap.navigableKeySet()) {
				shapeMap.get(id).draw(g);	// if (shapeMap.containsKey(id))
			}
		}
		// draws the shape currently being drawn in draw mode (before it is added to sketch)
		if (mode == Mode.DRAW && curr!=null) {
			curr.draw(g);
		}
		repaint();
	}

	// Helpers for event handlers
	
	/**
	 * Helper method for press at point
	 * In drawing mode, start a new object;
	 * in moving mode, (request to) start dragging if clicked in a shape;
	 * in recoloring mode, (request to) change clicked shape's color
	 * in deleting mode, (request to) delete clicked shape
	 */
	private void handlePress(Point p) {
		if (mode == Mode.DRAW) {
			drawFrom = p;	// set drawFrom as the starting point of the shapes
			// creating "empty" shapes from drawFrom
			if (shapeType.equals("ellipse")) curr = new Ellipse(drawFrom.x, drawFrom.y, color);
			if (shapeType.equals("rectangle")) curr = new Rectangle(drawFrom.x, drawFrom.y, color);
			if (shapeType.equals("segment")) curr = new Segment(drawFrom.x, drawFrom.y, color);
		}
		// if in not in DRAW mode, first find the current shape clicked on
		else if (sketch!=null){
			// traverse the sketch from recent to older shapes until you find the shape being clicked on
			// so that if you click over 2 shapes, only the latest (top) shape is changed
			for (int id : sketch.id2Shape.descendingKeySet()) {
				if (sketch.id2Shape.get(id).contains(p.x, p.y)) {	// checking if clicking on existing shape in sketch
					curr = sketch.id2Shape.get(id);	// set curr to clicked shape
					movingId = id;					// set movingID to curr shape id
					break;
				}
			}
			// if a shape was clicked on
			if (curr!=null && curr.contains(p.x, p.y)) {
				if (mode == Mode.MOVE) {
					moveFrom = p;
					comm.updateShape(curr, movingId, "move");	// asking server to move shape
				}

				if (mode == Mode.RECOLOR) {
					curr.setColor(color);	// changing the color of the shape in local sketch
					comm.updateShape(curr, movingId, "recolor");	// asking server to update color
					curr = null;	// setting curr to null again
					movingId = -1;
					repaint();
				}

				if (mode == Mode.DELETE) {
					comm.updateShape(curr, movingId, "delete");	// asking server to remove shape
					curr = null;	// setting curr to null in local sketch
					movingId = -1;
					repaint();
				}
			}
		}
	}

	/**
	 * Helper method for drag to new point
	 * In drawing mode, update the other corner of the object;
	 * in moving mode, (request to) drag the object
	 */
	private void handleDrag(Point p) {
		// TODO: YOUR CODE HERE
		// updating the other corner of the shape
		if (mode == Mode.DRAW) {
			if (shapeType.equals("ellipse")) {
				((Ellipse) curr).setCorners(drawFrom.x, drawFrom.y, p.x, p.y);
			}
			if (shapeType.equals("rectangle")) {
				((Rectangle) curr).setCorners(drawFrom.x, drawFrom.y, p.x, p.y);
			}
			if (shapeType.equals("segment")) {
				((Segment) curr).setEnd(p.x, p.y);
			}

		}
		// moving the shape and requesting server to update movement
		if (mode == Mode.MOVE && moveFrom!=null) {
			curr.moveBy(p.x-moveFrom.x, p.y-moveFrom.y);	// moving shape locally
			moveFrom = p;
			comm.updateShape(curr, movingId, "move");	// asking server to move shape in all editors
		}
		repaint();
	}

	/**
	 * Helper method for release
	 * In drawing mode, pass the add new object request on to the server;
	 * in moving mode, release it		
	 */
	private void handleRelease() {
		if (mode == Mode.DRAW) {
			comm.updateShape(curr, -1, "draw");
		}
		if (mode == Mode.MOVE) {
			moveFrom = null;
		}
		// having to set curr to null,
		// otherwise editor keeps drawing curr separately even after it is added to the sketch and removed
		curr = null;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new Editor();
			}
		});	
	}
}
