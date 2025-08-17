package org.openbot.vision;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Very simple Visual Odometry (VO) module.
 * Tracks camera motion from consecutive frames using optical flow (corner tracking).
 * 
 * NOTE: This is a skeleton, not a full implementation.
 * Later you’ll integrate feature extraction & optical flow.
 */
public class VisualOdometry {

    private static final String TAG = "VisualOdometry";

    // Last processed grayscale frame
    private float[][] lastFrame;

    // Robot’s estimated position (x, y, heading in radians)
    private double posX = 0.0;
    private double posY = 0.0;
    private double heading = 0.0;

    // Store tracked features (x, y pixel coordinates)
    private List<float[]> lastFeatures = new ArrayList<>();

    public VisualOdometry() {
        Log.d(TAG, "VO initialized");
    }

    /**
     * Convert Bitmap to grayscale float array (0–1 normalized).
     */
    private float[][] toGrayscale(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float[][] gray = new float[h][w];
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = pixels[y * w + x];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                gray[y][x] = (r + g + b) / 3f / 255f;
            }
        }
        return gray;
    }

    /**
     * Process a new frame from the camera.
     * Updates the estimated robot position.
     */
    public void processFrame(Bitmap frame) {
        float[][] gray = toGrayscale(frame);

        if (lastFrame == null) {
            lastFrame = gray;
            lastFeatures = detectCorners(gray);
            return;
        }

        // Track features using naive optical flow
        List<float[]> newFeatures = trackFeatures(lastFrame, gray, lastFeatures);

        // Estimate motion (translation + small rotation)
        estimateMotion(lastFeatures, newFeatures);

        // Update state
        lastFrame = gray;
        lastFeatures = newFeatures;
    }

    /**
     * Detect corners (simple FAST-like).
     * Placeholder: returns a grid of points.
     */
    private List<float[]> detectCorners(float[][] gray) {
        List<float[]> corners = new ArrayList<>();
        int step = 20;
        for (int y = step; y < gray.length; y += step) {
            for (int x = step; x < gray[0].length; x += step) {
                corners.add(new float[]{x, y});
            }
        }
        return corners;
    }

    /**
     * Track features between frames (naive block matching).
     */
    private List<float[]> trackFeatures(float[][] prev, float[][] curr, List<float[]> prevPts) {
        List<float[]> tracked = new ArrayList<>();
        for (float[] pt : prevPts) {
            // Stub: copy point as-is (no real tracking yet)
            tracked.add(new float[]{pt[0], pt[1]});
        }
        return tracked;
    }

    /**
     * Estimate motion from feature displacements.
     * For now, just logs movement.
     */
    private void estimateMotion(List<float[]> oldPts, List<float[]> newPts) {
        if (oldPts.size() != newPts.size()) return;

        double dx = 0, dy = 0;
        for (int i = 0; i < oldPts.size(); i++) {
            dx += (newPts.get(i)[0] - oldPts.get(i)[0]);
            dy += (newPts.get(i)[1] - oldPts.get(i)[1]);
        }

        dx /= oldPts.size();
        dy /= oldPts.size();

        // Update robot coordinates (scale factor TBD)
        posX += dx * 0.01;
        posY += dy * 0.01;

        Log.d(TAG, "Estimated position: x=" + posX + " y=" + posY);
    }

    public double getX() { return posX; }
    public double getY() { return posY; }
    public double getHeading() { return heading; }
}
