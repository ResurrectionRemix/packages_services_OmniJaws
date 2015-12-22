/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnirom.omnijaws;

import java.util.HashSet;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class CustomLocationPreference extends EditTextPreference {
    public CustomLocationPreference(Context context) {
        super(context);
    }
    public CustomLocationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public CustomLocationPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        String location = Config.getLocationName(getContext());
        if (location != null) {
            setSummary(location);
        }
    }
    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        final AlertDialog d = (AlertDialog) getDialog();
        Button okButton = d.getButton(DialogInterface.BUTTON_POSITIVE);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CustomLocationPreference.this.onClick(d, DialogInterface.BUTTON_POSITIVE);
                if (getEditText().getText().toString().length() > 0) {
                    new WeatherLocationTask(d, getEditText().getText().toString()).execute();
                } else {
                    Config.setLocationId(getContext(), null);
                    Config.setLocationName(getContext(), null);
                    setSummary("");
                    setText("");
                    d.dismiss();
                }
            }
        });
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        String location = Config.getLocationName(getContext());
        if (location != null) {
            getEditText().setText(location);
            getEditText().setSelection(location.length());
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        // we handle persisting the selected location below, so pretend cancel
        super.onDialogClosed(false);
    }

    private class WeatherLocationTask extends AsyncTask<Void, Void, List<WeatherInfo.WeatherLocation>> {
        private Dialog mDialog;
        private ProgressDialog mProgressDialog;
        private String mLocation;

        public WeatherLocationTask(Dialog dialog, String location) {
            mDialog = dialog;
            mLocation = location;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            final Context context = getContext();

            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage(context.getString(R.string.weather_progress_title));
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            mProgressDialog.show();
        }

        @Override
        protected List<WeatherInfo.WeatherLocation> doInBackground(Void... input) {
            return Config.getProvider(getContext()).getLocations(mLocation);
        }

        @Override
        protected void onPostExecute(List<WeatherInfo.WeatherLocation> results) {
            super.onPostExecute(results);

            final Context context = getContext();

            if (results == null || results.isEmpty()) {
                Toast.makeText(context,
                        context.getString(R.string.weather_retrieve_location_dialog_title),
                        Toast.LENGTH_SHORT)
                        .show();
            } else if (results.size() > 1) {
                handleResultDisambiguation(results);
            } else {
                applyLocation(results.get(0));
            }
            mProgressDialog.dismiss();
        }

        private void handleResultDisambiguation(final List<WeatherInfo.WeatherLocation> results) {
            CharSequence[] items = buildItemList(results);
            new AlertDialog.Builder(getContext())
                    .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            applyLocation(results.get(which));
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setTitle(R.string.weather_select_location)
                    .show();
        }

        private CharSequence[] buildItemList(List<WeatherInfo.WeatherLocation> results) {
            boolean needCountry = false, needPostal = false;
            String countryId = results.get(0).countryId;
            HashSet<String> postalIds = new HashSet<String>();

            for (WeatherInfo.WeatherLocation result : results) {
                if (!TextUtils.equals(result.countryId, countryId)) {
                    needCountry = true;
                }
                String postalId = result.countryId + "##" + result.city;
                if (postalIds.contains(postalId)) {
                    needPostal = true;
                }
                postalIds.add(postalId);
                if (needPostal && needCountry) {
                    break;
                }
            }

            int count = results.size();
            CharSequence[] items = new CharSequence[count];
            for (int i = 0; i < count; i++) {
                WeatherInfo.WeatherLocation result = results.get(i);
                StringBuilder builder = new StringBuilder();
                if (needPostal && result.postal != null) {
                    builder.append(result.postal).append(" ");
                }
                builder.append(result.city);
                if (needCountry) {
                    String country = result.country != null
                            ? result.country : result.countryId;
                    builder.append(" (").append(country).append(")");
                }
                items[i] = builder.toString();
            }
            return items;
        }

        private void applyLocation(final WeatherInfo.WeatherLocation result) {
            Config.setLocationId(getContext(), result.id);
            Config.setLocationName(getContext(), result.city);
            setText(result.city);
            mDialog.dismiss();
            CustomLocationPreference.this.setSummary(result.city);
        }
    }
}
