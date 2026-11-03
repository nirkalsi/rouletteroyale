package nir.com.rouletteroyale;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Local Robolectric renders for store screenshot candidates. NOT device captures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class StoreShotsTest {

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
    @Test public void captureStoreStills() throws Exception {
        new File("/tmp/storeshots-rl").mkdirs();
        int W = 1080, H = 1920;
        RouletteView v = new RouletteView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        call(v, "placeBet", "RED"); Thread.sleep(150); v.draw(c);
        call(v, "placeBet", "N7");  Thread.sleep(150); v.draw(c);
        call(v, "placeBet", "D1");  Thread.sleep(150);
        v.draw(c);
        save(bmp, "/tmp/storeshots-rl/shot1-bets.png");

        int seed = 0;
        while (new Random(seed).nextInt(37) != 7) seed++;
        Field rngF = RouletteView.class.getDeclaredField("rng");
        rngF.setAccessible(true);
        rngF.set(v, new Random(seed));
        call(v, "spin");
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 1800) { v.draw(c); Thread.sleep(33); }
        save(bmp, "/tmp/storeshots-rl/shot2-spin.png");

        t0 = System.currentTimeMillis();
        while (spinning(v) && System.currentTimeMillis() - t0 < 20000) { v.draw(c); Thread.sleep(33); }
        if (spinning(v)) call(v, "finishSpin");
        v.draw(c); Thread.sleep(400); v.draw(c);
        save(bmp, "/tmp/storeshots-rl/shot3-result.png");
        Thread.sleep(1200); v.draw(c);
        save(bmp, "/tmp/storeshots-rl/shot4-win.png");
    }

    private static void save(Bitmap bmp, String path) throws Exception {
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
        fos.close();
    }
}
