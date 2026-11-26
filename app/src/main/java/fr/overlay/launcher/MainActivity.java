package fr.overlay.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    static final String WALL = "wallpaper.img";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        l.setPadding(p, p * 3, p, p);
        l.addView(button("1. Activer le service (Accessibilité)",
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        l.addView(button("2. Choisir un fond d'écran", () ->
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 1)));
        l.addView(button("3. (Option) En faire le lanceur par défaut — gestes recréés",
                () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS))));
        l.addView(button("Retirer le fond d'écran", () -> {
            deleteFile(WALL);
            Toast.makeText(this, "Fond retiré", Toast.LENGTH_SHORT).show();
        }));
        setContentView(l);
    }

    private Button button(String text, Runnable r) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> r.run());
        return btn;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData());
             OutputStream out = new FileOutputStream(new java.io.File(getFilesDir(), WALL))) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            Toast.makeText(this, "Fond appliqué", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
