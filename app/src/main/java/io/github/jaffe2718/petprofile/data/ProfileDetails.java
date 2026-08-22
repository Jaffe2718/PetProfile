package io.github.jaffe2718.petprofile.data;

import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;

import java.util.ArrayList;
import java.util.List;

public class ProfileDetails {
    public ProfileEntity profile;
    public final List<ProfileCustomFieldEntity> customFields = new ArrayList<>();
    public final List<String> parentIds = new ArrayList<>();
    public final List<ProfileEntity> parents = new ArrayList<>();
    public final List<RoutineEntity> routineWork = new ArrayList<>();
    public String fatherId;
    public String motherId;
    public String establishmentSource;
    public Long establishmentTimestamp;
    public Long lastRecordTimestamp;
}
