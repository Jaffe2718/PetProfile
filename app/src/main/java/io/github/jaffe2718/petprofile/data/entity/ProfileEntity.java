package io.github.jaffe2718.petprofile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "profiles", indices = {@Index(value = "createdAt")})
public class ProfileEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String kingdom = "";
    public String phylum = "";
    public String taxClass = "";
    public String taxOrder = "";
    public String family = "";
    public String genus = "";
    public String species = "";
    public String subspecies = "";
    @NonNull
    public String gender = "UNKNOWN";

    public String avatarUri;
    public long createdAt;
    public long updatedAt;
    public Long archivedAt;

    public ProfileEntity() {
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
