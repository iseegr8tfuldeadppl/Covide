package mustshop.arduino.covide;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class QRCoding extends AppCompatActivity {

    private Context context;
    private ImageView gobackImage, qrcodeListImage;
    private static final int PERMISSION_REQUEST_CAMERA = 0;
    private SurfaceView surfaceView;
    private BarcodeDetector barcodeDetector;
    private CameraSource cameraSource;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private final String MY_PREFS_NAME = "mustshop.arduino.covide";
    private List<QRCode> QRCodes = new ArrayList<>();
    private TextView status;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcoding);

        Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler());
        context = this;

        surfaceView = findViewById(R.id.surfaceView);
        gobackImage = findViewById(R.id.gobackImage);
        qrcodeListImage = findViewById(R.id.qrcodeListImage);
        status = findViewById(R.id.status);

        glider(context, R.drawable.arrow, gobackImage);
        glider(context, R.drawable.qr2, qrcodeListImage);

        prepareSharedPrefs();
        loadSharedPrefs();

        requestCamera();
    }

    private boolean released = false;
    @Override
    protected void onPause() {
        super.onPause();
        if(cameraSource!=null){
            cameraSource.release();
            released = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(released){
            released = false;
            startCamera();
        }
    }

    private void requestCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(QRCoding.this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            }
        }
    }

    private void print(Object log){
        Toast.makeText(context, String.valueOf(log), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode) {
            case PERMISSION_REQUEST_CAMERA:
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
                } else {
                    print("Camera Permission Denied");
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }

    private boolean saying_we_saved_it = false;
    private long previous_save_of_this_value = 0;
    private long delay_until_we_say_we_already_saved_this_one = 3000; // 3 seconds
    private void startCamera() {

        // reloading thread stuff
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean took_it_into_account = false;
                while(true){
                    if(saying_we_saved_it && !took_it_into_account){
                        took_it_into_account = true;
                        previous_save_of_this_value = System.currentTimeMillis();
                    }
                    if(took_it_into_account && System.currentTimeMillis() - previous_save_of_this_value > delay_until_we_say_we_already_saved_this_one){
                        took_it_into_account = false;
                        saying_we_saved_it = false;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                status.setText("Scanning...");
                            }
                        });
                    }

                }
            }
        }).start();


        // scanning stuff
        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.ALL_FORMATS)
                .build();

        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setRequestedPreviewSize(1920, 1080)
                .setAutoFocusEnabled(true) //you should add this feature
                .build();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    if (ContextCompat.checkSelfPermission(QRCoding.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraSource.start(surfaceView.getHolder());
                    } else {
                        ActivityCompat.requestPermissions(QRCoding.this, new
                                String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    log(e);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                cameraSource.stop();
            }
        });


        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
                log("released");
            }

            @Override
            public void receiveDetections(@NonNull Detector.Detections<Barcode> detections) {
                final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                if (barcodes.size() != 0) {
                    boolean new_qrcodes = false;
                    for(int i=0; i<barcodes.size(); i++){
                        boolean already_scanned = false;
                        for(QRCode code:QRCodes){
                            if(code.qrcode.equals(barcodes.valueAt(i).rawValue)){
                                already_scanned = true;
                                break;
                            }
                        }
                        if(!already_scanned){
                            int finalI = i;
                            QRCodes.add(new QRCode(){{
                                qrcode = barcodes.valueAt(finalI).rawValue;
                            }});
                            new_qrcodes = true;
                        }
                    }
                    if(new_qrcodes){
                        saveSharedPrefs();
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(!saying_we_saved_it){
                                    saying_we_saved_it = true;
                                    status.setText("Already Scanned");
                                }
                            }
                        });
                    }
                }
            }
        });
    }


    private void prepareSharedPrefs() {
        prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
    }

    public void listClicked(View view) {
        Intent intent = new Intent(context, QRCodeList.class);
        startActivity(intent);
        finish();
    }

    public static class QRCode {
        String qrcode = "";
    }

    private void saveSharedPrefs() {
        StringBuilder allQRCodesTogether = new StringBuilder("");
        for(QRCode code:QRCodes){
            allQRCodesTogether.append(code.qrcode).append(",");
        }
        editor.putString("visited_locations", allQRCodesTogether.toString());
        editor.commit();

        handler.post(new Runnable() {
            @Override
            public void run() {
                if(!saying_we_saved_it){
                    saying_we_saved_it = true;
                    status.setText("Scanned Location #" + QRCodes.size());
                }
            }
        });
    }

    private void loadSharedPrefs() {
        String visited_locations_string = prefs.getString("visited_locations", "");
        String[] visited_locations = visited_locations_string.split(",");
        for(int i=0; i<visited_locations.length; i++){
            if(!visited_locations[i].isEmpty()){
                int finalI = i;
                QRCodes.add(new QRCode(){{
                    qrcode = visited_locations[finalI];
                }});
            }
        }
    }

    private void log(Object log){
        Log.i("HH", String.valueOf(log));
    }


    public void gobackClicked(View view) {
        Intent intent = new Intent(context, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(context, MainActivity.class);
        startActivity(intent);
        finish();
    }

    void glider(Context context, int source, ImageView image){
        try {
            Glide.with(context).load(source).into(image);
        } catch (Exception e) {
            Log.i("HH", e.toString());
            e.printStackTrace();
        }
    }

    public void scanClicked(View view) {
    }
}