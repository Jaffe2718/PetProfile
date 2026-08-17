package io.github.jaffe2718.petprofile.data;

import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileParentCrossRef;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;

import java.util.ArrayList;
import java.util.List;

public class ExportBundle {
    public String rootProfileId;
    public List<String> descendantIds = new ArrayList<>();
    public List<ProfileEntity> profiles = new ArrayList<>();
    public List<ProfileCustomFieldEntity> customFields = new ArrayList<>();
    public List<ProfileParentCrossRef> parentLinks = new ArrayList<>();
    public List<RecordEntity> records = new ArrayList<>();
    public List<RecordFieldEntity> recordFields = new ArrayList<>();
    public List<RecordImageEntity> recordImages = new ArrayList<>();
}
