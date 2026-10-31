package nir.com.rouletteroyale;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;

/** Roulette Royale - free social roulette. Virtual coins only, no real money, no prizes.
 *  Casino-table redesign: full betting layout (straight, dozens, columns, even chances),
 *  multi-chip placement, wheel + ball spin animation, win celebrations. */
public class RouletteView extends View {
    public interface Listener {
        void onOutOfCoins();
        void onSpinComplete(int spinsDone, long win, long bet);
        void onBigWin(long win, long bet);
        void onLevelUp(int level, long reward);
        void saveState();
    }

    // European wheel order, single zero
    private static final int[] WHEEL = {0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};
    private static final int[] REDS = {1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36};
    private static final int[] BETS = {5, 10, 25, 50};
    private static final int[] CHIP_COLORS = {0xFFC62828, 0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A};

    // payout multiples INCLUDING returned stake: straight 35:1, dozen/column 2:1, even chance 1:1
    private static int payoutMult(String spot) {
        if (spot.startsWith("N")) return 36;
        if (spot.startsWith("D") || spot.startsWith("C")) return 3;
        return 2;
    }

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private long balance = 500;
    private long shownBalance = 500;
    private long lastWin = 0;
    private int betIdx = 1;                    // selected chip: 10
    private long spinsDone = 0;
    private long xp = 0;
    private int level = 1;
    private long lastRegenAt = System.currentTimeMillis();

    // betting table state: spot id -> staked amount ("N7", "RED", "D2", "C3", ...)
    private final LinkedHashMap<String, Integer> bets = new LinkedHashMap<>();
    private final List<String> placeOrder = new ArrayList<>(); // for UNDO
    private final Set<String> winSpots = new HashSet<>();
    private long winFlashUntil = 0;
    private int levelUpPending = 0;
    private long levelUpReward = 0;
    private boolean levelUpShowing = false;
    private long levelUpAt = 0;
    private long settledAt = 0;

    private boolean spinning = false;
    private long spinStart = 0;
    private static final long SPIN_MS = 3400;
    private int result = -1;
    private float wheelAngle = 0f, startAngle = 0f, targetAngle = 0f;
    private float ballStart = 0f, ballTarget = 0f, ballAngle = 0f;

    private boolean animating = false;
    private long lastTouchAt = 0;
    private int pressedBtn = -1;
    private long betBumpAt = 0;               // 0 spin, 1 clear, 2 undo
    private long pressedAt = 0;

    // chip particles for win celebration
    private final float[] px = new float[18], py = new float[18], pvx = new float[18], pvy = new float[18];
    private boolean particlesOn = false;
    // coin burst: coins fly from screen center to the balance pill (daily bonus claim)
    private final float[] cbx = new float[14], cby = new float[14], cbvx = new float[14], cbvy = new float[14];
    private boolean coinBurstOn = false;
    private long coinBurstAt = 0;

    private long particlesAt = 0;

    public RouletteView(Context c) { super(c); }

    public void setListener(Listener l) { listener = l; }
    public void addCoins(long n) { balance += n; persist(); invalidate(); }
    public long getBalance() { return balance; }
    public int getLevel() { return level; }
    public long getSpinsDone() { return spinsDone; }
    private long xpNeeded() { return level * 500L; }

    public String serialize() { return balance + ";" + spinsDone + ";" + betIdx + ";" + lastRegenAt + ";" + xp + ";" + level; }
    public void restore(String s) {
        try {
            String[] a = s.split(";");
            balance = Long.parseLong(a[0]);
            shownBalance = balance;
            spinsDone = Long.parseLong(a[1]);
            if (a.length > 4) xp = Long.parseLong(a[4]);
            if (a.length > 5) level = Math.max(1, Integer.parseInt(a[5]));
            betIdx = Math.min(BETS.length - 1, Math.max(0, Integer.parseInt(a[2])));
            if (a.length > 3) lastRegenAt = Long.parseLong(a[3]);
        } catch (Exception ignored) {}
    }

    private void persist() { if (listener != null) listener.saveState(); }

    private static boolean isRed(int n) { for (int r : REDS) if (r == n) return true; return false; }

    private void regen() {
        long now = System.currentTimeMillis();
        if (balance < 100 && now - lastRegenAt >= 60000) {
            long ticks = (now - lastRegenAt) / 60000;
            balance = Math.min(100, balance + 5 * ticks);
            lastRegenAt += ticks * 60000;
            persist();
        }
    }

    private long totalBet() {
        long t = 0; for (int v : bets.values()) t += v; return t;
    }

    private void placeBet(String spot) {
        if (spinning) return;
        regen();
        int chip = BETS[betIdx];
        if (balance < chip) { if (listener != null) listener.onOutOfCoins(); return; }
        balance -= chip;
        bets.put(spot, bets.containsKey(spot) ? bets.get(spot) + chip : chip);
        placeOrder.add(spot);
        GameSounds.play(getContext(), GameSounds.CHIP);
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        persist();
        invalidate();
    }

    private void undoBet() {
        if (spinning || placeOrder.isEmpty()) return;
        String spot = placeOrder.remove(placeOrder.size() - 1);
        // refund the last chip placed on that spot: walk back its chip value
        // chips are always the selected value at placement time; refund by rebuilding
        int chip = BETS[betIdx];
        int cur = bets.get(spot);
        int refund = Math.min(chip, cur);
        // if the last chip on this spot was a different value, fall back to clearing remainder correctly:
        bets.put(spot, cur - refund);
        if (bets.get(spot) <= 0) bets.remove(spot);
        balance += refund;
        persist();
        invalidate();
    }

