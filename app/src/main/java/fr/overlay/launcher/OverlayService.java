package fr.overlay.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Accueil + tiroir affichés par-dessus le lanceur MIUI : les gestes système restent intacts. */
public class OverlayService extends AccessibilityService {

    static class App {
        final String label, pkg, cls; final Drawable icon;
        App(String l, String p, String c, Drawable i) { label = l; pkg = p; cls = c; icon = i; }
        String key() { return pkg + "/" + cls; }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable check = this::evaluate;
    private final List<App> apps = new ArrayList<>(), favs = new ArrayList<>();
    private WindowManager wm;
    private SharedPreferences prefs;
    private FrameLayout root;
    private ImageView wall;
    private View home, drawer;
    private BaseAdapter appsAdapter, favsAdapter;
    private String homePkg;
    private boolean shown;
    private long wallStamp = -1;

    @Override protected void onServiceConnected() {
        wm = getSystemService(WindowManager.class);
        prefs = getSharedPreferences("p", MODE_PRIVATE);
        ResolveInfo h = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        homePkg = h != null ? h.activityInfo.packageName : "com.miui.home";
        buildUi();
        reloadApps();
    }

    // ---------- Détection de l'accueil ----------

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        ui.removeCallbacks(check);
        if (!pkg.equals(homePkg)) { hide(); return; }
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && shown) closeDrawer();
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

    // ---------- Interface ----------

    private void buildUi() {
        root = new FrameLayout(this);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, -1, -1);
        View dim = new View(this);
        dim.setBackgroundColor(0x40000000);
        root.addView(dim, -1, -1);

