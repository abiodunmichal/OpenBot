package org.openbot.main;

import android.graphics.Bitmap;
import android.util.Log;

import boofcv.struct.image.GrayU8;
import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.android.ConvertBitmap;

public class BoofTest {

    public static Bitmap runEdgeDetection(Bitmap input) {
        // Convert Bitmap to BoofCV image
        GrayU8 gray = ConvertBitmap.bitmapToBoof(input, (GrayU8) null, null);

        // Run Canny edge detector
        CannyEdge<GrayU8, GrayU8> canny =
                FactoryEdgeDetectors.canny(2, true, true, GrayU8.class, GrayU8.class);

        canny.process(gray, 0.1f, 0.3f, null);
        GrayU8 edgeImg = canny.getBinary();  // use getBinary() instead of getEdges()

        // Convert back to Bitmap
        Bitmap output = Bitmap.createBitmap(edgeImg.width, edgeImg.height, Bitmap.Config.ARGB_8888);
        ConvertBitmap.boofToBitmap(edgeImg, output, null);

        Log.d("BoofTest", "Edge detection done.");
        return output;
    }
}
