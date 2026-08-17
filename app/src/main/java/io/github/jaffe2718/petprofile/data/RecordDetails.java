package io.github.jaffe2718.petprofile.data;

import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;

import java.util.ArrayList;
import java.util.List;

public class RecordDetails {
    public RecordEntity record;
    public final List<RecordFieldEntity> fields = new ArrayList<>();
    public final List<RecordImageEntity> images = new ArrayList<>();
}
