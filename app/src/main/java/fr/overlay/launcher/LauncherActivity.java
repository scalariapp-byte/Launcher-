package fr.overlay.launcher;

import android.app.Activity;
import android.os.Bundle;

/** Vrai écran d'accueil (à choisir comme lanceur par défaut). */
public class LauncherActivity extends Activity {
    private LauncherView view;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        view = new LauncherView(this, () -> {}, true);
        setContentView(view);
    }

    @Override protected void onResume() { super.onResume(); view.reset(); }

    @Override protected void onNewIntent(android.content.Intent i) { super.onNewIntent(i); view.closeDrawer(); }

    @Override public void onBackPressed() { if (view.drawerOpen()) view.closeDrawer(); }
}
