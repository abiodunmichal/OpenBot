package org.openbot.main;

import android.graphics.Bitmap;
import android.util.Log;

import boofcv.struct.image.GrayU8;
import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.android.ConvertBitmap;

public class BoofTest {

    public static Bitmap runEdgeDetection(Bitmap input) {
        // Allocate GrayU8 image
        GrayU8 gray = new GrayU8(input.getWidth(), input.getHeight());
        ConvertBitmap.bitmapToBoof(input, gray, null);

        // Run Canny edge detector
        CannyEdge<GrayU8, GrayU8> canny =
                FactoryEdgeDetectors.canny(2, true, true, GrayU8.class, GrayU8.class);

        canny.process(gray, 0.1f, 0.3f, null);

        // Some builds have getEdges(), some use getContours()
        GrayU8 edgeImg = canny.getEdges();  // try this first

        // Convert back to Bitmap
        Bitmap output = Bitmap.createBitmap(edgeImg.width, edgeImg.height, Bitmap.Config.ARGB_8888);
        ConvertBitmap.boofToBitmap(edgeImg, output, null);

        Log.d("BoofTest", "Edge detection done.");
        return output;
    }
            }
