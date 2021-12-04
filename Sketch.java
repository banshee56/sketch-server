import java.util.*;

public class Sketch {
    TreeMap<Integer, Shape> id2Shape; // the map from moving IDs to shapes

    Sketch() {
        id2Shape = new TreeMap<>();
    }

    /**
     * Updates the shape in the map by either adding a new shape with a newly assigned movingID (draw)
     * or by changing the shape held by that movingID (shape has different location for move, different
     * color for recolor)
     */
    public synchronized void updateSketch(Shape shape, int movingID) {
        id2Shape.put(movingID, shape);
	}

    /**
     * Removes shape from sketch
     */
	public synchronized void deleteFromSketch(int movingId) {
		id2Shape.remove(movingId);
	}
}
