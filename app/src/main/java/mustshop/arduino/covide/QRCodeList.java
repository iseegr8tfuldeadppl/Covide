package mustshop.arduino.covide;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class QRCodeList extends AppCompatActivity implements DeviceListInterface {

    private RecyclerView qrcodeListRecyclerView;
    private List<QRCoding.QRCode> qrCodes = new ArrayList<>();
    private ImageView gobackImage;
    private QRCodeListAdapter qrCodeListAdapter;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private final String MY_PREFS_NAME = "mustshop.arduino.covide";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode_list);

        Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler());

        gobackImage = findViewById(R.id.gobackImage);

        glider(this, R.drawable.arrow, gobackImage);

        prepareSharedPrefs();
        loadSharedPrefs();

        qrcodeListRecyclerView = findViewById(R.id.deviceListRecyclerView);
        qrCodeListAdapter = new QRCodeListAdapter(this, qrCodes);
        qrcodeListRecyclerView.setAdapter(qrCodeListAdapter);
        qrcodeListRecyclerView.setLayoutManager(new MyLinearLayoutManager(this, 1, false));
    }

    private void loadSharedPrefs() {
        String visited_locations_string = prefs.getString("visited_locations", "");
        String[] visited_locations = visited_locations_string.split(",");
        for(int i=0; i<visited_locations.length; i++){
            int finalI = i;
            qrCodes.add(new QRCoding.QRCode(){{
                qrcode = visited_locations[finalI];
            }});
        }
    }

    private void log(Object log){
        Log.i("HH", String.valueOf(log));
    }

    private void saveSharedPrefs() {
        StringBuilder allQRCodesTogether = new StringBuilder("");
        for(QRCoding.QRCode code:qrCodes){
            allQRCodesTogether.append(code.qrcode).append(",");
        }
        editor.putString("visited_locations", allQRCodesTogether.toString());
        editor.commit();
    }



    private void prepareSharedPrefs() {
        prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
    }

    void glider(Context context, int source, ImageView image){
        try {
            Glide.with(context).load(source).into(image);
        } catch (Exception e) {
            Log.i("HH", e.toString());
            e.printStackTrace();
        }
    }


    public void gobackClicked(View view) {
        Intent intent = new Intent(this, QRCoding.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, QRCoding.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void delete(int positionReal) {
        qrCodes.remove(positionReal);
        qrCodeListAdapter.notifyItemRemoved(positionReal);
        qrCodeListAdapter.notifyItemRangeChanged(positionReal, qrCodes.size());
        saveSharedPrefs();
    }
}