        // Accueil : heure, date, favoris
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(16), statusBar() + dp(48), dp(16), dp(32));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm");
        clock.setTextSize(76); clock.setTextColor(0xFFFFFFFF);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM"); date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(17); date.setTextColor(0xDDFFFFFF);
        date.setGravity(Gravity.CENTER_HORIZONTAL);
        h.addView(clock); h.addView(date);
        View space = new View(this);
        h.addView(space, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = label("Glisse vers le haut · appui long sur une app pour l'épingler", 12, 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        h.addView(hint);
        GridView favGrid = grid(favs);
        favsAdapter = (BaseAdapter) favGrid.getAdapter();
        favGrid.setPadding(dp(8), dp(16), dp(8), dp(16));
        GradientDrawable dock = new GradientDrawable();
        dock.setColor(0x33FFFFFF); dock.setCornerRadius(dp(28));
        favGrid.setBackground(dock);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(12);
        h.addView(favGrid, flp);
        home = h;
        root.addView(home, -1, -1);

        // Tiroir
        GridView d = grid(apps);
        appsAdapter = (BaseAdapter) d.getAdapter();
        d.setPadding(dp(12), statusBar() + dp(24), dp(12), dp(48));
        d.setClipToPadding(false);
        d.setBackgroundColor(0xE6101014);
        d.setVisibility(View.GONE);
        drawer = d;
        root.addView(drawer, -1, -1);

        GestureDetector g = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (Math.abs(vy) < Math.abs(vx)) return false;
                if (vy < 0 && drawer.getVisibility() != View.VISIBLE) { openDrawer(); return true; }
                if (vy > 0 && drawer.getVisibility() == View.VISIBLE && !drawer.canScrollVertically(-1)) { closeDrawer(); return true; }
                return false;
            }
        });
        home.setOnTouchListener((v, e) -> g.onTouchEvent(e));
        favGrid.setOnTouchListener((v, e) -> { g.onTouchEvent(e); return false; });
        drawer.setOnTouchListener((v, e) -> { g.onTouchEvent(e); return false; });
    }

    private GridView grid(List<App> list) {
        GridView g = new GridView(this);
        g.setNumColumns(4);
        g.setVerticalSpacing(dp(20));
        g.setVerticalScrollBarEnabled(false);
        g.setOverScrollMode(View.OVER_SCROLL_NEVER);
        g.setSelector(android.R.color.transparent);
        g.setOnItemClickListener((p, v, i, id) -> launch(list.get(i)));
        g.setOnItemLongClickListener((p, v, i, id) -> { toggleFav(list.get(i)); return true; });
        g.setAdapter(new BaseAdapter() {
            public int getCount() { return list.size(); }
            public Object getItem(int i) { return list.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                TextView t = v != null ? (TextView) v : label("", 12, 0xFFFFFFFF);
                if (v == null) {
                    t.setGravity(Gravity.CENTER_HORIZONTAL);
                    t.setSingleLine(); t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setCompoundDrawablePadding(dp(6));
                    t.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
                }
                App a = list.get(i);
                a.icon.setBounds(0, 0, dp(54), dp(54));
                t.setCompoundDrawables(null, a.icon, null, null);
                t.setText(a.label);
                return t;
            }
        });
        return g;
    }

    private TextView label(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private void openDrawer() {
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationY(root.getHeight());
        drawer.animate().translationY(0).setDuration(220).start();
        home.animate().alpha(0).setDuration(220).start();
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        home.animate().alpha(1).setDuration(200).start();
        drawer.animate().translationY(root.getHeight()).setDuration(200)
                .withEndAction(() -> { drawer.setVisibility(View.GONE); ((GridView) drawer).setSelection(0); }).start();
    }

    private void show() {
        if (shown) return;
        loadWallpaper();
        reloadApps();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (android.os.Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        lp.windowAnimations = android.R.style.Animation_Toast;
        wm.addView(root, lp);
        shown = true;
    }

    private void hide() {
        if (!shown) return;
        wm.removeViewImmediate(root);
        shown = false;
        drawer.setVisibility(View.GONE);
        home.setAlpha(1);
    }

    // ---------- Données ----------

    private void launch(App a) {
        hide();
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private void toggleFav(App a) {
        Set<String> s = new HashSet<>(prefs.getStringSet("favs", new HashSet<>()));
        boolean add = !s.remove(a.key());
        if (add) {
            if (s.size() >= 8) { Toast.makeText(this, "8 favoris maximum", Toast.LENGTH_SHORT).show(); return; }
            s.add(a.key());
        }
        prefs.edit().putStringSet("favs", s).apply();
        Toast.makeText(this, a.label + (add ? " épinglé" : " retiré"), Toast.LENGTH_SHORT).show();
        refreshFavs();
    }

    private void refreshFavs() {
        Set<String> s = prefs.getStringSet("favs", new HashSet<>());
        favs.clear();
        for (App a : apps) if (s.contains(a.key())) favs.add(a);
        favsAdapter.notifyDataSetChanged();
    }

    private void reloadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<App> l = new ArrayList<>();
            for (ResolveInfo r : pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                if (r.activityInfo.packageName.equals(getPackageName())) continue;
                l.add(new App(r.loadLabel(pm).toString(), r.activityInfo.packageName, r.activityInfo.name, r.loadIcon(pm)));
            }
            l.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            ui.post(() -> { apps.clear(); apps.addAll(l); appsAdapter.notifyDataSetChanged(); refreshFavs(); });
        }).start();
    }

    private void loadWallpaper() {
        File f = new File(getFilesDir(), MainActivity.WALL);
        long stamp = f.exists() ? f.lastModified() : 0;
        if (stamp == wallStamp) return;
        wallStamp = stamp;
        if (stamp == 0) {
            wall.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF1E2A4A, 0xFF3A1F4F, 0xFF0F1020}));
            return;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int target = getResources().getDisplayMetrics().heightPixels;
        o.inSampleSize = 1;
        while (o.outHeight / (o.inSampleSize * 2) >= target) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        wall.setImageBitmap(BitmapFactory.decodeFile(f.getPath(), o));
    }

    private int statusBar() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(32);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
