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

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {


    private LayoutInflater mInflater;
    private List<FoundBluetoothDevice> devices;
    private static final long month = (long) 60 * 60 * 24 * 30 * 1000; // one month converted to milliseconds
    private static final long week = 1000*60*60*24*7; // one week converted to milliseconds
    private static final long day = 1000*60*60*24; // one day converted to milliseconds
    private static final long hour = 1000*60*60; // one day converted to milliseconds
    private static final long min = 1000*60; // one day converted to milliseconds
    private static final long sec = 1000; // one second converted to milliseconds

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

    public DeviceListAdapter(Context context, List<FoundBluetoothDevice> devices) {
        this.devices = devices;
        this.mInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public DeviceListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View contactView = mInflater.inflate(R.layout.device, parent, false);
        return new ViewHolder(contactView); }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int positionReal = holder.getAdapterPosition();
        FoundBluetoothDevice device = devices.get(positionReal);
        Context context = holder.itemView.getContext();

        Typeface regularFont = Typeface.createFromAsset(context.getAssets(), "fonts/Tajawal-Medium.ttf");
        holder.name.setTypeface(regularFont);
        holder.timeOfThemBeingAround.setTypeface(regularFont);

        if(device.live){
            holder.statusColor.setBackgroundColor(Color.GREEN);
        } else {
            holder.statusColor.setBackgroundColor(Color.RED);
        }


        if(device.name==null)
            holder.name.setText("Loading name...");
        else {
            if(device.name.equals("null")){
                holder.name.setText("Loading name...");
            } else if(device.name.contains(" ## ")){
                holder.name.setText("#Covide " + device.name.split(" ## ")[0]);
            } else {
                holder.name.setText(device.name.split(" ## ")[0]);
            }
        }

        timeStuff(holder, device.total_time_encountered_with);

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



    private void processTime(long time_since_last_online, String timeText, long baseTime, ViewHolder holder) {
        int how_many_times = (int) (time_since_last_online / baseTime);
        if(how_many_times>0){
            if(how_many_times>1){
                holder.timeOfThemBeingAround.setText(how_many_times + " " + timeText + "s");
            } else {
                holder.timeOfThemBeingAround.setText("1 " + timeText);
            }
        }
    }

    private void timeStuff(ViewHolder holder, long totalTime){
        totalTime *= 1000;
        if(totalTime >= month){
            processTime(totalTime, "month", month, holder);
        } else if(totalTime >= week){
            processTime(totalTime, "week", week, holder);
        } else if(totalTime >= day){
            processTime(totalTime, "day", day, holder);
        } else if(totalTime >= hour){
            processTime(totalTime, "hour", hour, holder);
        } else if(totalTime >= min){
            processTime(totalTime, "min", min, holder);
        } else {
            processTime(totalTime, "sec", sec, holder);
        }
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
    public int getItemCount() { return devices.size(); }

}
