package com.im.njams.sdk.model.svg;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for {@link Point}, the simple (x, y) coordinate holder.
 */
public class PointTest {

    private static final double DELTA = 0.0;

    @Test
    public void constructorStoresCoordinates() {
        Point point = new Point(1.5, -2.25);
        assertEquals(1.5, point.getX(), DELTA);
        assertEquals(-2.25, point.getY(), DELTA);
    }

    @Test
    public void settersUpdateCoordinates() {
        Point point = new Point(0, 0);
        point.setX(10.0);
        point.setY(20.0);
        assertEquals(10.0, point.getX(), DELTA);
        assertEquals(20.0, point.getY(), DELTA);
    }
}
