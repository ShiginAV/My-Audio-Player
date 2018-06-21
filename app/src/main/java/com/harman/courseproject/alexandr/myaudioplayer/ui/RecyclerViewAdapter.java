package com.harman.courseproject.alexandr.myaudioplayer.ui;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import com.harman.courseproject.alexandr.myaudioplayer.R;

public class RecyclerViewAdapter extends RecyclerView.Adapter<ViewHolder> {

    private List<Audio> audioList;
    private Context context;

    public RecyclerViewAdapter(List<Audio> audioList, Context context) {
        this.audioList = audioList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String song = String.format("%s - %s", audioList.get(position).getArtist(), audioList.get(position).getTitle());
        holder.getTitle().setText(song);
    }

    @Override
    public int getItemCount() {
        return audioList.size();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }
}

class ViewHolder extends RecyclerView.ViewHolder {
    private TextView title;
    private ImageView play_pause;

    public TextView getTitle() {
        return title;
    }

    public ImageView getPlay_pause() {
        return play_pause;
    }

    public ViewHolder(View itemView) {
        super(itemView);
        title = itemView.findViewById(R.id.title);
        play_pause = itemView.findViewById(R.id.play_pause);
    }
}
