package io.github.jaffe2718.petprofile.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.FieldType;

import java.util.ArrayList;
import java.util.List;

public class FieldEditorAdapter extends RecyclerView.Adapter<FieldEditorAdapter.Holder> {
    public static final int MODE_PROFILE = 0;
    public static final int MODE_RECORD = 1;

    public interface Listener {
        void onDelete(int position);
    }

    private final List<EditableField> fields = new ArrayList<>();
    private final int mode;
    private final Listener listener;

    public FieldEditorAdapter(int mode, Listener listener) {
        this.mode = mode;
        this.listener = listener;
    }

    public void setFields(List<EditableField> newFields) {
        fields.clear();
        if (newFields != null) {
            fields.addAll(newFields);
        }
        notifyDataSetChanged();
    }

    public List<EditableField> getFields() {
        return new ArrayList<>(fields);
    }

    public void addField(EditableField field) {
        fields.add(field);
        notifyItemInserted(fields.size() - 1);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_field_editor, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(fields.get(position), position);
    }

    @Override
    public int getItemCount() {
        return fields.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final RadioGroup typeRadioGroup;
        private final RadioButton typeFirstRadio;
        private final RadioButton typeSecondRadio;
        private final EditText nameEditText;
        private final EditText valueEditText;
        private final EditText unitEditText;
        private final View unitInputLayout;
        private final ImageButton deleteButton;
        private boolean binding;

        Holder(@NonNull View itemView) {
            super(itemView);
            typeRadioGroup = itemView.findViewById(R.id.typeRadioGroup);
            typeFirstRadio = itemView.findViewById(R.id.typeFirstRadio);
            typeSecondRadio = itemView.findViewById(R.id.typeSecondRadio);
            nameEditText = itemView.findViewById(R.id.nameEditText);
            valueEditText = itemView.findViewById(R.id.valueEditText);
            unitEditText = itemView.findViewById(R.id.unitEditText);
            unitInputLayout = itemView.findViewById(R.id.unitInputLayout);
            deleteButton = itemView.findViewById(R.id.deleteFieldButton);
        }

        void bind(EditableField field, int position) {
            binding = true;
            String[] types = mode == MODE_PROFILE
                    ? new String[]{FieldType.TEXT, FieldType.NUMBER}
                    : new String[]{FieldType.NUMBER, FieldType.TAG};
            if (field.type == null || field.type.trim().isEmpty()) {
                field.type = types[0];
            }
            typeFirstRadio.setText(displayType(types[0]));
            typeSecondRadio.setText(displayType(types[1]));
            typeRadioGroup.check(types[1].equals(field.type)
                    ? R.id.typeSecondRadio
                    : R.id.typeFirstRadio);
            nameEditText.setText(field.name == null ? "" : field.name);
            valueEditText.setText(field.value == null ? "" : field.value);
            unitEditText.setText(field.unit == null ? "" : field.unit);
            updateUnitVisibility(field.type);
            binding = false;

            typeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (binding) {
                    return;
                }
                field.type = checkedId == R.id.typeSecondRadio ? types[1] : types[0];
                updateUnitVisibility(field.type);
            });
            attachTextWatcher(nameEditText, text -> field.name = text);
            attachTextWatcher(valueEditText, text -> field.value = text);
            attachTextWatcher(unitEditText, text -> field.unit = text);
            deleteButton.setOnClickListener(v -> {
                int current = getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION || listener == null) return;
                fields.remove(current);
                notifyItemRemoved(current);
                listener.onDelete(current);
            });
        }

        private void updateUnitVisibility(String type) {
            boolean numeric = FieldType.NUMBER.equals(type);
            unitInputLayout.setVisibility(numeric ? View.VISIBLE : View.GONE);
        }

        private String displayType(String type) {
            if (FieldType.NUMBER.equals(type)) {
                return itemView.getContext().getString(R.string.label_numeric);
            }
            if (FieldType.TAG.equals(type)) {
                return itemView.getContext().getString(R.string.label_tag);
            }
            return itemView.getContext().getString(R.string.label_text);
        }

        private void attachTextWatcher(EditText editText, TextConsumer consumer) {
            TextWatcher old = (TextWatcher) editText.getTag();
            if (old != null) {
                editText.removeTextChangedListener(old);
            }
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!binding) {
                        consumer.accept(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };
            editText.setTag(watcher);
            editText.addTextChangedListener(watcher);
        }
    }

    private interface TextConsumer {
        void accept(String value);
    }
}
