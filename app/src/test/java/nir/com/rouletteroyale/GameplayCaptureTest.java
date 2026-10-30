package nir.com.rouletteroyale;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Captures a scripted gameplay sequence as PNG frames for a synthetic preview video.
 *  NOT a device capture: rendered locally via Robolectric native graphics. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class GameplayCaptureTest {

    private static void call(Object o, String name, Object... args) throws Exception {
        for (Method m : o.getClass().getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                m.invoke(o, args);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean spinning(RouletteView v) throws Exception {
        Field f = RouletteView.class.getDeclaredField("spinning");
        f.setAccessible(true);
        return f.getBoolean(v);
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureRouletteSpin() throws Exception {
        new File("/tmp/frames-rl").mkdirs();
        RouletteView v = new RouletteView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        long t0 = System.currentTimeMillis();
        int frame = 0;
        boolean b1 = false, b2 = false, b3 = false, spun = false, finished = false;
        while (frame < 420) {                          // ~14s at 30fps
            long el = System.currentTimeMillis() - t0;
            if (!b1 && el > 500) { call(v, "placeBet", "RED"); b1 = true; }
            else if (b1 && !b2 && el > 1200) { call(v, "placeBet", "N7"); b2 = true; }
            else if (b2 && !b3 && el > 1900) { call(v, "placeBet", "D1"); b3 = true; }
            else if (b3 && !spun && el > 2800) { call(v, "spin"); spun = true; }
            else if (spun && !finished && el > 2800 + 3500 && !spinning(v)) { finished = true; }
            else if (spun && !finished && el > 2800 + 3500 && spinning(v)) { call(v, "finishSpin"); finished = true; }
            v.draw(c);
            FileOutputStream fos = new FileOutputStream(String.format("/tmp/frames-rl/f%04d.png", frame++));
            bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
            fos.close();
            Thread.sleep(33);
        }
    }
}
