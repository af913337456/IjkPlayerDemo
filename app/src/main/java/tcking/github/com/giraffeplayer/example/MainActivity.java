package tcking.github.com.giraffeplayer.example;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.LinearLayout;

import tcking.github.com.giraffeplayer.GiraffePlayer;

/**
 *  Created by LinGuanHong on 16/5/17.
 *
 */

public class MainActivity extends AppCompatActivity {
    GiraffePlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** 动态添加，方便多路播放 */
        player = new GiraffePlayer
                (
                        this,
                        (LinearLayout) findViewById(R.id.container),
                        "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8"
                );

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.onDestroy();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (player != null) {
            /** 让用户自己手动切换就注释下面的 */
           player.onConfigurationChanged(newConfig,true);
        }
    }
}