    private void clearBets() {
        if (spinning || bets.isEmpty()) return;
        balance += totalBet();
        bets.clear();
        placeOrder.clear();
        persist();
        invalidate();
    }

    private boolean spotWins(String spot, int n) {
        switch (spot.charAt(0)) {
            case 'N': return n == Integer.parseInt(spot.substring(1));
            case 'D':
                int dz = spot.charAt(1) - '0';
                return n >= (dz - 1) * 12 + 1 && n <= dz * 12;
            case 'C':
                int col = spot.charAt(1) - '0';
                return n > 0 && n % 3 == col % 3;
            default:
                switch (spot) {
                    case "RED": return isRed(n);
                    case "BLACK": return n != 0 && !isRed(n);
                    case "EVEN": return n != 0 && n % 2 == 0;
                    case "ODD": return n % 2 == 1;
                    case "LOW": return n >= 1 && n <= 18;
                    case "HIGH": return n >= 19;
                }
        }
        return false;
    }

    private void spin() {
        if (spinning || totalBet() == 0) return;
        xp += totalBet();
        lastWin = 0;
        winSpots.clear();
        result = rng.nextInt(37);
        spinning = true;
        spinStart = System.currentTimeMillis();
        GameSounds.play(getContext(), GameSounds.SPIN);
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        startAngle = wheelAngle;
        int pocket = 0;
        for (int i = 0; i < 37; i++) if (WHEEL[i] == result) pocket = i;
        // land the winning pocket at top; 4-6 full turns
        float seg = 360f / 37f;
        float pocketAngle = 360f - (pocket * seg) - seg / 2f;
        targetAngle = startAngle + 360f * (4 + rng.nextInt(3)) + ((pocketAngle - startAngle) % 360f + 360f) % 360f;
        // ball runs the other way, settles into the top pocket
        ballStart = ballAngle;
        ballTarget = -90f - 360f * (6 + rng.nextInt(3));
        persist();
        invalidate();
    }

