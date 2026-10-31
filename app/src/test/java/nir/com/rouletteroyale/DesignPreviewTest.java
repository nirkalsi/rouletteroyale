package nir.com.rouletteroyale;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Renders the REAL screen (native top row + RouletteView canvas) as PNGs for
 *  design preview. Local render, not a device capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class DesignPreviewTest {

    private static Field fld(Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n); f.setAccessible(true); return f;
    }
    private static void setLong(Object o, String n, long v) throws Exception { fld(o.getClass(), n).setLong(o, v); }
    private static void setInt(Object o, String n, int v) throws Exception { fld(o.getClass(), n).setInt(o, v); }
    @SuppressWarnings("unchecked")
    private static Map<String, Integer> betsMap(Object o) throws Exception { return (Map<String, Integer>) fld(o.getClass(), "bets").get(o); }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderStates() throws Exception {
        new File("/tmp/rl-design").mkdirs();
        int W = 1080, H = 2400;

        MainActivity act = new MainActivity();
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true);
        attach.invoke(act, RuntimeEnvironment.getApplication());
        Method br = MainActivity.class.getDeclaredMethod("buildButtonRow");
        br.setAccessible(true);
        View row = (View) br.invoke(act);
        android.widget.LinearLayout rowL = (android.widget.LinearLayout) row;
        for (int i = 0; i < rowL.getChildCount(); i++)
            ((android.widget.Button) rowL.getChildAt(i)).setSingleLine(false);

        RouletteView v = new RouletteView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B3D2E);
        root.addView(row);
        root.addView(v, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        setLong(v, "balance", 12480L);
        setLong(v, "shownBalance", 12480L);
        setInt(v, "betIdx", 1);
        setInt(v, "level", 7);
        setLong(v, "xp", 1600L);
        setLong(v, "spinsDone", 41L);

        // ---- state 1: betting with chips staked on several spots ----
        Map<String, Integer> bets = betsMap(v);
        bets.put("N7", 10);
        bets.put("N20", 25);
        bets.put("RED", 50);
        bets.put("D2", 10);
        bets.put("C3", 5);
        setLong(v, "lastTouchAt", System.currentTimeMillis());
        root.draw(c);
        save(bmp, "/tmp/rl-design/preview-1-betting.png");

        // ---- state 2: no bets, fresh table ----
        bets.clear();
        root.draw(c);
        save(bmp, "/tmp/rl-design/preview-2-clean.png");
    }

    private static void save(Bitmap bmp, String path) throws Exception {
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
        fos.close();
        System.out.println("WROTE " + path);
    }
}
