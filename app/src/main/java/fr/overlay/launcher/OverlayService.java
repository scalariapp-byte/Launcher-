package fr.overlay.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tiroir d'apps affiché par-dessus le lanceur MIUI : les gestes système restent intacts. */
public class OverlayService extends AccessibilityService {

    private static class App {
        final String label, pkg, cls;
        final Drawable icon;
        App(String label, String pkg, String cls, Drawable icon) {
            this.label = label; this.pkg = pkg; this.cls = cls; this.icon = icon;
        }
    }

    private WindowManager wm;
    private GridView grid;
    private String homePkg;
    private boolean shown;
    private final List<App> apps = new ArrayList<>();

    @Override protected void onServiceConnected() {
        wm = getSystemService(WindowManager.class);
        ResolveInfo h = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        homePkg = h != null ? h.activityInfo.packageName : "com.miui.home";
        grid = buildGrid();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        String cls = e.getClassName() == null ? "" : e.getClassName().toString().toLowerCase();
        if (pkg.equals(homePkg) && !cls.contains("recent")) show(); else hide();
    }

    @Override public void onInterrupt() { hide(); }

    private GridView buildGrid() {
        GridView g = new GridView(this);
        g.setNumColumns(4);
        g.setVerticalSpacing(dp(20));
        g.setPadding(dp(12), dp(64), dp(12), dp(48));
        g.setClipToPadding(false);
        g.setVerticalScrollBarEnabled(false);
        g.setOverScrollMode(View.OVER_SCROLL_NEVER);
        g.setSelector(android.R.color.transparent);
        g.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB0000000, 0xE0000000}));
        g.setOnItemClickListener((p, v, i, id) -> {
            App a = apps.get(i);
            hide();
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(a.pkg, a.cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        });
        g.setAdapter(new BaseAdapter() {
            public int getCount() { return apps.size(); }
            public Object getItem(int i) { return apps.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                TextView t = (TextView) v;
                if (t == null) {
                    t = new TextView(OverlayService.this);
                    t.setGravity(Gravity.CENTER_HORIZONTAL);
                    t.setTextColor(0xFFFFFFFF);
                    t.setTextSize(12);
                    t.setSingleLine();
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setCompoundDrawablePadding(dp(6));
                    t.setPadding(dp(4), 0, dp(4), 0);
                }
                App a = apps.get(i);
                a.icon.setBounds(0, 0, dp(52), dp(52));
                t.setCompoundDrawables(null, a.icon, null, null);
                t.setText(a.label);
                return t;
            }
        });
        return g;
    }

    private void show() {
        if (shown) return;
        loadApps();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.windowAnimations = android.R.style.Animation_Toast;
        grid.setSelection(0);
        wm.addView(grid, lp);
        shown = true;
    }

    private void hide() {
        if (!shown) return;
        wm.removeViewImmediate(grid);
        shown = false;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        apps.clear();
        for (ResolveInfo r : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            apps.add(new App(r.loadLabel(pm).toString(), r.activityInfo.packageName,
                    r.activityInfo.name, r.loadIcon(pm)));
        }
        apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
