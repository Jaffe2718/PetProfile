package io.github.jaffe2718.petprofile.ui;

import io.github.jaffe2718.petprofile.data.FieldType;

public class EditableField {
    public String key;
    public String name;
    public String type;
    public String value;
    public String unit;

    public EditableField() {
        this.type = FieldType.TEXT;
    }

    public EditableField(String name, String type) {
        this.name = name;
        this.type = type;
    }
}
