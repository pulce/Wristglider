package com.pulce.wristglider;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.activity.WearableActivity;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.ErrorDialogFragment;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
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
import com.pulce.commonclasses.Statics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class MainWearActivity extends WearableActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        DataApi.DataListener,
        Thread.UncaughtExceptionHandler {

    private static final String TAG = "WearMain";

    private static final DecimalFormat latForm = new DecimalFormat("0000000");
    private static final DecimalFormat lonForm = new DecimalFormat("00000000");
    private static SimpleDateFormat clockFormat;

    private static SharedPreferences prefs;
    private static boolean debugMode = false;

    private static boolean mockup = false;

    private TextView speedTextView;
    private TextView altTextView;
    private TextView alternatives;
    private ImageView directionView;
    private TextView loggerState;
    //private TextView batteryState;
    private ProgressBar progressBar;
    private RelativeLayout coreLayout;

    private Bitmap arrowBitmap;
    private Matrix rotateMatrix = new Matrix();

    private PrintWriter pw;
    private boolean loggerRunning = false;
    private String recentIgcFileName;

    private GoogleApiClient mGoogleApiClient;

    private Handler mHandler;

    private Thread.UncaughtExceptionHandler mDefaultUncaughtExceptionHandler;

    private LinkedList<Location> pointStack;
    private Location lastLoggedLocation;
    private long startTimeOfFlight = 0;
    private int killFirstDirtylocations = 0;

    private float speedmultiplier;
    private float heightmultiplier;

    private boolean activityStopping;

    private EarthGravitationalModel gh;

    private BroadcastReceiver mBatInfoReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        debugMode = BuildConfig.LOG_ENABLED;
        if (debugMode) Log.d(TAG, "creating " + BuildConfig.VERSION_NAME);
        setAmbientEnabled();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        gh = new EarthGravitationalModel();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    gh.load(MainWearActivity.this);
                } catch (Exception e) {
                    if (debugMode) Log.e(TAG, Log.getStackTraceString(e));
                    reportException(e);
                }
            }
        });
        if (debugMode) Log.d(TAG, "Screen flag " + prefs.getBoolean(Statics.PREFSCREENON, false));
        if (!prefs.getBoolean(Statics.PREFSCREENON, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (prefs.getBoolean(Statics.PREFROTATEVIEW, false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        if (DateFormat.is24HourFormat(this)) {
            clockFormat = new SimpleDateFormat("HH:mm");
        } else {
            clockFormat = new SimpleDateFormat("hh:mm");
        }

        setContentView(R.layout.activity_wear_main);
        setMultipliers();

        arrowBitmap = BitmapFactory.decodeResource(this.getResources(),
                R.drawable.direction);

        pointStack = new LinkedList<>();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        speedTextView = (TextView) findViewById(R.id.speedtext);
        altTextView = (TextView) findViewById(R.id.altitext);
        directionView = (ImageView) findViewById(R.id.directionImage);
        alternatives = (TextView) findViewById(R.id.otherfeed);
        loggerState = (TextView) findViewById(R.id.loggerstate);
        /*batteryState = (TextView) findViewById(R.id.batterystate);
        mBatInfoReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context ctxt, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                batteryState.setText("Bat: " + String.valueOf(level) + "%");
            }
        };*/
        this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        progressBar = (ProgressBar) findViewById(R.id.progress);
        coreLayout = (RelativeLayout) findViewById(R.id.container);
        coreLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (loggerRunning) {
                    final AlertDialog.Builder dialog = new AlertDialog.Builder(MainWearActivity.this)
                            .setTitle(R.string.stop_logger)
                            .setMessage(R.string.stop_logger_confirm)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    stopLogger();
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert);
                    final AlertDialog alert = dialog.create();
                    alert.show();
                    final Handler handler = new Handler();
                    final Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if (alert.isShowing()) {
                                alert.dismiss();
                            }
                        }
                    };
                    alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            handler.removeCallbacks(runnable);
                        }
                    });
                    handler.postDelayed(runnable, 3000);
                } else {
                    startLogger();
                }
                return true;
            }
        });

        if (mockup) {
            progressBar.setVisibility(View.INVISIBLE);
            speedTextView.setText("36.5");
            altTextView.setText("3585");
            directionView.setImageDrawable(getRotatedDir(30));
        }
