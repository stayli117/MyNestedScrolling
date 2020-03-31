package com.stayli.nested;


import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.stayli.nested.view.MyNestedChild;

/**
 * Created by  yahuigao
 * Date: 2020/3/30
 * Time: 10:44 AM
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MyNestedChild myNestedChild = findViewById(R.id.mnc);

        int childCount = myNestedChild.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childAt = myNestedChild.getChildAt(i);
            if (childAt instanceof TextView) {
                ((TextView) childAt).setText(String.format("底部可滚动滚动组件 %d", i));
            }
        }
    }
}
