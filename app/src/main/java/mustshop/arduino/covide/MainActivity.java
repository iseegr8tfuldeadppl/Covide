package mustshop.arduino.covide;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private Context context;
    private BluetoothAdapter mBluetoothAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler());
        context = this;

        if (!checkIfAlreadyhavePermission()) {
            requestForSpecificPermission();
        } if(checkIfAlreadyhaveLocationPermission()){
            requestForSpecificLocationPermission();
        } else {
            oncreateStuff();
        }
    }

    private void oncreateStuff() {
        declareBluetoothStuff();
        displayBondedDevices();
        attachBroadCastReceiver();
        search();
    }

    @Override
    protected void onDestroy() {
        MainActivity.this.unregisterReceiver(myReceiver);
        super.onDestroy();
    }

    private static int REQUEST_ENABLE_BT = 1337;
    private boolean searching = false;
    private long last_search=0;
    private void search(){

        new Thread(new Runnable() {
            @Override
            public void run() {

                int previous_elements_size = 0;
                while(true){
                    // was bluetooth turned off
                    if(!mBluetoothAdapter.isEnabled()) {
                        log("bluetooth is off");
                        setBluetoothEnable(true);
                    }

                    if(!searching && System.currentTimeMillis() - last_search > 5000){
                        last_search = System.currentTimeMillis();
                        startSearching();
                    }

                }

            }
        }).start();
    }


    public void setBluetoothEnable(Boolean enable) {
        if(mBluetoothAdapter != null){
            if (enable) {
                if (!mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.enable();
                }
            } else {
                if (mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.disable();
                }
            }
        }
    }


    private void startSearching() {
        mBluetoothAdapter.startDiscovery();
    }



    private void attachBroadCastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        MainActivity.this.registerReceiver(myReceiver, intentFilter);
    }


    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg = Message.obtain();
            String action = intent.getAction();
            assert action != null;

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    // TODO: update a button to allow the user to stop discovery mode, or just disable it on pause or when they close the devices menu
                    log("Discovery started");
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    // TODO: update a button to allow the user to stop discovery mode, or just disable it on pause or when they close the devices menu
                    log("Discovery finished");
                    searching = false;
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    //Found, add to a device list

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        print("new device " + device.getName() + " " + device.getAddress());
                    }
                    break;
            }
        }
    };

    private void displayBondedDevices(){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        for(BluetoothDevice device: pairedDevices){
            print("Bonded device " + device.getName() + " " + device.getAddress());
        }
    }

    private boolean declareBluetoothStuff() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            log("No bluetooth adapter available");
            return false;
        }

        return true;
    }

    private boolean checkIfAlreadyhavePermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1)
            return true;
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN);
    }

    private boolean checkIfAlreadyhaveLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }




    private void requestForSpecificPermission() {
        ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.BLUETOOTH_ADMIN }, 101);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 101:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //granted
                    if(checkIfAlreadyhaveLocationPermission()){
                        requestForSpecificLocationPermission();
                    } else {
                        oncreateStuff();
                    }
                } else {
                    //not granted
                    print("Failed to get bluetooth access");
                }
                break;
            case 102:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //granted
                    oncreateStuff();
                } else {
                    //not granted
                    print("Failed to get location access");
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void requestForSpecificLocationPermission() {
        ActivityCompat.requestPermissions((Activity) this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                102);
    }


    private void print(Object log){
        Toast.makeText(context, String.valueOf(log), Toast.LENGTH_LONG).show();
    }



    private void log(Object log){
        Log.i("HH", String.valueOf(log));
    }

}