package com.pulce.wristglider;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.firebase.crash.FirebaseCrash;
import com.pulce.commonclasses.Statics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static SharedPreferences prefs;

    private static final String TAG = "MobileMain";

    private static final String DELIMITER = ";;delimiter;;";
    private NDSpinner spinner;
    private ArrayList<String> spinnerArray;
    ArrayAdapter<String> spinnerArrayAdapter;
    private boolean spinnerSilent = false;

    private static boolean debugMode = false;
    private GoogleApiClient mGoogleApiClient;
    private TableLayout tablelayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        debugMode = BuildConfig.LOG_ENABLED;

        Log.d(TAG, "Created");
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        tablelayout = (TableLayout) findViewById(R.id.tablelayout);
        addTableRow(getString(R.string.pilot_name), Statics.PREFPILOTNAME);
        addTableRow(getString(R.string.glider), Statics.PREFGLIDERARRAY);
        addTableRow(getString(R.string.logger_seconds), Statics.PREFLOGGERSECONDS);
        addTableRow(getString(R.string.logger_autostart), Statics.PREFLOGGERAUTO);
        addTableRow(getString(R.string.enable_ambient), Statics.PREFSCREENON);
        addTableRow(getString(R.string.rotate_view), Statics.PREFROTATEDEGREES);
        addTableRow(getString(R.string.height_unit), Statics.PREFHEIGTHUNIT);
        addTableRow(getString(R.string.speed_unit), Statics.PREFSPEEDUNIT);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addApi(Wearable.API).build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (debugMode) Log.d(TAG, "data event happened");
        for (DataEvent event : dataEvents) {
            if (debugMode) Log.d(TAG, event.getDataItem().getUri().getPath());
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().contains(Statics.DATAIGC)) {
                getStringFromAsset(event.getDataItem());
            }
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().contains(Statics.DATATHROWABLE)) {
                getExceptionFromWear(event.getDataItem());
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (debugMode) Log.d(TAG, "onConnected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        getAllAvailableDataItems();
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (debugMode) Log.d(TAG, "connection to location client suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (debugMode) Log.d(TAG, "connection failed");
    }

    private void getExceptionFromWear(DataItem dataItem) {
        if (debugMode) Log.d(TAG, "Exception from wear!!!");
        dataItem.freeze();
        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
        ByteArrayInputStream bis = new ByteArrayInputStream(dataMapItem.getDataMap().getByteArray("exception"));
        Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataItem.getUri());
        try {

            ObjectInputStream ois = new ObjectInputStream(bis);
            Throwable ex = (Throwable) ois.readObject();
            FirebaseCrash.log("board: " + dataMapItem.getDataMap().getString("board"));
            FirebaseCrash.log("fingerprint: " + dataMapItem.getDataMap().getString("fingerprint"));
            FirebaseCrash.log("model: " + dataMapItem.getDataMap().getString("model"));
            FirebaseCrash.log("manufacturer: " + dataMapItem.getDataMap().getString("manufacturer"));
            FirebaseCrash.log("product: " + dataMapItem.getDataMap().getString("product"));
            FirebaseCrash.report(ex);
        } catch (IOException | ClassNotFoundException e) {
            FirebaseCrash.report(e);
            if (debugMode) Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void getAllAvailableDataItems() {
        PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(mGoogleApiClient);
        results.setResultCallback(new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(@NonNull DataItemBuffer dataItems) {
                if (dataItems.getCount() != 0) {
                    for (DataItem item : dataItems) {
                        if (item.getUri().getPath().contains(Statics.DATAIGC)) {
                            getStringFromAsset(item);
                        }
                        if (item.getUri().getPath().contains(Statics.DATATHROWABLE)) {
                            getExceptionFromWear(item);
                        }
                    }
                }
                dataItems.release();
            }
        });
    }

    private void getStringFromAsset(DataItem dataItem) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    Statics.MY_PERMISSION_WRITE_STORAGE);
            return;
        }

        dataItem.freeze();
        final Uri dataItemUri = dataItem.getUri();
        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
        final Asset profileAsset = dataMapItem.getDataMap().getAsset("igcfile");
        final String igcname = dataMapItem.getDataMap().getString("igcname");
        if (debugMode) Log.d(TAG, "getStringFromAsset called");
        Runnable run = new Runnable() {
            public void run() {
                if (debugMode) Log.d(TAG, "write thread running ");
                if (profileAsset == null) {
                    FirebaseCrash.report(new IllegalArgumentException("Asset must be non-null"));
                }
                ConnectionResult result =
                        mGoogleApiClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) return;

                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, profileAsset).await().getInputStream();
                if (assetInputStream == null || igcname == null) {
                    if (debugMode) Log.e(TAG, "Requested an unknown Asset.");
                    FirebaseCrash.report(new RuntimeException("Requested an unknown Asset."));
                    return;
                }
                mkLocalDir();
                FileOutputStream fos = null;
                String filePath = Environment.getExternalStorageDirectory()
                        .getAbsolutePath()
                        + "/WristGlider/" + igcname;
                try {
                    File file = new File(filePath);
                    if (!file.exists()) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(MainActivity.this, getString(R.string.saved_log) + " " + igcname, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    fos = new FileOutputStream(file);
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = assetInputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    fos.write(buffer.toByteArray());
                    registerForDelete(igcname);
                    if (!igcname.contains(Statics.getUTCdateReverse())) {
                        Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataItemUri);
                    }
                } catch (Exception e) {
                    if (debugMode) Log.e(TAG, Log.getStackTraceString(e));
                    FirebaseCrash.report(e);

                } finally {
                    try {
                        assert fos != null;
                        fos.close();
                    } catch (IOException e) {
                        if (debugMode) Log.e(TAG, Log.getStackTraceString(e));
                        FirebaseCrash.report(e);
                    }
                }
                MediaScannerConnection.scanFile(MainActivity.this, new String[] { filePath }, null,
                        null);
            }
        };
        new Thread(run).start();
    }

    private void mkLocalDir() {
        File localDir = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath()
                + "/"
                + "WristGlider");
        if (!localDir.exists()) {
            localDir.mkdirs();
        }
        MediaScannerConnection.scanFile(this.getApplicationContext(),
                new String[]{localDir.getAbsolutePath()}, null, null);
    }


    private void updatePreferences() {
        if (debugMode) Log.d(TAG, "update Preferences");
        PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATAPREFERENCES);
        dataMap.getDataMap().putString(Statics.PREFPILOTNAME, prefs.getString(Statics.PREFPILOTNAME, "na"));
        dataMap.getDataMap().putString(Statics.PREFGLIDERTYPE, prefs.getString(Statics.PREFGLIDERTYPE, "na"));
        dataMap.getDataMap().putString(Statics.PREFGLIDERID, prefs.getString(Statics.PREFGLIDERID, "na"));
        dataMap.getDataMap().putLong(Statics.PREFLOGGERSECONDS, prefs.getLong(Statics.PREFLOGGERSECONDS, 1000));
        dataMap.getDataMap().putBoolean(Statics.PREFLOGGERAUTO, prefs.getBoolean(Statics.PREFLOGGERAUTO, false));
        dataMap.getDataMap().putBoolean(Statics.PREFROTATEVIEW, prefs.getBoolean(Statics.PREFROTATEVIEW, false));
        dataMap.getDataMap().putBoolean(Statics.PREFSCREENON, prefs.getBoolean(Statics.PREFSCREENON, false));
        dataMap.getDataMap().putString(Statics.PREFSPEEDUNIT, prefs.getString(Statics.PREFSPEEDUNIT, "km/h"));
        dataMap.getDataMap().putString(Statics.PREFHEIGTHUNIT, prefs.getString(Statics.PREFHEIGTHUNIT, "m"));
        dataMap.getDataMap().putString(Statics.PREFROTATEDEGREES, prefs.getString(Statics.PREFROTATEDEGREES, "0"));
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    }

    private void registerForDelete(final String filename) {
        if (debugMode) Log.d(TAG, filename + " registered to be deleted");
        PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATADELETE + filename);
        dataMap.getDataMap().putString(Statics.DATADELETE, filename);
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    }

    private String gliderArrayToString(ArrayList<String> gliders) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gliders.size() - 1; i++) {
            sb.append(gliders.get(i));
            if (i < gliders.size() - 2) {
                sb.append(DELIMITER);
            }
        }
        return sb.toString();
    }

    private ArrayList<String> stringToGliderArray(String gliders) {
        ArrayList<String> glidersArray;
        if (gliders == null) {
            glidersArray = new ArrayList<>();
        } else {
            String[] dumbArray = gliders.split(DELIMITER);
            glidersArray = new ArrayList<>(Arrays.asList(dumbArray));
        }
        glidersArray.add(getString(R.string.add_new_glider1) + "\n" + getString(R.string.add_new_glider2));
        return glidersArray;
    }

    private void addTableRow(final String text1, final String preferencekey) {
        TableRow tr = new TableRow(this);
        tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        TextView tv1 = (TextView) getLayoutInflater().inflate(R.layout.standard_textview, tr, false);
        tv1.setText(text1);
        tr.addView(tv1);
        switch (preferencekey) {
            case Statics.PREFGLIDERARRAY:
                spinnerSilent = true;
                spinner = new NDSpinner(this);
                spinnerArray = stringToGliderArray(prefs.getString(preferencekey, null));
                spinnerArrayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_two_boxes, R.id.spinner_twoline_text_view, spinnerArray);
                spinner.setAdapter(spinnerArrayAdapter);
                tr.addView(spinner);
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (spinnerSilent) {
                            spinnerSilent = false;
                            return;
                        }
                        if (spinner.getSelectedItem().toString().contains(getString(R.string.add_new_glider1))) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle(R.string.glider_type_and_id);
                            final EditText gliderType = new EditText(MainActivity.this);
                            gliderType.setHint(getString(R.string.glider_type));
                            final EditText gliderID = new EditText(MainActivity.this);
                            gliderID.setHint(getString(R.string.glider_id));
                            LinearLayout layout = new LinearLayout(MainActivity.this);
                            layout.setOrientation(LinearLayout.VERTICAL);
                            layout.addView(gliderType);
                            layout.addView(gliderID);
                            builder.setView(layout);
                            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    spinnerSilent = true;
                                    spinnerArray.add(0, gliderType.getText().toString() + "\n" + gliderID.getText().toString());
                                    spinner.setSelection(0, false);
                                    spinnerArrayAdapter.notifyDataSetChanged();
                                    prefs.edit().putString(preferencekey, gliderArrayToString(spinnerArray)).apply();
                                    prefs.edit().putString(Statics.PREFGLIDERTYPE, gliderType.getText().toString()).apply();
                                    prefs.edit().putString(Statics.PREFGLIDERID, gliderID.getText().toString()).apply();
                                    updatePreferences();
                                }
                            });
                            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    spinnerSilent = true;
                                    spinner.setSelection(spinner.lastActivePosition);
                                }
                            });
                            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    spinnerSilent = true;
                                    spinner.setSelection(spinner.lastActivePosition);
                                }
                            });
                            builder.show();
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle(R.string.glider_type_and_id);
                            final EditText gliderType = new EditText(MainActivity.this);
                            gliderType.setText(spinner.getSelectedItem().toString().split("\n")[0]);
                            final EditText gliderID = new EditText(MainActivity.this);
                            gliderID.setText(spinner.getSelectedItem().toString().split("\n")[1]);
                            LinearLayout layout = new LinearLayout(MainActivity.this);
                            layout.setOrientation(LinearLayout.VERTICAL);
                            layout.addView(gliderType);
                            layout.addView(gliderID);
                            builder.setView(layout);
                            builder.setPositiveButton(R.string.select, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    String selection = spinnerArray.get(spinner.getSelectedItemPosition());
                                    spinnerArray.remove(selection);
                                    spinnerArray.add(0, gliderType.getText().toString() + "\n" + gliderID.getText().toString());
                                    spinnerSilent = true;
                                    spinner.setSelection(0);
                                    spinnerArrayAdapter.notifyDataSetChanged();
                                    prefs.edit().putString(preferencekey, gliderArrayToString(spinnerArray)).apply();
                                    prefs.edit().putString(Statics.PREFGLIDERTYPE, gliderType.getText().toString()).apply();
                                    prefs.edit().putString(Statics.PREFGLIDERID, gliderID.getText().toString()).apply();
                                    updatePreferences();
                                }
                            });
                            builder.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    spinnerSilent = true;
                                    spinnerArray.remove(spinner.getSelectedItem());
                                    spinner.setSelection(0, false);
                                    spinnerArrayAdapter.notifyDataSetChanged();
                                }
                            });
                            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    spinnerSilent = true;
                                    spinner.setSelection(spinner.lastActivePosition);
                                }
                            });
                            builder.show();
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                });
                break;
            case Statics.PREFLOGGERAUTO:
            case Statics.PREFSCREENON:
                final CheckBox cp2 = new CheckBox(this);
                cp2.setChecked(prefs.getBoolean(preferencekey, false));
                cp2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        prefs.edit().putBoolean(preferencekey, isChecked).apply();
                        updatePreferences();
                    }
                });
                tr.addView(cp2);
                break;
            case Statics.PREFHEIGTHUNIT:
            case Statics.PREFSPEEDUNIT:
            case Statics.PREFLOGGERSECONDS:
            case Statics.PREFROTATEDEGREES:
                final Spinner spinner2 = new Spinner(this);
                ArrayList<String> spinner2Array = new ArrayList<>();
                switch (preferencekey) {
                    case Statics.PREFROTATEDEGREES:
                        spinner2Array.add("0");
                        spinner2Array.add("90");
                        spinner2Array.add("-90");
                        break;
                    case Statics.PREFHEIGTHUNIT:
                        spinner2Array.add("m");
                        spinner2Array.add("ft");
                        break;
                    case Statics.PREFSPEEDUNIT:
                        spinner2Array.add("km/h");
                        spinner2Array.add("mph");
                        spinner2Array.add("kn");
                        break;
                    default:
                        spinner2Array.add("1");
                        spinner2Array.add("2");
                        spinner2Array.add("5");
                        spinner2Array.add("10");
                        break;
                }
                ArrayAdapter<String> spinner2ArrayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, R.id.standard_text_view, spinner2Array);
                spinner2.setAdapter(spinner2ArrayAdapter);
                for (int i = 0; i < spinner2Array.size(); i++) {
                    if (preferencekey.equals(Statics.PREFLOGGERSECONDS)) {
                        if (("" + (prefs.getLong(preferencekey, 0)/1000)).equals(spinner2Array.get(i))) {
                            spinner2.setSelection(i);
                        }
                    } else {
                        if (prefs.getString(preferencekey, "").equals(spinner2Array.get(i))) {
                            spinner2.setSelection(i);
                        }
                    }
                }
                spinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (preferencekey.equals(Statics.PREFLOGGERSECONDS)) {
                            prefs.edit().putLong(preferencekey, Long.parseLong(spinner2.getSelectedItem().toString()) * 1000).apply();
                        } else {
                            prefs.edit().putString(preferencekey, spinner2.getSelectedItem().toString()).apply();
                        }
                        updatePreferences();
                        if (debugMode) Log.d(TAG, spinner2.getSelectedItem().toString());
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
                tr.addView(spinner2);
                break;
            default:
                tr.setClickable(true);
                final TextView tv2 = (TextView) getLayoutInflater().inflate(R.layout.standard_textview, tr, false);
                if (prefs.getString(preferencekey, "na").equals("na")) {
                    tv2.setHint(R.string.enter_pilot);
                } else {
                    tv2.setText(prefs.getString(preferencekey, "na"));
                }

                tr.addView(tv2);
                tv2.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(text1);
                        //TODO: enter
                        final EditText input = new EditText(MainActivity.this);
                        input.setText(prefs.getString(preferencekey, ""));
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        builder.setView(input);
                        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                tv2.setText(input.getText().toString());
                                tv2.setText(input.getText().toString());
                                prefs.edit().putString(preferencekey, tv2.getText().toString()).apply();
                                if (debugMode) Log.d(TAG, "String pref changed");
                                updatePreferences();
                                if (debugMode) Log.d(TAG, "Updating Preferences");
                            }
                        });
                        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                        builder.show();
                    }
                });
                break;
        }
        tablelayout.addView(tr, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Statics.MY_PERMISSION_WRITE_STORAGE: {
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        getAllAvailableDataItems();
                    } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(MainActivity.this, getString(R.string.permission_external_storage_hint), Toast.LENGTH_LONG).show();
                    }
                 }
            }
        }
    }

}
