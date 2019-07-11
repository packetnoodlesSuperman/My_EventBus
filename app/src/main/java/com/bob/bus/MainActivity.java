package com.bob.bus;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.greenrobot.eventbus.Subscribe;

public class MainActivity extends AppCompatActivity {

    @Subscribe
    public void onMainEvent(String msg) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public static void main(String[] args) {
        String index = "org.greenrobot.eventbusperf.MyEventBusIndex";
        int i = index.lastIndexOf(46);
        System.out.print(i+"");

    }
}
