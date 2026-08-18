package io.github.jaffe2718.petprofile.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.bumptech.glide.Glide;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.Holder> {
    public interface Listener {
        void onOpen(RecordEntity record);

        void onDelete(RecordEntity record);
    }

    private final List<RecordEntity> items = new ArrayList<>();
    private final Listener listener;
    private Map<String, List<String>> imageUrisByRecord = new HashMap<>();

    public RecordAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<RecordEntity> newItems, Map<String, List<String>> imageUrisByRecord) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        this.imageUrisByRecord = imageUrisByRecord == null ? new HashMap<>() : imageUrisByRecord;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final TextView type;
        private final TextView time;
        private final TextView title;
        private final TextView summary;
        private final Button deleteButton;
        private final LinearLayout thumbnailsContainer;

        Holder(@NonNull View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.typeTextView);
            time = itemView.findViewById(R.id.timeTextView);
            title = itemView.findViewById(R.id.titleTextView);
            summary = itemView.findViewById(R.id.summaryTextView);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            thumbnailsContainer = itemView.findViewById(R.id.thumbnailsContainer);
        }

        void bind(RecordEntity record) {
            applyTypeBackground(record.type);
            String typeText = typeLabel(record);
            type.setText(typeText);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            time.setText(format.format(new Date(record.timestamp)));
            title.setText(record.title == null || record.title.trim().isEmpty()
                    ? typeText
                    : record.title);
            summary.setText(buildSummary(record));
            renderThumbnails(record.id);
            boolean establishment = RecordType.ESTABLISHMENT.equals(record.type);
            deleteButton.setEnabled(!establishment);
            deleteButton.setAlpha(establishment ? 0.4f : 1f);
            deleteButton.setOnClickListener(v -> listener.onDelete(record));
            itemView.setOnClickListener(v -> listener.onOpen(record));
        }

        private void renderThumbnails(String recordId) {
            thumbnailsContainer.removeAllViews();
            List<String> uris = imageUrisByRecord.get(recordId);
            if (uris == null || uris.isEmpty()) {
                return;
            }
            int count = Math.min(3, uris.size());
            int size = dp(52);
            for (int i = 0; i < count; i++) {
                ImageView image = new ImageView(itemView.getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                if (i > 0) {
                    params.setMargins(dp(4), 0, 0, 0);
                }
                image.setLayoutParams(params);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackgroundResource(R.drawable.bg_thumb_rounded);
                image.setClipToOutline(true);
                Glide.with(itemView).load(uris.get(i)).into(image);
                thumbnailsContainer.addView(image);
            }
        }

        private int dp(int value) {
            return Math.round(itemView.getResources().getDisplayMetrics().density * value);
        }

        private void applyTypeBackground(String type) {
            if (!(itemView instanceof MaterialCardView)) {
                return;
            }
            int color;
            switch (type) {
                case RecordType.ESTABLISHMENT:
                    color = itemView.getContext().getColor(R.color.record_establishment_bg);
                    break;
                case RecordType.TRANSFER:
                    color = itemView.getContext().getColor(R.color.record_transfer_bg);
                    break;
                case RecordType.ARCHIVE:
                    color = itemView.getContext().getColor(R.color.record_archive_bg);
                    break;
                default:
                    color = itemView.getContext().getColor(R.color.record_daily_bg);
                    break;
            }
            ((MaterialCardView) itemView).setCardBackgroundColor(color);
        }

        private String typeLabel(RecordEntity record) {
            String type = record.type;
            switch (type) {
                case RecordType.ESTABLISHMENT:
                    return itemView.getContext().getString(R.string.record_establishment);
                case RecordType.DAILY:
                    return itemView.getContext().getString(R.string.record_daily);
                case RecordType.TRANSFER:
                    return itemView.getContext().getString(R.string.record_transfer);
                case RecordType.ARCHIVE:
                    String reason = "TRANSFER".equals(record.archiveReason)
                            ? itemView.getContext().getString(R.string.record_archive_transfer)
                            : itemView.getContext().getString(R.string.record_archive_death);
                    return itemView.getContext().getString(R.string.record_archive) + " - " + reason;
                default:
                    return type;
            }
        }

        private String buildSummary(RecordEntity record) {
            StringBuilder builder = new StringBuilder();
            if (record.locationName != null && !record.locationName.trim().isEmpty()) {
                builder.append(record.locationName);
            }
            if (RecordType.TRANSFER.equals(record.type)) {
                if (record.transferFromPerson != null && !record.transferFromPerson.isEmpty()) {
                    append(builder, record.transferFromPerson);
                }
                if (record.transferToPerson != null && !record.transferToPerson.isEmpty()) {
                    append(builder, "→ " + record.transferToPerson);
                }
            }
            return builder.toString();
        }

        private void append(StringBuilder builder, String value) {
            if (value == null || value.isEmpty()) return;
            if (builder.length() > 0) builder.append(" · ");
            builder.append(value);
        }
    }
}
