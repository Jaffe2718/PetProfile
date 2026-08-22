package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jaffe2718.petprofile.R;

public class MarkdownImageStrip extends RecyclerView {
    public interface Listener {
        void onInsertImage(String uri);

        void onDeleteRequest(int position, String uri);

        void onOrderChanged(List<String> uris);
    }

    private final List<String> imageUris = new ArrayList<>();
    private final ImageAdapter adapter;
    private Listener listener;
    private boolean deleteMode;
    private ItemTouchHelper itemTouchHelper;
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MarkdownImageStrip(Context context) {
        this(context, null);
    }

    public MarkdownImageStrip(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MarkdownImageStrip(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        setClipToPadding(false);
        adapter = new ImageAdapter();
        setAdapter(adapter);
        itemTouchHelper = new ItemTouchHelper(new DragCallback());
        itemTouchHelper.attachToRecyclerView(this);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(this, 3));
        glowPaint.setColor(Color.rgb(145, 192, 172));
        glowPaint.setShadowLayer(dp(this, 12), 0f, 0f, Color.argb(110, 145, 192, 172));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setImages(List<String> uris) {
        imageUris.clear();
        if (uris != null) {
            imageUris.addAll(uris);
        }
        adapter.notifyDataSetChanged();
    }

    public List<String> getImages() {
        return new ArrayList<>(imageUris);
    }

    public void setDeleteMode(boolean enabled) {
        deleteMode = enabled;
        adapter.notifyDataSetChanged();
    }

    public boolean isDeleteMode() {
        return deleteMode;
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageHolder> {
        @NonNull
        @Override
        public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            int size = dp(parent, 72);
            FrameLayout item = new FrameLayout(context);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(size, size);
            params.setMarginStart(dp(parent, 8));
            item.setLayoutParams(params);

            ImageView thumb = new ImageView(context);
            thumb.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundResource(R.drawable.bg_thumb_rounded);
            thumb.setClipToOutline(true);
            item.addView(thumb);

            ImageView badge = new ImageView(context);
            badge.setImageResource(R.drawable.ic_trash);
            badge.setColorFilter(Color.rgb(255, 105, 105));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    dp(parent, 28),
                    dp(parent, 28),
                    Gravity.CENTER
            );
            badge.setLayoutParams(badgeParams);
            item.addView(badge);

            return new ImageHolder(item, thumb, badge);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
            holder.bind(imageUris.get(position), position);
        }

        @Override
        public int getItemCount() {
            return imageUris.size();
        }
    }

    private class ImageHolder extends RecyclerView.ViewHolder {
        private final ImageView thumb;
        private final ImageView badge;
        private int index;
        private String uri;

        ImageHolder(@NonNull View itemView, ImageView thumb, ImageView badge) {
            super(itemView);
            this.thumb = thumb;
            this.badge = badge;
            itemView.setOnClickListener(v -> {
                if (deleteMode) {
                    if (listener != null) {
                        listener.onDeleteRequest(index, uri);
                    }
                } else if (listener != null) {
                    listener.onInsertImage(uri);
                }
            });
        }

        void bind(String currentUri, int position) {
            index = position;
            uri = currentUri;
            thumb.setAlpha(deleteMode ? 0.45f : 1f);
            badge.setVisibility(deleteMode ? View.VISIBLE : View.GONE);
            Glide.with(itemView).load(currentUri).into(thumb);
        }
    }

    private class DragCallback extends ItemTouchHelper.Callback {
        @Override
        public boolean isLongPressDragEnabled() {
            return !deleteMode && imageUris.size() > 1;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = source.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from < 0 || to < 0) {
                return false;
            }
            Collections.swap(imageUris, from, to);
            adapter.notifyItemMoved(from, to);
            if (listener != null) {
                listener.onOrderChanged(getImages());
            }
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void onChildDrawOver(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
            super.onChildDrawOver(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                RectF bounds = new RectF(
                        itemView.getLeft() + dX,
                        itemView.getTop(),
                        itemView.getRight() + dX,
                        itemView.getBottom()
                );
                c.drawRoundRect(bounds, dp(itemView, 16), dp(itemView, 16), glowPaint);
            }
        }

        @Override
        public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, 0f, actionState, isCurrentlyActive);
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) {
                return;
            }
            int width = recyclerView.getWidth();
            if (dX < -width * 0.2f) {
                recyclerView.scrollBy(-12, 0);
            } else if (dX > width * 0.2f) {
                recyclerView.scrollBy(12, 0);
            }
        }
    }

    private int dp(View view, int value) {
        return Math.round(view.getResources().getDisplayMetrics().density * value);
    }
}
