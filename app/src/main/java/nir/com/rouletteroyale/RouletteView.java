package nir.com.rouletteroyale;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.Random;

/** Roulette Royale - free social roulette. Virtual coins only, no real money, no prizes. */
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
    private static final String[] TYPES = {"RED", "BLACK", "EVEN", "ODD", "1-18", "19-36", "NUM"};

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private long balance = 500;
    private long lastWin = 0;
    private int betIdx = 1;                    // 10
    private int typeIdx = 0;                   // RED
    private int straightNum = 7;               // lucky number for NUM bets
    private long spinsDone = 0;
    private long xp = 0;
    private int level = 1;
    private long levelFlashUntil = 0;
    private long lastRegenAt = System.currentTimeMillis();

    private boolean spinning = false;
    private long spinStart = 0;
    private static final long SPIN_MS = 2600;
    private int result = -1;
    private float wheelAngle = 0f, startAngle = 0f, targetAngle = 0f;
    private long winFlashUntil = 0;
    private float titleGlow = 0f;

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
            long steps = (now - lastRegenAt) / 60000;
            balance = Math.min(100, balance + 5 * steps);
            lastRegenAt = now;
            persist();
        }
    }

    private void spin() {
        if (spinning) return;
        regen();
        long bet = BETS[betIdx];
        if (balance < bet) { if (listener != null) listener.onOutOfCoins(); return; }
        balance -= bet;
        xp += bet;
        lastWin = 0;
        result = rng.nextInt(37);
        spinning = true;
        spinStart = System.currentTimeMillis();
        startAngle = wheelAngle;
        // land so that result pocket sits at top; add 5-7 full turns
        int pocket = 0;
        for (int i = 0; i < 37; i++) if (WHEEL[i] == result) pocket = i;
        float pocketAngle = 360f - (pocket * (360f / 37f));
        targetAngle = startAngle + 360f * (5 + rng.nextInt(3)) + ((pocketAngle - startAngle) % 360f + 360f) % 360f;
        persist();
        invalidate();
    }

    private void finishSpin() {
        spinning = false;
        spinsDone++;
        wheelAngle = targetAngle % 360f;
        long bet = BETS[betIdx];
        long win = 0;
        boolean hit = false;
        switch (TYPES[typeIdx]) {
            case "RED": hit = isRed(result); break;
            case "BLACK": hit = result != 0 && !isRed(result); break;
            case "EVEN": hit = result != 0 && result % 2 == 0; break;
            case "ODD": hit = result % 2 == 1; break;
            case "1-18": hit = result >= 1 && result <= 18; break;
            case "19-36": hit = result >= 19; break;
            case "NUM": hit = result == straightNum; break;
        }
        if (hit) {
            win = TYPES[typeIdx].equals("NUM") ? bet * 36 : bet * 2;
            balance += win;
            lastWin = win;
            winFlashUntil = System.currentTimeMillis() + 1800;
        }
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            long reward = 150L * level;
            balance += reward;
            levelFlashUntil = System.currentTimeMillis() + 2000;
            if (listener != null) listener.onLevelUp(level, reward);
        }
        persist();
        if (listener != null) {
            if (win >= 25 * bet) listener.onBigWin(win, bet);
            listener.onSpinComplete((int) spinsDone, win, bet);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        long now = System.currentTimeMillis();
        regen();
        float w = getWidth();
        p.setShader(new LinearGradient(0, 0, 0, getHeight(), 0xFF0B3D2E, 0xFF04150F, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, getHeight(), p);
        p.setShader(null);

        float wheelCy = getHeight() * 0.42f;
        float wheelR = w * 0.40f;

        // title
        titleGlow = (titleGlow + 0.03f) % 6.28f;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(w * 0.075f);
        p.setFakeBoldText(true);
        p.setColor(0xFFFFD700);
        p.setShadowLayer(14 + 6 * (float) Math.sin(titleGlow), 0, 0, 0xFFFF9D00);
        cv.drawText("ROULETTE ROYALE", w / 2, getHeight() * 0.09f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);

        // balance / last result
        p.setTextSize(w * 0.05f);
        p.setColor(0xFFFFFFFF);
        cv.drawText("COINS  " + balance, w / 2, getHeight() * 0.135f, p);
        // level + xp bar
        p.setTextSize(w * 0.034f);
        p.setColor(0xFFA8CBB8);
        cv.drawText("LV " + level, w * 0.14f, getHeight() * 0.172f, p);
        float barL = w * 0.24f, barR = w * 0.76f, barY = getHeight() * 0.158f, barH = w * 0.022f;
        rf.set(barL, barY, barR, barY + barH);
        p.setColor(0xFF12503C); cv.drawRoundRect(rf, 8, 8, p);
        float frac = Math.min(1f, xp / (float) xpNeeded());
        rf.set(barL, barY, barL + (barR - barL) * frac, barY + barH);
        p.setColor(0xFFFFD700); cv.drawRoundRect(rf, 8, 8, p);
        if (now < levelFlashUntil) {
            p.setTextSize(w * 0.055f);
            p.setColor(0xFFFFD700);
            p.setFakeBoldText(true);
            cv.drawText("LEVEL UP!", w / 2, getHeight() * 0.21f, p);
            p.setFakeBoldText(false);
        }
        if (result >= 0 && !spinning) {
            p.setColor(result == 0 ? 0xFF2E8B57 : (isRed(result) ? 0xFFFF5252 : 0xFFB0BEC5));
            p.setFakeBoldText(true);
            cv.drawText("LAST  " + result + (isRed(result) ? " RED" : (result == 0 ? " ZERO" : " BLACK")), w / 2, getHeight() * 0.19f, p);
            p.setFakeBoldText(false);
        }

        if (spinning) {
            float t = Math.min(1f, (now - spinStart) / (float) SPIN_MS);
            float ease = 1f - (1f - t) * (1f - t) * (1f - t);
            wheelAngle = startAngle + (targetAngle - startAngle) * ease;
            if (t >= 1f) post(this::finishSpin);
            else postInvalidateDelayed(30);
        }

        // wheel
        float seg = 360f / 37f;
        for (int i = 0; i < 37; i++) {
            int n = WHEEL[i];
            p.setColor(n == 0 ? 0xFF0E7C46 : (isRed(n) ? 0xFFC62828 : 0xFF1B1B1B));
            rf.set(w / 2 - wheelR, wheelCy - wheelR, w / 2 + wheelR, wheelCy + wheelR);
            cv.drawArc(rf, wheelAngle + i * seg - 90, seg, true, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        p.setColor(0xFFFFD700);
        cv.drawCircle(w / 2, wheelCy, wheelR, p);
        cv.drawCircle(w / 2, wheelCy, wheelR * 0.62f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0B3D2E);
        cv.drawCircle(w / 2, wheelCy, wheelR * 0.60f, p);

        // pocket numbers
        p.setTextSize(wheelR * 0.11f);
        for (int i = 0; i < 37; i++) {
            double a = Math.toRadians(wheelAngle + (i + 0.5f) * seg - 90);
            float tx = w / 2 + (float) Math.cos(a) * wheelR * 0.82f;
            float ty = wheelCy + (float) Math.sin(a) * wheelR * 0.82f - (p.descent() + p.ascent()) / 2;
            p.setColor(0xFFFFFFFF);
            cv.drawText(String.valueOf(WHEEL[i]), tx, ty, p);
        }

        // ball at top when idle after spin
        if (!spinning && result >= 0) {
            p.setColor(0xFFFAFAFA);
            cv.drawCircle(w / 2, wheelCy - wheelR * 0.82f, wheelR * 0.05f, p);
        }

        // win flash
        if (lastWin > 0 && now < winFlashUntil) {
            p.setColor(0xFF7CFC00);
            p.setTextSize(w * 0.065f);
            p.setFakeBoldText(true);
            cv.drawText("WIN +" + lastWin, w / 2, wheelCy + wheelR * 0.35f, p);
            p.setFakeBoldText(false);
            postInvalidateDelayed(150);
        }

        // controls
        float ctrlY = wheelCy + wheelR + 22;
        float btnH = getHeight() * 0.065f;
        p.setTextSize(w * 0.042f);

        // bet amount
        rf.set(w * 0.04f, ctrlY, w * 0.30f, ctrlY + btnH);
        p.setColor(0xFF14503C); cv.drawRoundRect(rf, 14, 14, p);
        p.setColor(0xFFFFD700); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); cv.drawRoundRect(rf, 14, 14, p); p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        cv.drawText("BET " + BETS[betIdx], w * 0.17f, ctrlY + btnH * 0.65f, p);

        // bet type
        rf.set(w * 0.36f, ctrlY, w * 0.66f, ctrlY + btnH);
        p.setColor(0xFF14503C); cv.drawRoundRect(rf, 14, 14, p);
        p.setColor(0xFFFFD700); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); cv.drawRoundRect(rf, 14, 14, p); p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        String typeLabel = TYPES[typeIdx].equals("NUM") ? ("NUM " + straightNum) : TYPES[typeIdx];
        cv.drawText(typeLabel, w * 0.51f, ctrlY + btnH * 0.65f, p);

        // spin button
        rf.set(w * 0.72f, ctrlY, w * 0.96f, ctrlY + btnH);
        boolean canSpin = !spinning && balance >= BETS[betIdx];
        p.setColor(spinning ? 0xFF555555 : (canSpin ? 0xFFC62828 : 0xFF6E3A3A));
        cv.drawRoundRect(rf, 14, 14, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(w * 0.055f);
        p.setFakeBoldText(true);
        cv.drawText(spinning ? "..." : "SPIN", w * 0.84f, ctrlY + btnH * 0.66f, p);
        p.setFakeBoldText(false);

        // odds hint
        p.setTextSize(w * 0.032f);
        p.setColor(0xFFC8D6CF);
        cv.drawText(TYPES[typeIdx].equals("NUM") ? "Straight number pays 35:1" : "Even chance pays 1:1 - tap to change bet", w / 2, ctrlY + btnH + w * 0.06f, p);

        // disclaimer
        p.setTextSize(w * 0.028f);
        p.setColor(0xFFE0C9A6);
        cv.drawText("Free play only. Virtual coins - no real money, no prizes.", w / 2, getHeight() - w * 0.03f, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        float w = getWidth();
        float ctrlY = getHeight() * 0.42f + w * 0.40f + 22;
        float btnH = getHeight() * 0.065f;
        float x = e.getX(), y = e.getY();
        if (y >= ctrlY && y <= ctrlY + btnH && !spinning) {
            if (x >= w * 0.04f && x <= w * 0.30f) {
                betIdx = (betIdx + 1) % BETS.length; persist(); invalidate();
            } else if (x >= w * 0.36f && x <= w * 0.66f) {
                if (TYPES[typeIdx].equals("NUM")) {
                    straightNum++;
                    if (straightNum > 36) { typeIdx = 0; straightNum = 7; }
                } else {
                    typeIdx++;
                }
                persist(); invalidate();
            } else if (x >= w * 0.72f && x <= w * 0.96f) {
                spin();
            }
        }
        return true;
    }
}
