package nir.com.rouletteroyale;

import android.graphics.Rect;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Scripted real gameplay for device acceptance video: taps derived from the game view's own layout math. */
@RunWith(AndroidJUnit4.class)
public class GameplayScriptTest {
    private UiDevice d;
    private Rect v;

    private void tap(float fx, float fy) {
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    private void awaitView(String pkg) {
        android.content.Context c = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
        UiObject2 first = d.wait(Until.findObject(By.pkg(pkg).clazz("android.widget.Button")), 30000);
        if (first == null) throw new AssertionError("top buttons not found for " + pkg);
        int dh = d.getDisplayHeight(), dw = d.getDisplayWidth(), top = 0;
        for (UiObject2 b : d.findObjects(By.pkg(pkg).clazz("android.widget.Button"))) {
            Rect r = b.getVisibleBounds();
            if (r.centerY() < dh / 4) top = Math.max(top, r.bottom);
        }
        int navId = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = navId > 0 ? c.getResources().getDimensionPixelSize(navId) : 0;
        v = new Rect(0, top, dw, dh - nav);
    }

    @Test public void playRoulette() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        awaitView("nir.com.rouletteroyale");
        Thread.sleep(1500);
        tap(0.5845f, 0.72f); Thread.sleep(700);   // select chip 25
        tap(0.4625f, 0.527f); Thread.sleep(700);  // bet on number 20
        tap(0.24f, 0.584f); Thread.sleep(700);    // bet on RED (even-chances row)
        tap(0.76f, 0.788f); Thread.sleep(12000);  // SPIN, ball settles, WIN banner
        tap(0.5f, 0.646f); Thread.sleep(1500);    // dismiss level-up overlay if shown
    }
}
