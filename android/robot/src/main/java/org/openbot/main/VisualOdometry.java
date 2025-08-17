package org.openbot.vision;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ultra-light VO (no external libs)
 * - Detect features with a minimal Harris corner score
 * - Track via block matching (SAD) in a small search window
 * - Estimate Δx, Δy (pixels) from median flow; θ from median angle delta
 * - Integrate into a simple pose (x,y,theta) in "VO units"
 *
 * NOTE: Scale is arbitrary (pixels). You can tune PIXELS_TO_METERS to map to meters.
 * This is a teaching skeleton – optimize/tweak thresholds on device.
 */
public class VisualOdometry {

    // ===== Config =====
    private static final int MAX_FEATURES = 250;
    private static final int NMS_RADIUS = 4;           // non-max suppression radius (px)
    private static final float HARRIS_K = 0.04f;
    private static final int HARRIS_BLOCK = 3;         // neighborhood radius for Harris
    private static final int TRACK_WINDOW = 7;         // match window half-size (patch radius)
    private static final int SEARCH_RADIUS = 8;        // search radius for block matching
    private static final int MIN_GRAD = 20;            // ignore very flat regions
    private static final int MIN_FEATURE_SPACING = 6;  // suppress too dense features
    private static final float MIN_CORR = 0.85f;       // reject poor matches (NCC threshold)
    private static final float PIXELS_TO_METERS = 0.0025f; // rough scale; tune on your phone

    // Pose (integrated)
    private double x = 0, y = 0, theta = 0;

    // Previous frame state
    private int prevW = -1, prevH = -1;
    private int[] prevGray = null;
    private List<Point> prevPts = new ArrayList<>();

    // Simple 2D point container
    private static class Point {
        int x, y;
        float score; // for corners
        Point(int x, int y) { this.x = x; this.y = y; }
        Point(int x, int y, float s) { this.x = x; this.y = y; this.score = s; }
    }

    public static class Pose2D {
        public final double x, y, theta;
        public Pose2D(double x, double y, double theta) { this.x = x; this.y = y; this.theta = theta; }
    }

    public Pose2D getPose() { return new Pose2D(x, y, theta); }

    /** Call this every camera frame (RGB Bitmap). */
    public void update(Bitmap frameRgb) {
        final int w = frameRgb.getWidth();
        final int h = frameRgb.getHeight();
        int[] gray = new int[w * h];
        rgbToGray(frameRgb, gray);

        if (prevGray == null || prevW != w || prevH != h) {
            // First frame → just detect features and store
            prevW = w; prevH = h;
            prevGray = gray;
            prevPts = detectCorners(gray, w, h, MAX_FEATURES);
            return;
        }

        // Track features with block matching
        List<Point> currPts = new ArrayList<>(prevPts.size());
        List<float[]> flows = new ArrayList<>(prevPts.size()); // dx,dy per feature
        for (Point p : prevPts) {
            float[] best = matchPoint(prevGray, gray, prevW, prevH, p.x, p.y);
            if (best != null) {
                int nx = (int)Math.round(p.x + best[0]);
                int ny = (int)Math.round(p.y + best[1]);
                // Keep only if inside bounds
                if (nx >= TRACK_WINDOW && ny >= TRACK_WINDOW && nx < w - TRACK_WINDOW && ny < h - TRACK_WINDOW) {
                    currPts.add(new Point(nx, ny));
                    flows.add(new float[]{best[0], best[1]});
                }
            }
        }

        if (flows.size() >= 8) {
            // Robust delta from median flow
            float medDx = medianComponent(flows, 0);
            float medDy = medianComponent(flows, 1);

            // Estimate small rotation from median angle change
            double medDTheta = estimateRotation(prevPts, currPts);

            // Integrate to pose (scale pixels → meters; rotate body frame to world)
            // Small-angle approximation: rotate transl by current heading
            double dx_m = medDx * PIXELS_TO_METERS;
            double dy_m = medDy * PIXELS_TO_METERS;

            double cosT = Math.cos(theta), sinT = Math.sin(theta);
            x +=  cosT * dx_m - sinT * dy_m;
            y +=  sinT * dx_m + cosT * dy_m;
            theta += medDTheta;
        }

        // Refresh features every few frames or when count drops
        if (currPts.size() < MAX_FEATURES * 0.4f) {
            currPts = detectCorners(gray, w, h, MAX_FEATURES);
        }

        // Roll frame state
        prevGray = gray;
        prevW = w; prevH = h;
        prevPts = currPts;
    }

