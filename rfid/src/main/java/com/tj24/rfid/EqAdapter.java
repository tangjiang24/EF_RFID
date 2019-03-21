package com.tj24.rfid;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by energy on 2018/12/24.
 */

public class EqAdapter extends RecyclerView.Adapter<EqAdapter.VH>{

    private LayoutInflater inflater;
    private Context mContext;
    private List<BluetoothDevice> devices = new ArrayList<>();

    public EqAdapter(Context mContext, List<BluetoothDevice> devices) {
        this.mContext = mContext;
        this.devices = devices;
        inflater = LayoutInflater.from(mContext);
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        return  new VH(inflater.inflate(R.layout.rc_eq,null));
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        final BluetoothDevice device = devices.get(position);
        holder.tvName.setText(device.getName());
        holder.tvAdress.setText(device.getAddress());
        holder.bt.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onItemClickListner!=null){
                    onItemClickListner.onItemClick(device);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    class VH extends RecyclerView.ViewHolder{
        TextView tvName;
        TextView tvAdress;
        Button bt;
        public VH(View itemView) {
            super(itemView);
            tvAdress = (TextView) itemView.findViewById(R.id.tv_address);
            tvName = (TextView) itemView.findViewById(R.id.tv_name);
            bt = (Button) itemView.findViewById(R.id.btn_inventory);
        }
    }

    OnItemClickListner onItemClickListner;
    public interface OnItemClickListner{
      void  onItemClick(BluetoothDevice device);
    }

    public void setOnItemClickListner(OnItemClickListner onItemClickListner){
        this.onItemClickListner = onItemClickListner;
    }
}
