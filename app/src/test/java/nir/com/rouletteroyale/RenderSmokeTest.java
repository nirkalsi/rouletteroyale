package nir.com.rouletteroyale;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

/** Pixel-render smoke: game view + full screen + reward/daily/missions/shop overlays. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class RenderSmokeTest {

    private void renderView(View v, int w, int h, String path) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        assertTrue(path + " too small", new File(path).length() > 5000);
    }

    private MainActivity activity() {
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
    }

    private void callPrivate(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    private void callPrivate(Object o, String name, Class<?> type, Object arg) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name, type);
        m.setAccessible(true);
        m.invoke(o, arg);
    }

    private void renderLatestDialog(String tag) throws Exception {
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("no dialog for " + tag, d);
        android.view.View content = d.getWindow().getDecorView();
        content.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(1700, View.MeasureSpec.AT_MOST));
        renderView(content, Math.max(content.getMeasuredWidth(), 400),
                   Math.max(content.getMeasuredHeight(), 200) + 80, "/tmp/renders/" + tag + ".png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView1080() throws Exception {
        renderView(new RouletteView(RuntimeEnvironment.getApplication()), 1080, 2243, "/tmp/renders/rouletteroyale-game-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView720() throws Exception {
        renderView(new RouletteView(RuntimeEnvironment.getApplication()), 720, 1480, "/tmp/renders/rouletteroyale-game-720x1480.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderFullScreen1080() throws Exception {
        MainActivity a = activity();
        renderView(a.findViewById(android.R.id.content), 1080, 2243, "/tmp/renders/rouletteroyale-full-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderDailyBonus() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "maybeDailyBonus");
        renderLatestDialog("rouletteroyale-daily");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderMissions() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "showMissions");
        renderLatestDialog("rouletteroyale-missions");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderShop() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "showShop");
        renderLatestDialog("rouletteroyale-shop");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderOutOfCoinsRewardPrompt() throws Exception {
        MainActivity a = activity();
        a.onOutOfCoins();
        renderLatestDialog("rouletteroyale-outofcoins");
    }


    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderPlacedBets() throws Exception {
        RouletteView v = new RouletteView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(2243, View.MeasureSpec.EXACTLY));
        // place 10 (default chip) on straight 7, RED and 2nd dozen: balance 500 -> 470
        callPrivate(v, "placeBet", String.class, "N7");
        callPrivate(v, "placeBet", String.class, "RED");
        callPrivate(v, "placeBet", String.class, "D2");
        org.junit.Assert.assertEquals("three 10-chip placements must debit 30", 470L, v.getBalance());
        // settle the count-up animation so the render shows the true balance
        java.lang.reflect.Field sb = RouletteView.class.getDeclaredField("shownBalance");
        sb.setAccessible(true);
        sb.setLong(v, v.getBalance());
        renderView(v, 1080, 2243, "/tmp/renders/rouletteroyale-bets.png");
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void levelUpOverlay() throws Exception {
        RouletteView v = new RouletteView(RuntimeEnvironment.getApplication());
        renderView(v, 1080, 2243, "/tmp/renders/rouletteroyale-levelup-plain.png");
        setField(v, "levelUpPending", 3);
        setField(v, "levelUpReward", 450L);
        setField(v, "levelUpShowing", true);
        setField(v, "levelUpAt", System.currentTimeMillis() - 1000);
        renderView(v, 1080, 2243, "/tmp/renders/rouletteroyale-levelup.png");
    }

    private static void setField(Object o, String name, Object val) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        if (val instanceof Long) f.setLong(o, (Long) val);
        else if (val instanceof Integer) f.setInt(o, (Integer) val);
        else if (val instanceof Boolean) f.setBoolean(o, (Boolean) val);
        else f.set(o, val);
    }
}
