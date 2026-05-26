import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.types.Rect;

import java.nio.file.Files;
import java.nio.file.Paths;

public class SkijaSmoke {
    public static void main(String[] args) throws Exception {
        try (Surface surface = Surface.makeRasterN32Premul(200, 200)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFFFFFFFF);
            try (Paint paint = new Paint().setColor(0xFFFF0000)) {
                canvas.drawRect(Rect.makeXYWH(20, 20, 160, 160), paint);
            }
            try (Image img = surface.makeImageSnapshot();
                 Data data = img.encodeToData(EncodedImageFormat.PNG, 100)) {
                Files.write(Paths.get("out.png"), data.getBytes());
                System.out.println("png size = " + data.getBytes().length);
            }
        }
        System.out.println("Skija raster smoke PASS");
    }
}
