package nir.com.rouletteroyale;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Settlement and bet-lifecycle rules for the full betting table. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GameRulesTest {

    private static void set(Object o, String field, Object val) throws Exception {
        Field f = findField(o.getClass(), field);
        f.setAccessible(true);
        if (val instanceof Long) f.setLong(o, (Long) val); else f.set(o, val);
    }
    private static Field findField(Class<?> c, String name) throws Exception {
        while (c != null) { try { return c.getDeclaredField(name); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(name);
    }
    private static void call(Object o, String name, Class<?> t, Object arg) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name, t);
        m.setAccessible(true);
        m.invoke(o, arg);
    }
    private static void call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }
    private static RouletteView fresh() { return new RouletteView(RuntimeEnvironment.getApplication()); }

    private static long settle(RouletteView v, int result) throws Exception {
        set(v, "result", result);
        call(v, "finishSpin");
        return v.getBalance();
    }

    @Test public void zeroLosesAllOutsideBets() throws Exception {
        // every non-zero outside bet must lose on 0; only N0 wins
        for (String spot : new String[]{"RED", "BLACK", "EVEN", "ODD", "LOW", "HIGH", "D1", "D2", "D3", "C1", "C2", "C3"}) {
            RouletteView v = fresh();
            call(v, "placeBet", String.class, spot);        // 10 on the spot
            long bal = settle(v, 0);
            assertEquals(spot + " must lose on 0", 490L, bal);
        }
        RouletteView v = fresh();
        call(v, "placeBet", String.class, "N0");
        assertEquals("N0 pays 35:1 + stake", 490L + 360L, settle(v, 0));
    }

    @Test public void overlappingWinsSettleOncePerStake() throws Exception {
        // 7 is red, odd, low, dozen 1, column 1. 10 on each of N7,RED,ODD,LOW,D1,C1 = 60 staked.
        RouletteView v = fresh();
        for (String s : new String[]{"N7", "RED", "ODD", "LOW", "D1", "C1"})
            call(v, "placeBet", String.class, s);
        long bal = settle(v, 7);
        // 440 after staking; wins: 360 (straight) + 20*3 (RED/ODD/LOW) + 30 (dozen) + 30 (column) = 480 -> 920
        assertEquals(920L, bal);
    }

    @Test public void doubleChipOnSameSpotPaysOnce() throws Exception {
        RouletteView v = fresh();
        call(v, "placeBet", String.class, "RED");
        call(v, "placeBet", String.class, "RED");           // 20 total on RED
        assertEquals(480L, v.getBalance());
        assertEquals("20 on RED pays 20*2 once", 480L + 40L, settle(v, 7));
    }

    @Test public void undoRefundsLastChip() throws Exception {
        RouletteView v = fresh();
        call(v, "placeBet", String.class, "N7");
        call(v, "placeBet", String.class, "RED");
        call(v, "undoBet");
        assertEquals(490L, v.getBalance());                 // RED refunded, N7 remains
        call(v, "undoBet");
        assertEquals(500L, v.getBalance());
    }

    @Test public void clearRefundsEverything() throws Exception {
        RouletteView v = fresh();
        call(v, "placeBet", String.class, "N7");
        call(v, "placeBet", String.class, "RED");
        call(v, "placeBet", String.class, "D2");
        call(v, "clearBets");
        assertEquals(500L, v.getBalance());
    }

    @Test public void insufficientCoinsRejected() throws Exception {
        RouletteView v = fresh();
        final boolean[] out = {false};
        v.setListener(new RouletteView.Listener() {
            public void onOutOfCoins() { out[0] = true; }
            public void onSpinComplete(int s, long w, long b) {}
            public void onBigWin(long w, long b) {}
            public void onLevelUp(int l, long r) {}
            public void saveState() {}
        });
        set(v, "balance", 5L);
        set(v, "shownBalance", 5L);
        call(v, "placeBet", String.class, "RED");           // chip 10 > balance 5
        assertEquals(5L, v.getBalance());
        assertTrue("onOutOfCoins must fire", out[0]);
    }

    @Test public void spinLockedWhileSpinning() throws Exception {
        RouletteView v = fresh();
        call(v, "placeBet", String.class, "RED");
        call(v, "spin");
        Field f = findField(RouletteView.class, "spinStart"); f.setAccessible(true);
        long first = f.getLong(v);
        call(v, "spin");                                    // must be ignored
        assertEquals(first, f.getLong(v));
        call(v, "placeBet", String.class, "N7");            // betting locked mid-spin
        assertEquals(490L, v.getBalance());
    }
}
