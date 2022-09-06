package com.eledris.jots;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JotsRecyclerViewAdapter extends RecyclerView.Adapter<JotsRecyclerViewAdapter.ViewHolder> {

    // The character length of the note preview shown in the NotesFragment.
    private static final int maxPreviewLength = 30;

    private final List<Note> mData;
    private final LayoutInflater mInflater;
    private ItemClickListener mClickListener;

    private Context context;

    // Pass data into the constructor.
    JotsRecyclerViewAdapter(Context context, List<Note> data) {
        this.context = context;
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
    }

    // Inflate the xml layout for the note preview row, when required.
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.notes_preview, parent, false);
        return new ViewHolder(view);
    }

    // Bind data to the note preview holder.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        // Set the note preview's title.
        String title = mData.get(position).title;
        holder.noteTitle.setText(title);

        // Load the content preview.
        String content = mData.get(position).content;
        Spanned contentPreview = Html.fromHtml(content);

        // Select the entire content text or the shortened version (with ... added to the end), whichever is shorter.
        contentPreview = (contentPreview.length() > maxPreviewLength) ? (new SpannedString(contentPreview.subSequence(0, maxPreviewLength - 3) + "...")) : contentPreview;

        // .toString() strips the preview text of its HTML formatting - displaying the formatting could cause issues in the previews.
        holder.noteContentPreview.setText(contentPreview.toString());

        // Set the date preview of the note preview.
        Date date = mData.get(position).lastEdited;
        DateFormat dateFormat = new SimpleDateFormat(MainActivity.selectedDateFormat, Locale.US);
        holder.noteDate.setText(dateFormat.format(date));

        // Hide the content preview and date if the hideContentPreview setting is enabled.
        boolean hideContentPreview = MainActivity.sharedPreferences.getBoolean("settings_hidecontentpreview",  context.getResources().getBoolean(R.bool.settings_hideContentPreview_defaultValue));
        holder.noteContentPreview.setVisibility(hideContentPreview ? View.GONE : View.VISIBLE);
        holder.noteDate.setVisibility(hideContentPreview ? View.GONE : View.VISIBLE);

    }

    // Get the total number of rows.
    @Override
    public int getItemCount() {
        return mData.size();
    }

    // The current selected position.
    private int currentPos;
    public int getCurrentPos() { return currentPos; }

    // Get the item on the selected currentPos.
    public Note getSelectedItem() {
        if (currentPos >= 0 && mData != null && currentPos < mData.size()) {
            return mData.get(currentPos);
        }
        return null;
    }

    // Stores and recycles views as they are scrolled off screen.
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView noteTitle;
        TextView noteContentPreview;
        TextView noteDate;

        // Load the data from the note preview into the ViewHolder.
        ViewHolder(View itemView) {
            super(itemView);
            noteTitle = itemView.findViewById(R.id.note_title);
            noteContentPreview = itemView.findViewById(R.id.note_contentPreview);
            noteDate = itemView.findViewById(R.id.note_date);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mClickListener != null) mClickListener.onItemClick(view, getAdapterPosition());
        }

        @Override
        public boolean onLongClick(View view) {
            currentPos = getAdapterPosition();
            return false; // This call is not consuming the long click - that goes to NotesFragment.
        }
    }

    // Convenience method for getting data at click position.
    Note getItem(int id) {
        return mData.get(id);
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

}
