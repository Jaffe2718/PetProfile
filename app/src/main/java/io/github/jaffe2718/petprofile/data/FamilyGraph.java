package io.github.jaffe2718.petprofile.data;

import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FamilyGraph {
    public ProfileEntity root;
    public final List<ProfileEntity> ancestors = new ArrayList<>();
    public final List<ProfileEntity> descendants = new ArrayList<>();
    public final Map<String, List<String>> parentIdsByChild = new HashMap<>();
    public final Map<String, List<String>> childIdsByParent = new HashMap<>();

    public List<ProfileEntity> allProfiles() {
        List<ProfileEntity> result = new ArrayList<>();
        result.add(root);
        result.addAll(ancestors);
        result.addAll(descendants);
        return result;
    }
}
