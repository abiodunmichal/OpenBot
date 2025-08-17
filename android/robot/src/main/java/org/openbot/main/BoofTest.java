package org.openbot.vision;

import boofcv.struct.image.GrayU8;
import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.io.image.ConvertBufferedImage;

import android.graphics.Bitmap;
import android.util.Log;

public class BoofTest {

    public static Bitmap runEdgeDetection(Bitmap input) {
        // Convert Bitmap to BoofCV image
        GrayU8 gray = new GrayU8(input.getWidth(), input.getHeight());
        ConvertBufferedImage.convertFrom(input, true, gray);

        // Run Canny edge detector
        CannyEdge<GrayU8, GrayU8> canny =
                FactoryEdgeDetectors.canny(2, true, true, GrayU8.class, GrayU8.class);

        canny.process(gray, 0.1f, 0.3f, null);
        GrayU8 edgeImg = canny.getEdges();

        // Convert back to Bitmap for testing
        Bitmap output = Bitmap.createBitmap(edgeImg.width, edgeImg.height, Bitmap.Config.ARGB_8888);
        ConvertBufferedImage.convertTo(edgeImg, output, true);

        Log.d("BoofTest", "Edge detection done.");
        return output;
    }
}
