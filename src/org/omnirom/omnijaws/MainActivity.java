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
        IntentFilter updateFilter = new IntentFilter();
        updateFilter.addAction(WeatherService.BROADCAST_INTENT);
        updateFilter.addAction(WeatherService.STOP_INTENT);
        registerReceiver(mUpdateReceiver, updateFilter);
    }
    
    public void onUpdatePressed(View v) {
        mProgress.setIndeterminate(true);
        mProgress.setProgress(1);
        WeatherService.startUpdate(this, true);
    }
    
    public void onSettingsPressed(View v) {
        startActivity(new Intent(this, SettingsActivity.class));
    }
    private BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WeatherService.BROADCAST_INTENT)) {
                mProgress.setIndeterminate(false);
                WeatherInfo data = Config.getWeatherData(MainActivity.this);
                if (data != null) {
                    mTextView.setText(data.toString());
                }
            } else if (action.equals(WeatherService.STOP_INTENT)) {
                mProgress.setIndeterminate(false);
            }
        }
    };
    
    @Override
    protected void onPause() {
        super.onPause();
        mProgress.setIndeterminate(false);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUpdateReceiver);
        super.onStop();
    }
}
