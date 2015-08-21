/*
 *  Copyright (C) 2015 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnijaws;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;


public class MainActivity extends Activity {
    private TextView mTextView;
    private ProgressBar mProgress;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextView = (TextView) findViewById(R.id.textView);
        mProgress = (ProgressBar) findViewById(R.id.progress_bar);
    }
    
    public void onUpdatePressed(View v) {
        mProgress.setIndeterminate(true);
        mProgress.setProgress(1);
        WeatherService.startUpdate(this);
    }
    
    public void onSettingsPressed(View v) {
        startActivity(new Intent(this, SettingsActivity.class));
    }
    private IntentFilter mUpdateFilter = new IntentFilter(
            WeatherService.BROADCAST_INTENT);
    private BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mProgress.setIndeterminate(false);
            WeatherInfo data = Config.getWeatherData(MainActivity.this);
            if (data != null) {
                mTextView.setText(data.toString());
            }
        }
    };
    
    @Override
    protected void onPause() {
        super.onPause();
        mProgress.setIndeterminate(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mUpdateReceiver, mUpdateFilter);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mUpdateReceiver);
        super.onStop();
    }
}