    private void finishSpin() {
        spinning = false;
        spinsDone++;
        wheelAngle = targetAngle % 360f;
        ballAngle = ballTarget % 360f;
        long tb = totalBet();
        long win = 0;
        for (Map.Entry<String, Integer> e : bets.entrySet()) {
            if (spotWins(e.getKey(), result)) {
                win += (long) e.getValue() * payoutMult(e.getKey());
                winSpots.add(e.getKey());
            }
        }
        balance += win;
        lastWin = win;
        bets.clear();
        placeOrder.clear();
        if (win > 0) {
            winFlashUntil = System.currentTimeMillis() + 2200;
            startParticles();
            GameSounds.play(win >= 10 * tb ? GameSounds.BIG_WIN : GameSounds.WIN);
        } else {
            GameSounds.play(getContext(), GameSounds.LOSE);
        }
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            long reward = 150L * level;
            balance += reward;
            levelUpPending = level; levelUpReward += reward;
            if (listener != null) listener.onLevelUp(level, reward);
        }
        settledAt = System.currentTimeMillis();
        persist();
        if (listener != null) {
            if (win >= 25 * tb) listener.onBigWin(win, tb);
            listener.onSpinComplete((int) spinsDone, win, tb);
        }
        invalidate();
    }


    /** Coins burst from center and fly to the balance pill (e.g. daily claim). */
    public void coinBurst() {
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 14; i++) {
            cbx[i] = 0.5f + (r.nextFloat() - 0.5f) * 0.12f;
            cby[i] = 0.45f + (r.nextFloat() - 0.5f) * 0.08f;
            cbvx[i] = (r.nextFloat() - 0.5f) * 0.08f;
            cbvy[i] = (r.nextFloat() - 0.5f) * 0.08f;
        }
        coinBurstOn = true;
        coinBurstAt = System.currentTimeMillis();
        invalidate();
    }

    private void drawCoinBurst(Canvas cv, float w, float h, long now) {
        if (!coinBurstOn) return;
        float t = (now - coinBurstAt) / 750f;
        if (t >= 1f) { coinBurstOn = false; return; }
        for (int i = 0; i < 14; i++) {
            float local = Math.min(1f, Math.max(0f, t * 1.25f - i * 0.015f));
            float e = local * local;
            float x = (cbx[i] + cbvx[i] * local + (0.5f - cbx[i]) * e) * w;
            float y = (cby[i] + cbvy[i] * local + (0.045f - cby[i]) * e) * h;
            float rad = w * 0.014f * (1f - 0.4f * local);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54F);
            cv.drawCircle(x, y, rad, p);
            p.setColor(0xFFB8860B);
            cv.drawCircle(x, y, rad * 0.55f, p);
        }
        postInvalidateDelayed(28);
    }

    private void startParticles() {
        particlesOn = true;
        particlesAt = System.currentTimeMillis();
        for (int i = 0; i < 18; i++) {
            px[i] = 0.5f; py[i] = 0.30f;
            pvx[i] = (rng.nextFloat() - 0.5f) * 0.0022f;
            pvy[i] = -(0.0008f + rng.nextFloat() * 0.0022f);
        }
    }

    // ---------------------------------------------------------------- draw

    @Override protected void onDraw(Canvas cv) {
        long now = System.currentTimeMillis();
        regen();
        animating = spinning;
        float w = getWidth(), h = getHeight();

        // felt
        p.setShader(new RadialGradient(w / 2, h * 0.36f, h * 0.55f, 0xFF1B6A45, 0xFF06281A, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        drawHud(cv, w, h, now);
        drawWheel(cv, w, h, now);
        drawResult(cv, w, h, now);
        drawTable(cv, w, h, now);
        drawControls(cv, w, h, now);
        drawParticles(cv, w, h, now);
        drawCoinBurst(cv, w, h, now);

        // ---- level-up celebration overlay ----
        if (levelUpPending > 0 && !levelUpShowing && now - settledAt > 1400) {
            levelUpShowing = true; levelUpAt = now;
            GameSounds.play(getContext(), GameSounds.LEVEL_UP);
        }
        if (levelUpShowing) {
            animating = true;
            float t = Math.min(1f, (now - levelUpAt) / 420f);
            cv.drawColor(0xB3000000);
            float sc = easeOutBack(t);
            cv.save();
            cv.scale(sc, sc, w / 2f, h * 0.42f);
            float badgeR = w * 0.15f;
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD700);
            cv.drawCircle(w / 2f, h * 0.36f, badgeR, p);
            p.setColor(0xFFB8860B); p.setTextSize(badgeR * 0.9f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText("" + levelUpPending, w / 2f, h * 0.36f + badgeR * 0.32f, p);
            p.setFakeBoldText(false);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w * 0.012f); p.setColor(0x55FFD700);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI / 6 * i + now / 900.0;
                cv.drawLine(w / 2f + (float) Math.cos(a) * badgeR * 1.25f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.25f,
                        w / 2f + (float) Math.cos(a) * badgeR * 1.6f, h * 0.36f + (float) Math.sin(a) * badgeR * 1.6f, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(h * 0.045f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setShadowLayer(h * 0.008f, 0, h * 0.004f, 0xFF8E0000);
            p.setColor(0xFFFFE082);
            cv.drawText("LEVEL UP!", w / 2f, h * 0.53f, p);
            p.clearShadowLayer(); p.setFakeBoldText(false);
            p.setColor(0xFFFFFFFF); p.setTextSize(h * 0.022f);
            cv.drawText("Reward: +" + levelUpReward + " coins", w / 2f, h * 0.575f, p);
            cv.restore();
            if (t >= 1f)
                drawPillButton(cv, w * 0.3f, h * 0.62f, w * 0.4f, h * 0.052f, "COLLECT", 0xFF43A047, 0xFF1B5E20, true, false, 0);
        }

        if (animating) postInvalidateDelayed(32);
    }

    private void drawHud(Canvas cv, float w, float h, long now) {
        // level badge
        float lvR = w * 0.055f, lvX = w * 0.10f, lvY = h * 0.042f;
        p.setColor(0xFF0D47A1); cv.drawCircle(lvX, lvY, lvR, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(lvR * 0.18f); p.setColor(0x33FFFFFF);
        cv.drawCircle(lvX, lvY, lvR * 0.82f, p);
        float frac = Math.min(1f, xp / (float) xpNeeded());
        p.setStrokeWidth(lvR * 0.16f); p.setColor(0xFFFFD700);
        cv.drawArc(new RectF(lvX - lvR, lvY - lvR, lvX + lvR, lvY + lvR), -90, frac * 360, false, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF); p.setTextSize(lvR * 0.72f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText("" + level, lvX, lvY + lvR * 0.26f, p);
        p.setFakeBoldText(false);
        p.setTextSize(h * 0.011f); p.setColor(0x99FFFFFF);
        cv.drawText("LEVEL", lvX, lvY + lvR * 1.5f, p);
        // sound toggle
        float spX = w * 0.93f, spY = h * 0.042f, spR = w * 0.045f;
        p.setColor(0x66000000);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x55FFFFFF);
        cv.drawCircle(spX, spY, spR, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(spR * 0.9f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(GameSounds.isEnabled(getContext()) ? "\uD83D\uDD0A" : "\uD83D\uDD07", spX, spY + spR * 0.32f, p);


        // coin pill
        float pillW = w * 0.34f, pillH = h * 0.034f, pillX = w * 0.52f, pillY = h * 0.026f;
        rf.set(pillX, pillY, pillX + pillW, pillY + pillH);
        p.setColor(0xCC000000);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x66FFD700);
        cv.drawRoundRect(rf, pillH / 2, pillH / 2, p);
        p.setStyle(Paint.Style.FILL);
        float coinR = pillH * 0.36f, coinX = pillX + pillH * 0.55f, coinY = pillY + pillH / 2f;
        p.setColor(0xFFFFD700); cv.drawCircle(coinX, coinY, coinR, p);
        p.setColor(0xFFB8860B); p.setTextSize(coinR * 1.2f); p.setFakeBoldText(true);
        cv.drawText("$", coinX, coinY + coinR * 0.42f, p);
        p.setFakeBoldText(false);
        if (shownBalance != balance) {
            long diff = balance - shownBalance;
            long step = Math.max(1, Math.abs(diff) / 6);
            shownBalance += diff > 0 ? step : -step;
            if ((diff > 0 && shownBalance > balance) || (diff < 0 && shownBalance < balance)) shownBalance = balance;
            animating = true;
        }
        p.setColor(0xFFFFFFFF); p.setTextSize(pillH * 0.52f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText(String.format("%,d", shownBalance), pillX + pillW * 0.58f, pillY + pillH * 0.68f, p);
        p.setFakeBoldText(false);
        if (balance < 100) {
            long nextIn = 60000 - (now - lastRegenAt) % 60000;
            p.setTextSize(h * 0.011f); p.setColor(0x99FFFFFF);
            cv.drawText("+5 free in " + (nextIn / 1000 + 1) + "s", pillX + pillW / 2f, pillY + pillH + h * 0.016f, p);
            animating = true;
        }
    }

    private void drawWheel(Canvas cv, float w, float h, long now) {
        float wheelCy = h * 0.236f, wheelR = w * 0.30f;
        {   // short screens: shrink the wheel so the info band and balance pill stay clear
            float maxBottom = row0Y(h) - rowH(h) * 1.08f - h * 0.032f;
            if (wheelCy + wheelR * 1.11f > maxBottom)
                wheelR = Math.max(h * 0.10f, (maxBottom - wheelCy) / 1.11f);
        }
        if (spinning) {
            float t = Math.min(1f, (now - spinStart) / (float) SPIN_MS);
            float ease = 1f - (1f - t) * (1f - t) * (1f - t);
            wheelAngle = startAngle + (targetAngle - startAngle) * ease;
            // ball: fast opposite spin, eases out, radius shrinks into pocket ring at the end
            float be = 1f - (1f - t) * (1f - t) * (1f - t) * (1f - t);
            ballAngle = ballStart + (ballTarget - ballStart) * be;
            if (t >= 1f) post(this::finishSpin);
        }

        // wooden rim
        p.setColor(0xFF4E2A14);
        cv.drawCircle(w / 2, wheelCy, wheelR * 1.11f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(wheelR * 0.05f); p.setColor(0xFF6B3A1D);
        cv.drawCircle(w / 2, wheelCy, wheelR * 1.08f, p);
        p.setStyle(Paint.Style.FILL);

        // pockets
        float seg = 360f / 37f;
        for (int i = 0; i < 37; i++) {
            int n = WHEEL[i];
            p.setColor(n == 0 ? 0xFF0E7C46 : (isRed(n) ? 0xFFC62828 : 0xFF1B1B1B));
            rf.set(w / 2 - wheelR, wheelCy - wheelR, w / 2 + wheelR, wheelCy + wheelR);
            cv.drawArc(rf, wheelAngle + i * seg - 90, seg, true, p);
        }
        // gold trims + turret
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(wheelR * 0.03f); p.setColor(0xFFFFD700);
        cv.drawCircle(w / 2, wheelCy, wheelR, p);
        cv.drawCircle(w / 2, wheelCy, wheelR * 0.66f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF123D2B);
        cv.drawCircle(w / 2, wheelCy, wheelR * 0.63f, p);
        p.setShader(new RadialGradient(w / 2, wheelCy, wheelR * 0.3f, 0xFF8A8A8A, 0xFF3A3A3A, Shader.TileMode.CLAMP));
        cv.drawCircle(w / 2, wheelCy, wheelR * 0.16f, p);
        p.setShader(null);
        // cross turret
        p.setColor(0xFF9E9E9E);
        cv.drawRect(w / 2 - wheelR * 0.03f, wheelCy - wheelR * 0.30f, w / 2 + wheelR * 0.03f, wheelCy + wheelR * 0.30f, p);
        cv.drawRect(w / 2 - wheelR * 0.30f, wheelCy - wheelR * 0.03f, w / 2 + wheelR * 0.30f, wheelCy + wheelR * 0.03f, p);

        // pocket numbers
        p.setTextSize(wheelR * 0.115f);
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 37; i++) {
            double a = Math.toRadians(wheelAngle + (i + 0.5f) * seg - 90);
            float tx = w / 2 + (float) Math.cos(a) * wheelR * 0.84f;
            float ty = wheelCy + (float) Math.sin(a) * wheelR * 0.84f - (p.descent() + p.ascent()) / 2;
            p.setColor(0xFFFFFFFF);
            cv.drawText(String.valueOf(WHEEL[i]), tx, ty, p);
        }

        // ball
        if (spinning || result >= 0) {
            float t = spinning ? Math.min(1f, (now - spinStart) / (float) SPIN_MS) : 1f;
            float br = wheelR * (t < 0.7f ? 1.02f : (1.02f - 0.18f * (t - 0.7f) / 0.3f));
            // small bounce as it drops
            if (t > 0.85f && t < 1f) br += (float) Math.sin((t - 0.85f) / 0.15f * Math.PI * 3) * wheelR * 0.02f;
            double a = Math.toRadians(spinning ? ballAngle : -90);
            float bx = w / 2 + (float) Math.cos(a) * br;
            float by = wheelCy + (float) Math.sin(a) * br;
            p.setColor(0x55000000);
            cv.drawCircle(bx + 2, by + 3, wheelR * 0.055f, p);
            p.setShader(new RadialGradient(bx - wheelR * 0.02f, by - wheelR * 0.02f, wheelR * 0.06f, 0xFFFFFFFF, 0xFFB0BEC5, Shader.TileMode.CLAMP));
            cv.drawCircle(bx, by, wheelR * 0.055f, p);
            p.setShader(null);
        }
    }

    private void drawResult(Canvas cv, float w, float h, long now) {
        if (result >= 0 && !spinning) {
            String label = result + (result == 0 ? "" : (isRed(result) ? " RED" : " BLACK"));
            int col = result == 0 ? 0xFF2E8B57 : (isRed(result) ? 0xFFFF5252 : 0xFFB0BEC5);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.016f); p.setColor(0x99FFFFFF);
            cv.drawText("LAST RESULT", w / 2, h * 0.404f, p);
            p.setTextSize(h * 0.021f); p.setFakeBoldText(true); p.setColor(col);
            cv.drawText(label, w / 2, h * 0.424f, p);
            p.setFakeBoldText(false);
        } else if (!spinning) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.015f); p.setColor(0x99FFFFFF);
            cv.drawText("PLACE YOUR BETS ON THE TABLE", w / 2, h * 0.418f, p);
        }
        // win banner
        if (lastWin > 0 && now < winFlashUntil && !spinning) {
            float t = 1f - (winFlashUntil - now) / 2200f;
            float scale = t < 0.2f ? 0.5f + 2.5f * t : 1f;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.034f * scale);
            p.setFakeBoldText(true);
            String winTxt = "WIN +" + String.format("%,d", lastWin);
            float tw = p.measureText(winTxt);
            float cy = h * 0.236f + h * 0.012f; // wheel center; the band below already carries LAST RESULT
            float pad = h * 0.012f;
            p.setColor(0xB0000000);
            cv.drawRoundRect(w / 2 - tw / 2 - pad, cy - h * 0.034f * scale, w / 2 + tw / 2 + pad, cy + h * 0.010f, 18, 18, p);
            p.setColor(0xFF7CFC00);
            p.setShadowLayer(12, 0, 0, 0xAA7CFC00);
            cv.drawText(winTxt, w / 2, cy, p);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
            animating = true;
        }
    }

    // table geometry
    private float tblX(float w) { return w * 0.04f; }
    private float tblW(float w) { return w * 0.78f; }       // numbers grid width (12 cols)
    private float colW(float w) { return tblW(w) / 12f; }
    private float row0Y(float h) { return h * 0.470f; }     // first number row top
    private float rowH(float h) { return h * 0.038f; }

    private void drawTable(Canvas cv, float w, float h, long now) {
        float x0 = tblX(w), cw = colW(w), rh = rowH(h), y0 = row0Y(h);

        // zero: full-width bar above the grid
        rf.set(x0, y0 - rh * 1.08f, x0 + tblW(w) + w * 0.14f, y0 - rh * 0.08f);
        p.setColor(spotBg("N0", now));
        cv.drawRoundRect(rf, 8, 8, p);
        cellStroke(cv, "N0");
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(rh * 0.66f);
        p.setColor(0xFFFFFFFF); p.setFakeBoldText(true);
        p.setShadowLayer(4, 0, 2, 0x66000000);
        cv.drawText("0", rf.centerX(), rf.centerY() + rh * 0.24f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
        drawStakedChips(cv, "N0", rf.left, rf.top, rf.width(), rh);

        // numbers 1-36: top row is 3,6,...,36 ; bottom row 1,4,...,34
        for (int n = 1; n <= 36; n++) {
            int col = (n - 1) / 3;
            int row = 2 - (n - 1) % 3;         // 0 top .. 2 bottom
            float cx0 = x0 + col * cw, cy0 = y0 + row * rh;
            rf.set(cx0 + 2, cy0 + 2, cx0 + cw - 2, cy0 + rh - 2);
            int base = spotBg("N" + n, now);
            p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, lighten(base, 1.30f), base, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rf, 7, 7, p);
            p.setShader(null);
            cellStroke(cv, "N" + n);
            p.setColor(0xFFFFFFFF); p.setTextSize(rh * 0.55f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setShadowLayer(3, 0, 2, 0x77000000);
            cv.drawText(String.valueOf(n), rf.centerX(), rf.centerY() + rh * 0.20f, p);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
            drawStakedChips(cv, "N" + n, rf.left, rf.top, cw, rh);
        }

        // column bets 2:1 (right strip, one per row)
        for (int r = 0; r < 3; r++) {
            String spot = "C" + (3 - r);        // top row numbers %3==0 -> C3, bottom %3==1 -> C1
            rf.set(x0 + tblW(w) + 3, y0 + r * rh + 2, x0 + tblW(w) + w * 0.14f - 3, y0 + (r + 1) * rh - 2);
            int cb = spotBg(spot, now);
            p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, lighten(cb, 1.30f), cb, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rf, 7, 7, p);
            p.setShader(null);
            cellStroke(cv, spot);
            p.setColor(0xFFFFD700); p.setTextSize(rh * 0.40f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setShadowLayer(3, 0, 1, 0x66000000);
            cv.drawText("2:1", rf.centerX(), rf.centerY() + rh * 0.14f, p);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
            drawStakedChips(cv, spot, rf.left, rf.top, rf.width(), rh);
        }

        // dozens row
        String[] dLabels = {"1st 12", "2nd 12", "3rd 12"};
        float dzY = y0 + 3 * rh + 2;
        for (int d = 1; d <= 3; d++) {
            String spot = "D" + d;
            rf.set(x0 + (d - 1) * cw * 4 + 1, dzY, x0 + d * cw * 4 - 1, dzY + rh * 0.92f);
            p.setColor(spotBg(spot, now));
            cv.drawRoundRect(rf, 6, 6, p);
            cellStroke(cv, spot);
            p.setColor(0xFFFFFFFF); p.setTextSize(rh * 0.42f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText(dLabels[d - 1], rf.centerX(), rf.centerY() + rh * 0.15f, p);
            p.setFakeBoldText(false);
            drawStakedChips(cv, spot, rf.left, rf.top, rf.width(), rh);
        }

        // even chances row
        String[] spots = {"LOW", "EVEN", "RED", "BLACK", "ODD", "HIGH"};
        String[] labels = {"1-18", "EVEN", "RED", "BLACK", "ODD", "19-36"};
        float ecY = dzY + rh * 0.98f;
        float ecW = tblW(w) / 6f;
        for (int i = 0; i < 6; i++) {
            rf.set(x0 + i * ecW + 1, ecY, x0 + (i + 1) * ecW - 1, ecY + rh * 0.98f);
            p.setColor(spotBg(spots[i], now));
            cv.drawRoundRect(rf, 6, 6, p);
            cellStroke(cv, spots[i]);
            p.setTextAlign(Paint.Align.CENTER);
            if (spots[i].equals("RED") || spots[i].equals("BLACK")) {
                int col = spots[i].equals("RED") ? 0xFFFF5252 : 0xFF263238;
                p.setColor(col);
                float dx = rf.centerX(), dy = rf.centerY() - rh * 0.10f, dr = rh * 0.17f;
                Path dm = new Path();
                dm.moveTo(dx, dy - dr); dm.lineTo(dx + dr, dy); dm.lineTo(dx, dy + dr); dm.lineTo(dx - dr, dy);
                dm.close();
                if (spots[i].equals("BLACK")) { p.setStyle(Paint.Style.FILL); cv.drawPath(dm, p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xFFB0BEC5); cv.drawPath(dm, p); p.setStyle(Paint.Style.FILL); }
                else cv.drawPath(dm, p);
            } else {
                p.setColor(0xFFFFFFFF); p.setTextSize(rh * 0.34f);
                cv.drawText(labels[i], rf.centerX(), rf.centerY() - rh * 0.02f, p);
            }
            p.setColor(0x99FFFFFF); p.setTextSize(rh * 0.24f);
            cv.drawText("1:1", rf.centerX(), rf.centerY() + rh * 0.30f, p);
            drawStakedChips(cv, spots[i], rf.left, rf.top, rf.width(), rh);
        }
    }

    private int spotBg(String spot, long now) {
        if (winSpots.contains(spot) && now < winFlashUntil && !spinning) {
            return ((now / 200) % 2 == 0) ? 0xFFFFD54F : 0xFFFFF176;   // flashing gold for winners
        }
        if (spot.equals("N0")) return 0xCC0E7C46;
        if (spot.startsWith("N")) {
            int n = Integer.parseInt(spot.substring(1));
            return isRed(n) ? 0xCCB71C1C : 0xCC212121;
        }
        if (spot.equals("RED")) return 0xCCB71C1C;
        if (spot.equals("BLACK")) return 0xCC212121;
        return 0xCC14503C;
    }

    private void cellStroke(Canvas cv, String spot) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0x66FFFFFF);
        cv.drawRoundRect(rf, 7, 7, p);
        p.setStyle(Paint.Style.FILL);
        if (bets.containsKey(spot)) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.5f); p.setColor(0xFFFFD700);
            cv.drawRoundRect(rf, 7, 7, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private int lighten(int color, float f) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] * f);
        return android.graphics.Color.HSVToColor(hsv);
    }

    private void drawStakedChips(Canvas cv, String spot, float left, float top, float cw, float cellH) {
        Integer amt = bets.get(spot);
        if (amt == null || amt <= 0) return;
        float r = cellH * 0.24f;
        float cx, cy;
        if (spot.startsWith("N") && !spot.equals("N0")) {
            cx = left + cw * 0.5f; cy = top + cellH * 0.28f;      // above the number
        } else if (spot.equals("RED") || spot.equals("BLACK") || spot.equals("EVEN") || spot.equals("ODD")
                || spot.equals("LOW") || spot.equals("HIGH")) {
            cx = left + cw * 0.74f; cy = top + cellH * 0.50f;     // right of the label
        } else {
            cx = left + cw * 0.5f; cy = top + cellH * 0.74f;      // below dozen/column/zero labels
        }
        List<Integer> stack = new ArrayList<>();
        int rem = amt;
        for (int c = BETS.length - 1; c >= 0 && stack.size() < 3; c--)
            while (rem >= BETS[c] && stack.size() < 3) { rem -= BETS[c]; stack.add(c); }
        for (int i = stack.size() - 1; i >= 0; i--) {
            boolean topChip = i == 0;
            float yy = cy - (stack.size() - 1 - i) * r * 0.40f;
            drawChip(cv, cx, yy, r, CHIP_COLORS[stack.get(i)], topChip ? String.valueOf(amt) : "", false);
        }
    }

    private void drawControls(Canvas cv, float w, float h, long now) {
        // chip selector
        float chipR = w * 0.062f, chipGap = w * 0.045f;
        float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
        float cx0 = w / 2f - rowW / 2f + chipR;
        float cy = h * 0.72f;
        float tableBottom = row0Y(h) + rowH(h) * 5f;
        float chipTop = cy - chipR * 1.165f - h * 0.006f; // selected chip sits raised, gold ring at r*1.12+stroke
        float hintY = (tableBottom + chipTop) / 2f + h * 0.004f; // midway: clears both on short screens
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h * 0.012f); p.setColor(0x99FFFFFF);
        cv.drawText(totalBet() > 0 ? "TOTAL BET " + totalBet() : "TAP A CHIP, THEN TAP THE TABLE", w / 2, hintY, p);
        for (int i = 0; i < BETS.length; i++) {
            boolean sel = i == betIdx;
            drawChip(cv, cx0 + i * (chipR * 2 + chipGap), cy + (sel ? -h * 0.006f : 0), chipR, CHIP_COLORS[i], "" + BETS[i], sel);
        }
        // MAX BET shortcut
        float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
        float bump = now - betBumpAt < 250 ? 1f + 0.25f * (1f - (now - betBumpAt) / 250f) : 1f;
        if (now - betBumpAt < 250) postInvalidateDelayed(28);
        p.setColor(0xFFFFD54F);
        cv.drawCircle(mx, cy, chipR * 0.8f * bump, p);
        p.setColor(0xFF6D4C00); p.setTextSize(chipR * 0.4f * bump); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        cv.drawText("MAX", mx, cy + chipR * 0.14f * bump, p);
        p.setFakeBoldText(false);


        // buttons row: CLEAR | UNDO | SPIN
        float bh = h * 0.052f, by = h * 0.762f;
        boolean idle = !spinning;
        drawPillButton(cv, w * 0.05f, by, w * 0.22f, bh, "CLEAR", 0xFF78909C, 0xFF37474F, idle && !bets.isEmpty(), pressedBtn == 1, 0);
        drawPillButton(cv, w * 0.31f, by, w * 0.22f, bh, "UNDO", 0xFF78909C, 0xFF37474F, idle && !placeOrder.isEmpty(), pressedBtn == 2, 0);
        boolean canSpin = idle && totalBet() > 0;
        float pulse = (canSpin && now - lastTouchAt > 3000) ? (float) (0.5 + 0.5 * Math.sin(now / 450.0)) : 0f;
        if (pulse > 0) animating = true;
        drawPillButton(cv, w * 0.57f, by, w * 0.38f, bh, spinning ? "..." : "SPIN  \u25B6", 0xFFE53935, 0xFF8E0000, canSpin, pressedBtn == 0, pulse);

        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h * 0.0105f); p.setColor(0x99FFFFFF);
        cv.drawText("Straight 35:1  -  Dozen/Column 2:1  -  Even chances 1:1", w / 2, by + bh + h * 0.02f, p);
        cv.drawText("Free play only. Virtual coins - no real money, no prizes.", w / 2, by + bh + h * 0.04f, p);
    }

    private void drawParticles(Canvas cv, float w, float h, long now) {
        if (!particlesOn) return;
        float t = (now - particlesAt) / 1000f;
        if (t > 1.6f) { particlesOn = false; return; }
        animating = true;
        for (int i = 0; i < 18; i++) {
            float x = (px[i] + pvx[i] * t * 60) * w;
            float y = (py[i] + pvy[i] * t * 60 + 0.0011f * t * t * 3600) * h;
            if (y > h) continue;
            drawChip(cv, x, y, w * 0.016f, CHIP_COLORS[i % 4], "", false);
        }
    }

    private void drawChip(Canvas cv, float cx, float cy, float r, int color, String label, boolean selected) {
        p.setStyle(Paint.Style.FILL); p.setColor(0x66000000);
        cv.drawCircle(cx + 2, cy + 4, r, p);
        p.setColor(color);
        cv.drawCircle(cx, cy, r, p);
        p.setColor(0xFFF5F5F5);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i + Math.PI / 6;
            float sx = cx + (float) Math.cos(a) * r * 0.86f;
            float sy = cy + (float) Math.sin(a) * r * 0.86f;
            float ex = cx + (float) Math.cos(a) * r * 1.0f;
            float ey = cy + (float) Math.sin(a) * r * 1.0f;
            p.setStrokeWidth(r * 0.16f);
            cv.drawLine(sx, sy, ex, ey, p);
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.06f); p.setColor(0xCCFFFFFF);
        cv.drawCircle(cx, cy, r * 0.66f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF5F5F5); cv.drawCircle(cx, cy, r * 0.55f, p);
        if (!label.isEmpty()) {
            p.setColor(0xFF202020); p.setTextSize(r * 0.52f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            cv.drawText(label, cx, cy + r * 0.19f, p);
            p.setFakeBoldText(false);
        }
        if (selected) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.09f); p.setColor(0xFFFFD700);
            cv.drawCircle(cx, cy, r * 1.12f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private float easeOutBack(float t) { float c = 1.70158f; t -= 1f; return 1f + (c + 1f) * t * t * t + c * t * t; }

    private void drawPillButton(Canvas cv, float x, float y, float w, float h, String label,
                                int bgTop, int bgBottom, boolean enabled, boolean pressed, float pulse) {
        float sc = pressed ? 0.94f : 1f;
        float cx = x + w / 2f, cy = y + h / 2f;
        float dw = w * sc, dh = h * sc;
        rf.set(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2);
        if (pulse > 0f && enabled) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h * 0.06f);
            p.setColor(0x44FFD700);
            float grow = pulse * h * 0.22f;
            RectF glow = new RectF(rf.left - grow, rf.top - grow, rf.right + grow, rf.bottom + grow);
            cv.drawRoundRect(glow, dh * 0.5f + grow, dh * 0.5f + grow, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (enabled) {
            p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, bgTop, bgBottom, Shader.TileMode.CLAMP));
        } else {
            p.setShader(null); p.setColor(0xFF37474F);
        }
        cv.drawRoundRect(rf, dh * 0.5f, dh * 0.5f, p);
        p.setShader(null);
        if (enabled) {
            p.setColor(0x33FFFFFF);
            rf.set(cx - dw / 2 + 6, cy - dh / 2 + 4, cx + dw / 2 - 6, cy - dh * 0.18f);
            cv.drawRoundRect(rf, dh * 0.4f, dh * 0.4f, p);
        }
        p.setColor(enabled ? 0xFFFFFFFF : 0xFF90A4AE);
        p.setTextSize(dh * 0.40f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        p.setShadowLayer(3, 0, 2, 0x66000000);
        cv.drawText(label, cx, cy + dh * 0.15f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    // ---------------------------------------------------------------- touch

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) { lastTouchAt = System.currentTimeMillis(); return true; }
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth(), h = getHeight();
        float x = e.getX(), y = e.getY();
        if (Math.hypot(x - w * 0.93f, y - getHeight() * 0.042f) <= w * 0.06f) {
            GameSounds.toggle(getContext());
            invalidate();
            return true;
        }

        if (levelUpShowing) {
            if (x >= w * 0.3f && x <= w * 0.7f && y >= h * 0.62f && y <= h * 0.62f + h * 0.052f) {
                levelUpShowing = false; levelUpPending = 0; levelUpReward = 0;
                GameSounds.play(getContext(), GameSounds.CLICK);
                invalidate();
            }
            return true;
        }

        pressedBtn = -1;

        // buttons
        float bh = h * 0.052f, by = h * 0.762f;
        if (y >= by && y <= by + bh && !spinning) {
            if (x >= w * 0.05f && x <= w * 0.27f) { clearBets(); return true; }
            if (x >= w * 0.31f && x <= w * 0.53f) { undoBet(); return true; }
            if (x >= w * 0.57f && x <= w * 0.95f) { spin(); return true; }
        }
        // chip selector
        float chipR = w * 0.062f, chipGap = w * 0.045f;
        float rowW = BETS.length * chipR * 2 + (BETS.length - 1) * chipGap;
        float cx0 = w / 2f - rowW / 2f + chipR;
        float cy = h * 0.72f;
        float mx = w / 2f + rowW / 2f + chipGap * 0.6f + chipR * 0.8f;
        if (Math.hypot(x - mx, y - cy) <= chipR * 1.2f) { maxBet(); return true; }
        for (int i = 0; i < BETS.length; i++) {
            float cx = cx0 + i * (chipR * 2 + chipGap);
            if (Math.hypot(x - cx, y - cy) <= chipR * 1.3f) { betIdx = i; GameSounds.play(getContext(), GameSounds.CHIP); persist(); invalidate(); return true; }
        }
        if (spinning) return true;

        // betting table
        String spot = spotAt(x, y, w, h);
        if (spot != null) { placeBet(spot); return true; }
        return true;
    }


    private void maxBet() {
        for (int i = BETS.length - 1; i >= 0; i--) {
            if (balance >= BETS[i]) {
                if (i != betIdx) {
                    betIdx = i;
                    GameSounds.play(getContext(), GameSounds.CHIP);
                    betBumpAt = System.currentTimeMillis();
                }
                invalidate();
                persist();
                return;
            }
        }
    }

    private String spotAt(float x, float y, float w, float h) {
        float x0 = tblX(w), cw = colW(w), rh = rowH(h), y0 = row0Y(h);
        // zero bar
        if (y >= y0 - rh * 1.08f && y <= y0 - rh * 0.08f && x >= x0 && x <= x0 + tblW(w) + w * 0.14f) return "N0";
        // numbers + columns
        if (y >= y0 && y <= y0 + 3 * rh) {
            int row = (int) ((y - y0) / rh);
            if (x >= x0 + tblW(w) && x <= x0 + tblW(w) + w * 0.14f) return "C" + (3 - row);
            if (x >= x0 && x < x0 + tblW(w)) {
                int col = (int) ((x - x0) / cw);
                if (col >= 0 && col < 12) return "N" + (col * 3 + (3 - row));
            }
            return null;
        }
        // dozens
        float dzY = y0 + 3 * rh + 2;
        if (y >= dzY && y <= dzY + rh * 0.92f && x >= x0 && x <= x0 + tblW(w)) {
            int d = (int) ((x - x0) / (cw * 4)) + 1;
            if (d >= 1 && d <= 3) return "D" + d;
            return null;
        }
        // even chances
        float ecY = dzY + rh * 0.98f;
        String[] spots = {"LOW", "EVEN", "RED", "BLACK", "ODD", "HIGH"};
        if (y >= ecY && y <= ecY + rh * 0.98f && x >= x0 && x <= x0 + tblW(w)) {
            int i = (int) ((x - x0) / (tblW(w) / 6f));
            if (i >= 0 && i < 6) return spots[i];
        }
        return null;
    }
}
