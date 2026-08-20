package io.github.jaffe2718.petprofile.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.util.TaxonomyUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileListAdapter extends RecyclerView.Adapter<ProfileListAdapter.Holder> {
    public interface Listener {
        void onOpen(ProfileDetails details);

        void onEdit(ProfileDetails details);

        void onDelete(ProfileDetails details);
    }

    private final List<ProfileDetails> items = new ArrayList<>();
    private final Listener listener;

    public ProfileListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<ProfileDetails> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile, parent, false);
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
        private final ImageView avatar;
        private final TextView name;
        private final TextView taxonomy;
        private final TextView meta;
        private final LinearLayout attributesSummaryContainer;
        private final TextView attributesSummaryTextView;
        private final TableLayout attributesTable;
        private boolean attributesExpanded;

        Holder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatarImageView);
            name = itemView.findViewById(R.id.nameTextView);
            taxonomy = itemView.findViewById(R.id.taxonomyTextView);
            meta = itemView.findViewById(R.id.metaTextView);
            attributesSummaryContainer = itemView.findViewById(R.id.attributesSummaryContainer);
            attributesSummaryTextView = itemView.findViewById(R.id.attributesSummaryTextView);
            attributesTable = itemView.findViewById(R.id.attributesTable);
        }

        void bind(ProfileDetails details) {
            applyGenderBackground(details);
            applyArchivedTextColor(details);
            name.setText(TaxonomyUtil.displayName(details.profile, details.customFields));
            taxonomy.setText(TaxonomyUtil.speciesDisplay(details.profile));
            meta.setText(buildMeta(details));
            bindAttributes(details);
            if (details.profile.avatarUri != null && !details.profile.avatarUri.trim().isEmpty()) {
                Glide.with(avatar).load(details.profile.avatarUri).into(avatar);
            } else {
                avatar.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            itemView.setOnClickListener(v -> listener.onOpen(details));
            itemView.findViewById(R.id.editButton).setOnClickListener(v -> listener.onEdit(details));
            itemView.findViewById(R.id.deleteButton).setOnClickListener(v -> listener.onDelete(details));
        }

        private void bindAttributes(ProfileDetails details) {
            List<ProfileCustomFieldEntity> attributes = new ArrayList<>();
            for (ProfileCustomFieldEntity field : details.customFields) {
                if (isNicknameField(field)) continue;
                attributes.add(field);
            }
            if (attributes.isEmpty()) {
                attributesSummaryContainer.setVisibility(View.GONE);
                attributesTable.removeAllViews();
                attributesTable.setVisibility(View.GONE);
                attributesExpanded = false;
                return;
            }
            attributesSummaryContainer.setVisibility(View.VISIBLE);
            attributesSummaryTextView.setText(itemView.getContext().getString(R.string.label_profile_attributes)
                    + " (" + attributes.size() + ")");
            attributesTable.removeAllViews();
            addAttributeRow(itemView.getContext().getString(R.string.label_field_name),
                    itemView.getContext().getString(R.string.label_value), true);
            for (ProfileCustomFieldEntity field : attributes) {
                addAttributeRow(field.fieldName, formatAttribute(field), false);
            }
            attributesTable.setVisibility(attributesExpanded ? View.VISIBLE : View.GONE);
            attributesSummaryTextView.setOnClickListener(v -> {
                attributesExpanded = !attributesExpanded;
                attributesTable.setVisibility(attributesExpanded ? View.VISIBLE : View.GONE);
            });
        }

        private void addAttributeRow(String name, String value, boolean header) {
            TableRow row = new TableRow(itemView.getContext());
            TextView nameView = new TextView(itemView.getContext());
            nameView.setText(name == null ? "" : name);
            nameView.setPadding(4, 8, 12, 8);
            if (header) {
                nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
            }
            TextView valueView = new TextView(itemView.getContext());
            valueView.setText(value == null ? "" : value);
            valueView.setPadding(4, 8, 4, 8);
            valueView.setGravity(Gravity.START);
            if (header) {
                valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);
            }
            row.addView(nameView, new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.42f));
            row.addView(valueView, new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.58f));
            attributesTable.addView(row);
        }

        private String formatAttribute(ProfileCustomFieldEntity field) {
            if (io.github.jaffe2718.petprofile.data.FieldType.NUMBER.equals(field.fieldType)) {
                StringBuilder value = new StringBuilder(field.numericValue == null ? "" : String.valueOf(field.numericValue));
                if (field.unit != null && !field.unit.trim().isEmpty()) {
                    value.append(' ').append(field.unit.trim());
                }
                return value.toString();
            }
            return field.textValue == null ? "" : field.textValue;
        }

        private boolean isNicknameField(ProfileCustomFieldEntity field) {
            return "nickname".equalsIgnoreCase(field.fieldKey)
                    || "nickname".equalsIgnoreCase(field.fieldName)
                    || "昵称".equals(field.fieldName)
                    || "暱稱".equals(field.fieldName);
        }

        private void applyGenderBackground(ProfileDetails details) {
            if (!(itemView instanceof MaterialCardView)) {
                return;
            }
            if (details.profile.isArchived()) {
                ((MaterialCardView) itemView).setCardBackgroundColor(
                        itemView.getContext().getColor(R.color.profile_archived_bg));
                return;
            }
            String gender = details.profile.gender;
            int color;
            if ("MALE".equals(gender)) {
                color = itemView.getContext().getColor(R.color.profile_male_bg);
            } else if ("FEMALE".equals(gender)) {
                color = itemView.getContext().getColor(R.color.profile_female_bg);
            } else {
                color = itemView.getContext().getColor(R.color.profile_unknown_bg);
            }
            ((MaterialCardView) itemView).setCardBackgroundColor(color);
        }

        private void applyArchivedTextColor(ProfileDetails details) {
            if (details.profile.isArchived()) {
                int archivedText = itemView.getContext().getColor(R.color.profile_archived_text);
                name.setTextColor(archivedText);
                taxonomy.setTextColor(archivedText);
                meta.setTextColor(archivedText);
            } else {
                name.setTextColor(itemView.getContext().getColor(R.color.text_primary));
                taxonomy.setTextColor(itemView.getContext().getColor(R.color.text_primary));
                meta.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
            }
        }

        private String buildMeta(ProfileDetails details) {
            List<String> parts = new ArrayList<>();
            String gender = details.profile.gender;
            if ("MALE".equals(gender)) {
                parts.add("♂");
            } else if ("FEMALE".equals(gender)) {
                parts.add("♀");
            }
            for (ProfileCustomFieldEntity field : details.customFields) {
                if ("origin".equalsIgnoreCase(field.fieldKey)
                        || "产地".equals(field.fieldName)
                        || "sex".equalsIgnoreCase(field.fieldKey)
                        || "性别".equals(field.fieldName)) {
                    String value = field.textValue;
                    if (value != null && !value.trim().isEmpty()) {
                        parts.add(field.fieldName + ": " + value);
                    }
                }
            }
            String source = sourceLabel(details.establishmentSource);
            if (source != null) {
                parts.add(source);
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            long displayTime = details.establishmentTimestamp != null
                    ? details.establishmentTimestamp
                    : details.profile.createdAt;
            parts.add(format.format(new Date(displayTime)));
            if (details.profile.isArchived()) {
                parts.add(itemView.getContext().getString(R.string.status_archived));
            }
            return String.join(" · ", parts);
        }

        private String sourceLabel(String source) {
            if ("WILD".equals(source)) {
                return itemView.getContext().getString(R.string.record_establishment_source_wild);
            }
            if ("PURCHASE".equals(source)) {
                return itemView.getContext().getString(R.string.record_establishment_source_purchase);
            }
            if ("BREED".equals(source)) {
                return itemView.getContext().getString(R.string.record_establishment_source_breed);
            }
            return null;
        }
    }
}
