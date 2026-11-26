package fr.overlay.launcher;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Toast.makeText(this, "Active « Overlay Launcher » dans Accessibilité", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        finish();
    }
}
