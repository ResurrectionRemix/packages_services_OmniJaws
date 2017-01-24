package org.omnirom.omnijaws.client;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.omnirom.omnijaws.SettingsActivity;
import org.omnirom.omnijaws.R;

public class WeatherTileService extends TileService {
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        queryAndUpdateWeather();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        Intent weatherSettings = new Intent(this, SettingsActivity.class);
        startActivity(weatherSettings);
    }

    private BitmapDrawable resize(Resources resources, Drawable image,
                                  int iconSize) {
        float density = resources.getDisplayMetrics().density;
        int size = (int) Math.round(iconSize * density);
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        Bitmap bmResult = Bitmap.createBitmap(size, size,
                    Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmResult);
        Drawable d = image.mutate();
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return new BitmapDrawable(resources, bmResult);
    }

    public void queryAndUpdateWeather() {
        OmniJawsClient mWeatherClient = new OmniJawsClient(this);
        OmniJawsClient.WeatherInfo mWeatherData = null;
        Drawable weatherImage = mWeatherClient.getErrorWeatherConditionImage();
        String label = getResources().getString(R.string.service_error);
        try {
            if (!mWeatherClient.isOmniJawsEnabled()) {
                label = getResources().getString(R.string.service_disabled);
            } else {
                mWeatherClient.queryWeather();
                mWeatherData = mWeatherClient.getWeatherInfo();
                if (mWeatherData != null) {
                    weatherImage = mWeatherClient.getWeatherConditionImage(mWeatherData.conditionCode);
                    label = mWeatherData.temp + mWeatherData.tempUnits + " " + mWeatherData.city;
                } else {
                    label = getResources().getString(R.string.service_error);
                }
            }
        } catch(Exception e) {
        }
        Tile tile = this.getQsTile();
        tile.setLabel(label);
        tile.setIcon(Icon.createWithBitmap(resize(getResources(), weatherImage, 48).getBitmap()));
        tile.updateTile();

    }
}