    // ============ Image helpers ============

    private static void rgbToGray(Bitmap bmp, int[] outGray) {
        final int w = bmp.getWidth(), h = bmp.getHeight();
        int[] row = new int[w];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            bmp.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int c = row[x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                // Luma (BT.601-ish)
                int Y = (r * 299 + g * 587 + b * 114 + 500) / 1000;
                outGray[idx++] = Y;
            }
        }
    }

    // Harris corner detection (very compact)
    private List<Point> detectCorners(int[] gray, int w, int h, int maxPts) {
        int wh = w * h;
        int[] Ix = new int[wh];
        int[] Iy = new int[wh];

        // Sobel gradients
        for (int y = 1; y < h - 1; y++) {
            int off = y * w;
            for (int x = 1; x < w - 1; x++) {
                int a = off + x;
                int gx =
                        -gray[a - w - 1] - 2 * gray[a - 1] - gray[a + w - 1] +
                         gray[a - w + 1] + 2 * gray[a + 1] + gray[a + w + 1];
                int gy =
                        -gray[a - w - 1] - 2 * gray[a - w] - gray[a - w + 1] +
                         gray[a + w - 1] + 2 * gray[a + w] + gray[a + w + 1];
                Ix[a] = gx; Iy[a] = gy;
            }
        }

        // Harris response
        float[] R = new float[wh];
        int r = HARRIS_BLOCK;
        for (int y = r; y < h - r; y++) {
            for (int x = r; x < w - r; x++) {
                long Sx2 = 0, Sy2 = 0, Sxy = 0;
                for (int yy = -r; yy <= r; yy++) {
                    int off = (y + yy) * w;
                    for (int xx = -r; xx <= r; xx++) {
                        int a = off + (x + xx);
                        int ix = Ix[a], iy = Iy[a];
                        Sx2 += ix * (long)ix;
                        Sy2 += iy * (long)iy;
                        Sxy += ix * (long)iy;
                    }
                }
                float det = (float)(Sx2 * Sy2 - Sxy * Sxy);
                float trace = (float)(Sx2 + Sy2) + 1e-6f;
                R[y * w + x] = det - HARRIS_K * trace * trace;
            }
        }

        // Non-max suppression and threshold
        List<Point> candidates = new ArrayList<>();
        float thr = percentile(R, 0.995f); // keep strongest ~0.5% by default
        for (int y = r + NMS_RADIUS; y < h - r - NMS_RADIUS; y++) {
            for (int x = r + NMS_RADIUS; x < w - r - NMS_RADIUS; x++) {
                int a = y * w + x;
                float v = R[a];
                if (v < thr) continue;

                boolean isMax = true;
                for (int yy = -NMS_RADIUS; isMax && yy <= NMS_RADIUS; yy++) {
                    int off = (y + yy) * w;
                    for (int xx = -NMS_RADIUS; xx <= NMS_RADIUS; xx++) {
                        if (xx == 0 && yy == 0) continue;
                        if (R[off + (x + xx)] >= v) { isMax = false; break; }
                    }
                }
                if (isMax) candidates.add(new Point(x, y, v));
            }
        }

        // Spatial thinning (simple grid / spacing)
        candidates.sort((a,b)->Float.compare(b.score, a.score));
        List<Point> kept = new ArrayList<>(Math.min(maxPts, candidates.size()));
        for (Point p : candidates) {
            boolean ok = true;
            for (Point q : kept) {
                int dx = p.x - q.x, dy = p.y - q.y;
                if (dx*dx + dy*dy < MIN_FEATURE_SPACING*MIN_FEATURE_SPACING) { ok = false; break; }
            }
            if (ok) {
                kept.add(new Point(p.x, p.y));
                if (kept.size() >= maxPts) break;
            }
        }
        return kept;
    }

    // Block matching with SAD + NCC gating; returns {dx,dy} or null
    private float[] matchPoint(int[] prev, int[] curr, int w, int h, int x, int y) {
        int r = TRACK_WINDOW;
        // Reject if too close to border
        if (x < r || y < r || x >= w - r || y >= h - r) return null;

        // Precompute template stats for NCC
        double sumT = 0, sumT2 = 0;
        int tplCount = 0;
        for (int yy = -r; yy <= r; yy++) {
            int off = (y + yy) * w;
            for (int xx = -r; xx <= r; xx++) {
                int v = prev[off + (x + xx)];
                sumT += v; sumT2 += v*v;
                tplCount++;
            }
        }
        double meanT = sumT / tplCount;
        double varT = Math.max(1e-6, sumT2 / tplCount - meanT*meanT);

        int bestDx = 0, bestDy = 0;
        long bestSAD = Long.MAX_VALUE;
        double bestNCC = -1.0;

        for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
            int yy = y + dy;
            if (yy < r || yy >= h - r) continue;
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                int xx = x + dx;
                if (xx < r || xx >= w - r) continue;

                long sad = 0;
                double sumC = 0, sumC2 = 0, sumTC = 0;
                for (int sy = -r; sy <= r; sy++) {
                    int offP = (y + sy) * w + (x - r);
                    int offC = (yy + sy) * w + (xx - r);
                    for (int sx = 0; sx <= 2*r; sx++) {
                        int tp = prev[offP + sx];
                        int cp = curr[offC + sx];
                        int d = tp - cp;
                        sad += Math.abs(d);
                        sumC += cp;
                        sumC2 += cp*cp;
                        sumTC += (tp - meanT) * (cp);
                    }
                }
                double count = (2*r + 1) * (2*r + 1);
                double meanC = sumC / count;
                double varC = Math.max(1e-6, sumC2 / count - meanC*meanC);
                // Normalized cross-correlation (zero-mean template)
                double ncc = (sumTC - count * meanT * meanC)
                           / Math.sqrt(varT * varC + 1e-6);

                if (sad < bestSAD) {
                    bestSAD = sad; bestDx = dx; bestDy = dy; bestNCC = ncc;
                }
            }
        }

        if (bestNCC < MIN_CORR) return null; // reject weak match
        // Also reject if gradient is tiny (flat)
        if (!hasGradient(prev, w, h, x, y)) return null;

        return new float[]{bestDx, bestDy};
    }

    private boolean hasGradient(int[] gray, int w, int h, int x, int y) {
        if (x <= 0 || y <= 0 || x >= w-1 || y >= h-1) return false;
        int gX = (gray[y*w + (x+1)] - gray[y*w + (x-1)]);
        int gY = (gray[(y+1)*w + x] - gray[(y-1)*w + x]);
        return (Math.abs(gX) + Math.abs(gY)) >= MIN_GRAD;
    }

    private static float medianComponent(List<float[]> flows, int idx) {
        List<Float> vals = new ArrayList<>(flows.size());
        for (float[] f : flows) vals.add(f[idx]);
        Collections.sort(vals);
        int n = vals.size();
        return (n % 2 == 1) ? vals.get(n/2) : 0.5f * (vals.get(n/2 - 1) + vals.get(n/2));
    }

    // Very rough small-rotation estimate from feature angle changes (robust via median)
    private static double estimateRotation(List<Point> prev, List<Point> curr) {
        int n = Math.min(prev.size(), curr.size());
        if (n < 8) return 0;
        List<Double> dths = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Point a = prev.get(i);
            Point b = curr.get(i);
            double angPrev = Math.atan2(a.y, a.x);
            double angCurr = Math.atan2(b.y, b.x);
            double d = normalizeAngle(angCurr - angPrev);
            dths.add(d);
        }
        Collections.sort(dths);
        int m = dths.size();
        return (m % 2 == 1) ? dths.get(m/2) : 0.5*(dths.get(m/2 - 1) + dths.get(m/2));
    }

    private static double normalizeAngle(double a) {
        while (a > Math.PI) a -= 2*Math.PI;
        while (a < -Math.PI) a += 2*Math.PI;
        return a;
    }
  }
