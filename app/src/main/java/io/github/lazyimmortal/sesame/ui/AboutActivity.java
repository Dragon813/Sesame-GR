package io.github.lazyimmortal.sesame.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ViewAppInfo;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvVersion = findViewById(R.id.tv_about_version);
        String ver = ViewAppInfo.getAppVersion();
        try {
            int vc = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            tvVersion.setText(ver + " (" + vc + ")");
        } catch (Exception e) {
            tvVersion.setText(ver);
        }

        findViewById(R.id.tv_github_source).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/Dragon813/Sesame-GR")));
        });
        findViewById(R.id.tv_maintainer).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/Dragon813")));
        });
        findViewById(R.id.tv_ui_credit).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/aw1y2z")));
        });
    }
}
