/*
 * A class to handle the messages sent between client and server.
 *
 * 
 * @author Bansharee Ireen; Dartmouth CS 10, Winter 2021; added code to scaffold
 *
 */

import java.awt.*;

public class Message {

    public static void readMessage(String message, Sketch sketch) {
        String[] tokens = message.split(" ");

        // separate the string and hold the information in variables
        String mode = tokens[0];
        String shapeName = tokens[1];
        int x1 = Integer.parseInt(tokens[2]);
        int y1 = Integer.parseInt(tokens[3]);
        int x2 = Integer.parseInt(tokens[4]);
        int y2 = Integer.parseInt(tokens[5]);
        int rgb = Integer.parseInt(tokens[6]);
        int movingID = Integer.parseInt(tokens[7]);

        Color color = new Color(rgb);   // creating a color for the rgb value received in message

        if (mode.equals("delete")) {
            sketch.deleteFromSketch(movingID);  // removes shape
        }
        else if (mode.equals("draw") || mode.equals("move") || mode.equals("recolor")){
            Shape shape;
            // creates an instance of the modified shape
            if (shapeName.equals("ellipse")) {
                shape = new Ellipse(x1, y1, x2, y2, color);
            } else if (shapeName.equals("rectangle")) {
                shape = new Rectangle(x1, y1, x2, y2, color);
            } else {
                shape = new Segment(x1, y1, x2, y2, color);
            }

            // replaces/adds the the shape as value for the movingID
            sketch.updateSketch(shape, movingID);
        }
    }
}
