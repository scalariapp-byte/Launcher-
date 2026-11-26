package fr.overlay.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Deux modes :
 *  - lanceur MIUI par défaut : affiche LauncherView par-dessus l'accueil MIUI (gestes natifs) ;
 *  - cette app par défaut : ajoute des zones de gestes (bas = accueil / récents, bords = retour).
 */
public class OverlayService extends AccessibilityService {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable check = this::evaluate;
    private WindowManager wm;
    private LauncherView view;
    private View bottom, left, right;
    private String homePkg;
    private boolean shown, gestures;

    @Override protected void onServiceConnected() {
        wm = getSystemService(WindowManager.class);
        refreshMode();
    }

    private void refreshMode() {
        ResolveInfo h = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        homePkg = h != null ? h.activityInfo.packageName : "com.miui.home";
        boolean mine = homePkg.equals(getPackageName());
        if (mine && !gestures) { hide(); addGestureZones(); }
        if (!mine && gestures) removeGestureZones();
        if (!mine && view == null) view = new LauncherView(this, this::hide, false);
    }

    // ---------- Mode superposition ----------

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null) return;
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) refreshMode();
        if (gestures) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        ui.removeCallbacks(check);
        if (!pkg.equals(homePkg)) { hide(); return; }
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && shown) view.closeDrawer();
        ui.postDelayed(check, 200);
    }

    @Override public void onInterrupt() { ui.removeCallbacks(check); hide(); }

    private void evaluate() {
        AccessibilityNodeInfo n = getRootInActiveWindow();
        if (n == null || n.getPackageName() == null || !homePkg.equals(n.getPackageName().toString())) { hide(); return; }
        if (isRecents(n, 0)) hide(); else show();
    }

    private boolean isRecents(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 12) return false;
        String id = n.getViewIdResourceName();
        if (id != null && n.isVisibleToUser()) {
            id = id.toLowerCase();
            if (id.contains("recent") || id.contains("clear") || id.contains("task")) return true;
        }
        for (int i = 0; i < n.getChildCount(); i++) if (isRecents(n.getChild(i), depth + 1)) return true;
        return false;
    }

    private void show() {
        if (shown || view == null) return;
        view.reset();
        // On laisse libre la bande du bas : c'est là que MIUI capte ses gestes.
        int h = getResources().getDisplayMetrics().heightPixels + view.statusBar() - gestureBar();
        WindowManager.LayoutParams lp = params(-1, h, Gravity.TOP);
        lp.windowAnimations = android.R.style.Animation_Toast;
        wm.addView(view, lp);
        shown = true;
    }

    private void hide() {
        if (!shown) return;
        wm.removeViewImmediate(view);
        shown = false;
    }

    // ---------- Mode lanceur : gestes recréés ----------

    private void addGestureZones() {
        int edge = view == null ? dp(14) : view.dp(14);
        bottom = zone(Zone.BOTTOM); left = zone(Zone.LEFT); right = zone(Zone.RIGHT);
        // Les overlays d'accessibilité passent au-dessus de la barre de navigation :
        // on place les zones au-dessus d'elle pour ne pas bloquer ses boutons.
        int nav = buttonNavBar();
        int h = getResources().getDisplayMetrics().heightPixels - nav;
        WindowManager.LayoutParams b = params(-1, dp(22), Gravity.BOTTOM);
        b.y = nav;
        wm.addView(bottom, b);
        wm.addView(left, params(edge, h, Gravity.TOP | Gravity.START));
        wm.addView(right, params(edge, h, Gravity.TOP | Gravity.END));
        gestures = true;
    }

    private void removeGestureZones() {
        for (View v : new View[]{bottom, left, right}) if (v != null) wm.removeViewImmediate(v);
        gestures = false;
    }

    private enum Zone { BOTTOM, LEFT, RIGHT }

    private View zone(Zone z) {
        View v = new View(this);
        final float[] start = new float[2];
        final long[] t0 = new long[1];
        final boolean[] fired = new boolean[1];
        final Runnable recents = () -> { fired[0] = true; performGlobalAction(GLOBAL_ACTION_RECENTS); };
        v.setOnTouchListener((x, e) -> {
            float dx = e.getRawX() - start[0], dy = e.getRawY() - start[1];
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawX(); start[1] = e.getRawY(); t0[0] = e.getEventTime(); fired[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    // Bas : glisser puis immobiliser le doigt = récents
                    if (z == Zone.BOTTOM && !fired[0] && -dy > dp(80)) {
                        ui.removeCallbacks(recents);
                        ui.postDelayed(recents, 250);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    ui.removeCallbacks(recents);
                    if (fired[0]) return true;
                    if (z == Zone.BOTTOM) {
                        if (-dy > dp(60)) performGlobalAction(GLOBAL_ACTION_HOME);
                        else if (Math.abs(dx) > dp(80)) performGlobalAction(GLOBAL_ACTION_RECENTS);
                    } else {
                        float inward = z == Zone.LEFT ? dx : -dx;
                        if (inward > dp(50)) performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                    return true;
                default:
                    ui.removeCallbacks(recents);
                    return true;
            }
        });
        return v;
    }

    // ---------- Utilitaires ----------

    private WindowManager.LayoutParams params(int w, int h, int gravity) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        return lp;
    }

    /** Hauteur de la barre à boutons si elle est affichée, sinon 0. */
    private int buttonNavBar() {
        int mode = getResources().getIdentifier("config_navBarInteractionMode", "integer", "android");
        if (mode > 0 && getResources().getInteger(mode) == 2) return 0;
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(48);
    }

    private int gestureBar() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return Math.max(id > 0 ? getResources().getDimensionPixelSize(id) : 0, dp(24));
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