/*        alternatives.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                File dir = getFilesDir();
                File[] subFiles = dir.listFiles();
                if (subFiles != null) {
                    for (File file : subFiles) {
                        if (debugMode) Log.d(TAG, "Available Logfile: " + file.getName());
                    }
                }
                return true;
            }
        });*/
    }


    @Override
    public void onStart() {
        super.onStart();
        if (debugMode) Log.d(TAG, "starting");
        activityStopping = false;
        mGoogleApiClient.connect();
        mHandler = new Handler();
        Runnable screenUpdate = new Runnable() {
            public void run() {
                if (debugMode) Log.d(TAG, "schedule running");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                    if (alternatives != null)
                            alternatives.setText(clockFormat.format(new Date()).replaceAll(":", "\n"));
                    }
                });
                mHandler.postDelayed(this, 1000 * 60);
            }
        };
        screenUpdate.run();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (debugMode) Log.d(TAG, "stopping");
        activityStopping = true;
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }
        mGoogleApiClient.disconnect();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (debugMode) Log.d(TAG, "pausing");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (debugMode) Log.d(TAG, "on connected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);

        PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(mGoogleApiClient);
        results.setResultCallback(new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(@NonNull DataItemBuffer dataItems) {
                if (dataItems.getCount() != 0) {
                    for (DataItem item : dataItems) {
                        if (item.getUri().getPath().contains(Statics.DATAPREFERENCES)) {
                            updatePreferencesFromDataItem(item);
                        } else if (item.getUri().getPath().contains(Statics.DATADELETE)) {
                            deleteSingleIgcFile(item);
                        }
                    }
                }
                dataItems.release();
            }
        });

        File dir = getFilesDir();
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File file : subFiles) {
                if (!file.getName().contains(".igc")) continue;
                Asset asset = createAssetFromTextfile(file);
                PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATAIGC + file.getName());
                dataMap.getDataMap().putString("igcname", file.getName());
                dataMap.getDataMap().putAsset("igcfile", asset);
                PutDataRequest request = dataMap.asPutDataRequest();
                request.setUrgent();
                Wearable.DataApi.putDataItem(mGoogleApiClient, request);
            }
        }
        requestLocationUpdates();
        //throw new RuntimeException("This is a wear croshhhhh");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (debugMode) Log.d("TAG", "data event happened");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().contains(Statics.DATAPREFERENCES)) {
                updatePreferencesFromDataItem(event.getDataItem());
            } else if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().contains(Statics.DATADELETE)) {
                deleteSingleIgcFile(event.getDataItem());
            }
        }
    }

    private void deleteSingleIgcFile(DataItem item) {
        DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
        String filename = dataMapItem.getDataMap().getString(Statics.DATADELETE);
        File dir = getFilesDir();
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File file : subFiles) {
                if (filename.contains(Statics.getUTCdateReverse())) return;
                if (file.getName().equals(filename)) {
                    if (file.delete()) {
                        if (debugMode) Log.d(TAG, "File " + filename + " is deleted.");
                        Wearable.DataApi.deleteDataItems(mGoogleApiClient, item.getUri());
                    } else {
                        if (debugMode) Log.d(TAG, "File " + filename + " delete error.");
                    }
                }
            }
        }
    }

    private void updatePreferencesFromDataItem(DataItem dataItem) {
        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
        prefs.edit().putString(Statics.PREFPILOTNAME, dataMapItem.getDataMap().getString(Statics.PREFPILOTNAME)).apply();
        prefs.edit().putString(Statics.PREFGLIDERTYPE, dataMapItem.getDataMap().getString(Statics.PREFGLIDERTYPE)).apply();
        prefs.edit().putString(Statics.PREFGLIDERID, dataMapItem.getDataMap().getString(Statics.PREFGLIDERID)).apply();
        prefs.edit().putLong(Statics.PREFLOGGERSECONDS, dataMapItem.getDataMap().getLong(Statics.PREFLOGGERSECONDS)).apply();
        prefs.edit().putBoolean(Statics.PREFLOGGERAUTO, dataMapItem.getDataMap().getBoolean(Statics.PREFLOGGERAUTO)).apply();
        if (prefs.getBoolean(Statics.PREFROTATEVIEW, false) != dataMapItem.getDataMap().getBoolean(Statics.PREFROTATEVIEW)) {
            if (dataMapItem.getDataMap().getBoolean(Statics.PREFROTATEVIEW)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
            }
        }
        prefs.edit().putBoolean(Statics.PREFROTATEVIEW, dataMapItem.getDataMap().getBoolean(Statics.PREFROTATEVIEW)).apply();
        if (prefs.getBoolean(Statics.PREFSCREENON, false) != dataMapItem.getDataMap().getBoolean(Statics.PREFSCREENON)) {
            Intent i = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            finish();
            startActivity(i);
        }
        prefs.edit().putBoolean(Statics.PREFSCREENON, dataMapItem.getDataMap().getBoolean(Statics.PREFSCREENON)).apply();
        prefs.edit().putString(Statics.PREFSPEEDUNIT, dataMapItem.getDataMap().getString(Statics.PREFSPEEDUNIT)).apply();
        prefs.edit().putString(Statics.PREFHEIGTHUNIT, dataMapItem.getDataMap().getString(Statics.PREFHEIGTHUNIT)).apply();
        setMultipliers();
        if (debugMode) Log.d(TAG, "Preferences updated");
    }

    @Override
    public void onLocationChanged(Location location) {
        if (debugMode) Log.d(TAG, "location changed");
        killFirstDirtylocations++;
        if (location.hasSpeed()) {
            speedTextView.setText(String.format("%.1f", location.getSpeed() * speedmultiplier));
            if (location.getAccuracy() < 15 && killFirstDirtylocations++ > 4) {
                pointStack.add(location);
            }
        } else
            speedTextView.setText("--");
        if (location.hasAltitude()) {
            //Log.d(TAG, "Offsett: " + gh.heightOffset(location.getLongitude(), location.getLatitude(), location.getAltitude()));
            // Bearing
            progressBar.setVisibility(View.INVISIBLE);
            if (location.hasBearing()) {
                directionView.setImageDrawable(getRotatedDir(location.getBearing()));
            }
            altTextView.setText(String.format("%.0f", (location.getAltitude() - gh.heightOffset(location.getLongitude(), location.getLatitude(), location.getAltitude())) * heightmultiplier));
        } else {
            altTextView.setText("--");
        }
        if (!location.hasBearing()) {
            directionView.setImageResource(android.R.color.transparent);
        }
        alternatives.setText(clockFormat.format(new Date()).replaceAll(":", "\n"));

        if (pointStack.size() > 10) pointStack.removeFirst();
        if (loggerRunning) {
            if (startTimeOfFlight > 0) {
                int minutes = (int) (((location.getTime() - startTimeOfFlight) / (1000*60)) % 60);
                int hours   = (int) ((location.getTime() - startTimeOfFlight) / (1000*60*60));
                loggerState.setText(getString(R.string.flight_time) + " " + String.format("%02d", hours) + ":" + String.format("%02d", minutes));
            }
            if (location.hasAltitude()) logIGCline(location);
        } else if (pointStack.size() > 9 &&
                location.distanceTo(pointStack.getFirst()) > (location.getTime() - pointStack.getFirst().getTime()) * 0.004) {
            if (prefs.getBoolean(Statics.PREFLOGGERAUTO, false)) {
                startLogger();
                for (Location loc : pointStack) logIGCline(loc);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void requestLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(900);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainWearActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, R.string.permission_fine_location_hint, Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    Statics.MY_PERMISSION_FINE_LOCATION);
        } else {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
        if (debugMode) Log.d(TAG, "connection to location client suspended");
        if (!activityStopping) mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (debugMode) Log.d(TAG, "connection to location client failed");
        reportException(new RuntimeException(TAG, new Throwable("connection to location client failed")));
        if (!activityStopping) mGoogleApiClient.connect();
    }

    public BitmapDrawable getRotatedDir(float angle) {
        Bitmap canvasBitmap = arrowBitmap.copy(Bitmap.Config.ARGB_8888, true);
        canvasBitmap.eraseColor(0x00000000);
        Canvas canvas = new Canvas(canvasBitmap);
        rotateMatrix.setRotate(angle, canvas.getWidth() / 2, canvas.getHeight() / 2);
        canvas.drawBitmap(arrowBitmap, rotateMatrix, null);
        return new BitmapDrawable(getResources(), canvasBitmap);
     }

    private Asset createAssetFromTextfile(File filename) {
        FileInputStream fis = null;
        ByteArrayOutputStream byteStream = null;
        try {
            fis = new FileInputStream(filename);
            byte[] buffer = new byte[4096];
            byteStream = new ByteArrayOutputStream();
            int read;
            while ((read = fis.read(buffer)) != -1) {
                byteStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            reportException(e);
        } finally {
            try {
                if (byteStream != null)
                    byteStream.close();
            } catch (IOException e) {
                reportException(e);
            }
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                reportException(e);
            }
        }
        assert byteStream != null;
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    private void startLogger() {
        if (debugMode) Log.d(TAG, "logger triggered");
        int num = 1;
        boolean unique;
        do {
            File dir = getFilesDir();
            File[] subFiles = dir.listFiles();
            recentIgcFileName = Statics.getUTCdateReverse() + "_WG_" + num++ + ".igc";
            unique = true;
            if (subFiles != null) {
                for (File file : subFiles) {
                    if (file.getName().equals(recentIgcFileName)) unique = false;
                }
            }
        } while (!unique);
        if (debugMode) Log.d(TAG, "Now  creating " + recentIgcFileName);
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(recentIgcFileName, Context.MODE_PRIVATE);
            pw = new PrintWriter(fos, true);
            pw.println("AXXX"); // No Manufaxcturer code
            pw.println("HFFXA020"); // fixy acuracy 35m
            pw.println("HFDTE" + Statics.getUTCdateAsString());
            pw.println("HFPLTPILOTINCHARGE:" + prefs.getString(Statics.PREFPILOTNAME, "Anno Nymos"));
            pw.println("HFGTYGLIDERTYPE:" + prefs.getString(Statics.PREFGLIDERTYPE, "Dummy Glider"));
            pw.println("HFGIDGLIDERID:" + prefs.getString(Statics.PREFGLIDERID, "12345"));
            pw.println("HFFTYFRTYPE:WristGlider_V_" + BuildConfig.VERSION_NAME);
            pw.println("HFGPS:Generic");
            pw.println("HFDTM100DATUM:WGS-1984");
            pw.println("I013638FXA");
            loggerRunning = true;
            if (pointStack.size() > 0) {
                startTimeOfFlight = pointStack.getFirst().getTime();
            } else {
                startTimeOfFlight = new Date().getTime();
            }
            loggerState.setText(getString(R.string.logger_started));
        } catch (FileNotFoundException e) {
            reportException(e);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException e) {
                reportException(e);
            }
        }
    }

    private void stopLogger() {
        File dir = getFilesDir();
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File file : subFiles) {
                if (file.getName().contains(recentIgcFileName)) {
                    if (debugMode) Log.d(TAG, "Now checking File " + file.getName());
                    Asset asset = createAssetFromTextfile(file);
                    PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATAIGC + file.getName());
                    dataMap.getDataMap().putString("igcname", file.getName());
                    dataMap.getDataMap().putAsset("igcfile", asset);
                    PutDataRequest request = dataMap.asPutDataRequest();
                    request.setUrgent();
                    Wearable.DataApi.putDataItem(mGoogleApiClient, request);
                }
            }
        }
        loggerRunning = false;
        loggerState.setText("");
    }

    private void logIGCline(Location location) {
        if (lastLoggedLocation == null) lastLoggedLocation = location;
        if ((location.getTime() - lastLoggedLocation.getTime()) <
                prefs.getLong(Statics.PREFLOGGERSECONDS, 1000) + 50) {
            return;
        }
        if (getFileStreamPath(recentIgcFileName) == null || !getFileStreamPath(recentIgcFileName).exists()) {
            startLogger();
        }
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(recentIgcFileName, Context.MODE_APPEND);
            pw = new PrintWriter(fos, true);
            pw.println("B" + Statics.getUTCtimeAsString(location.getTime()) +
                    Statics.decToIgcFormat(location.getLatitude(), latForm) + "N" +
                    Statics.decToIgcFormat(location.getLongitude(), lonForm) + "E" +
                    "A00000" + new DecimalFormat("00000").format(location.getAltitude() - gh.heightOffset(location.getLongitude(), location.getLatitude(), location.getAltitude())) +
                    new DecimalFormat("000").format(location.getAccuracy()));
        } catch (FileNotFoundException e) {
            reportException(e);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException e) {
                reportException(e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == Statics.MY_PERMISSION_FINE_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationUpdates();
            } else {
                Toast.makeText(this, R.string.permission_fine_location_hint, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void setMultipliers() {
        if (prefs.getString(Statics.PREFSPEEDUNIT, "km/h").equals("km/h")) {
            speedmultiplier = 3.6f;
        } else if (prefs.getString(Statics.PREFSPEEDUNIT, "km/h").equals("kn")){
            speedmultiplier = 1.943844f;
        } else {
            speedmultiplier = 2.236936f;
        }
        if (prefs.getString(Statics.PREFHEIGTHUNIT, "m").equals("m")) {
            heightmultiplier = 1f;
        } else {
            heightmultiplier = 3.28084f;
        }
        Log.d(TAG, "" + speedmultiplier + heightmultiplier);
    }

    private void reportException(Throwable throwable) {
        if (debugMode) Log.d(TAG, "reporting exception");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(throwable);
            byte[] exceptionData = bos.toByteArray();
            PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATATHROWABLE + throwable.toString());
            dataMap.getDataMap().putString("board", Build.BOARD);
            dataMap.getDataMap().putString("fingerprint", Build.FINGERPRINT);
            dataMap.getDataMap().putString("model", Build.MODEL);
            dataMap.getDataMap().putString("manufacturer", Build.MANUFACTURER);
            dataMap.getDataMap().putString("product", Build.PRODUCT);
            dataMap.getDataMap().putByteArray("exception", exceptionData);
            PutDataRequest request = dataMap.asPutDataRequest();
            request.setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, request);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        } finally {
            try {
                if (oos != null)
                    oos.close();
            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            try {
                bos.close();
            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    @Override
    public void uncaughtException(Thread thread, final Throwable throwable) {
        if (debugMode) Log.d(TAG, "exception thrown and caught");
        reportException(throwable);
        mDefaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
    }
}
