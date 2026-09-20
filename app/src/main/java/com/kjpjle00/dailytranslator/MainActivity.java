package com.kjpjle00.dailytranslator;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("일상번역기");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(19, 54, 79));
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("\n초기화 완료\n새 기획서 기준으로 다시 개발합니다.");
        subtitle.setTextSize(17);
        subtitle.setTextColor(Color.rgb(92, 111, 125));
        subtitle.setGravity(Gravity.CENTER);

        root.addView(title);
        root.addView(subtitle);
        setContentView(root);
    }
}
