package fr.overlay.launcher;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.*;
import android.graphics.PixelFormat;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.util.*;

public class OverlayService extends AccessibilityService {
    private WindowManager wm;
    private GridView grid;
    private String home;
    private boolean shown;
    private List<ResolveInfo> apps = new ArrayList<>();

    @Override protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        PackageManager pm = getPackageManager();
        ResolveInfo h = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        home = h != null ? h.activityInfo.packageName : "com.miui.home";
        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setBackgroundColor(0xEE000000);
        grid.setPadding(24, 120, 24, 120);
        grid.setVerticalSpacing(32);
        grid.setOnItemClickListener((p, v, i, id) -> {
            ActivityInfo a = apps.get(i).activityInfo;
            hide();
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(a.packageName, a.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        });
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence p = e.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        if (pkg.equals(home)) {
            CharSequence c = e.getClassName();
            if (c != null && c.toString().toLowerCase().contains("recent")) hide(); else show();
        } else hide();
    }

    private void show() {
        if (shown) return;
        loadApps();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        wm.addView(grid, lp);
        shown = true;
    }

    private void hide() {
        if (!shown) return;
        wm.removeView(grid);
        shown = false;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        apps.removeIf(r -> r.activityInfo.packageName.equals(getPackageName()));
        Collections.sort(apps, (a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        grid.setAdapter(new BaseAdapter() {
            public int getCount() { return apps.size(); }
            public Object getItem(int i) { return apps.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup g) {
                TextView t = v instanceof TextView ? (TextView) v : new TextView(OverlayService.this);
                ResolveInfo r = apps.get(i);
                android.graphics.drawable.Drawable d = r.loadIcon(pm);
                d.setBounds(0, 0, 144, 144);
                t.setCompoundDrawables(null, d, null, null);
                t.setText(r.loadLabel(pm));
                t.setTextColor(0xFFFFFFFF);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine();
                return t;
            }
        });
    }

    @Override public void onInterrupt() { hide(); }
}
