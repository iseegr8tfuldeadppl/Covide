package mustshop.arduino.covide;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import mustshop.arduino.covide.MainActivity.FoundBluetoothDevice;

public class QRCodeListAdapter extends RecyclerView.Adapter<QRCodeListAdapter.ViewHolder> {


    private LayoutInflater mInflater;
    private List<QRCoding.QRCode> qrcodes;

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, timeOfThemBeingAround;
        LinearLayout everything;
        ImageView delete;
        RelativeLayout statusColor;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            timeOfThemBeingAround = itemView.findViewById(R.id.timeOfThemBeingAround);
            everything = itemView.findViewById(R.id.everything);
            delete = itemView.findViewById(R.id.delete);
            statusColor = itemView.findViewById(R.id.state);
        }
    }

    public QRCodeListAdapter(Context context, List<QRCoding.QRCode> qrcodes) {
        this.qrcodes = qrcodes;
        this.mInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public QRCodeListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View contactView = mInflater.inflate(R.layout.device, parent, false);
        return new ViewHolder(contactView); }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int positionReal = holder.getAdapterPosition();
        QRCoding.QRCode qrCode = qrcodes.get(positionReal);
        Context context = holder.itemView.getContext();

        Typeface regularFont = Typeface.createFromAsset(context.getAssets(), "fonts/Tajawal-Medium.ttf");
        holder.name.setTypeface(regularFont);
        holder.timeOfThemBeingAround.setTypeface(regularFont);

        holder.name.setText("#" + (positionReal+1) + " QR Code: " + qrCode.qrcode);
        holder.timeOfThemBeingAround.setText("");

        DeviceListInterface callback = (DeviceListInterface) context;
        holder.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.delete(positionReal);
            }
        });

        // laod images
        glider(context, R.drawable.delete, holder.delete);
    }

    void glider(Context context, int source, ImageView image){
        try {
            Glide.with(context).load(source).into(image);
        } catch (Exception e) {
            Log.i("HH", e.toString());
            e.printStackTrace();
        }
    }

    private void log(Object log){
        Log.i("HH", String.valueOf(log));
    }

    @Override
    public int getItemCount() { return qrcodes.size(); }

}
