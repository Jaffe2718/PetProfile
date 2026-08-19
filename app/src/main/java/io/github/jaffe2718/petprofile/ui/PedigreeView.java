package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.util.TaxonomyUtil;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class PedigreeView extends View {
    private static final float NODE_SIZE = 112f;
    private static final float HORIZONTAL_SPACING = 168f;
    private static final float ROW_HEIGHT = 250f;
    private static final float TOP_PADDING = 70f;
    private static final float LEFT_PADDING = NODE_SIZE / 2f + 40f;
    private static final float RIGHT_PADDING = NODE_SIZE / 2f + 40f;
    private static final float BOTTOM_PADDING = 110f;
    private static final float NICKNAME_OFFSET = 24f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, ProfileDetails> profileMap = new HashMap<>();
    private final Map<String, Node> nodeById = new HashMap<>();
    private final Map<String, Integer> generationMap = new HashMap<>();
    private final Map<String, String> fatherByChild = new HashMap<>();
    private final Map<String, String> motherByChild = new HashMap<>();
    private final Map<String, Family> familyOfOrigin = new HashMap<>();
    private final Map<String, List<Family>> familiesByFather = new HashMap<>();
    private final Map<String, List<Family>> familiesByMother = new HashMap<>();
    private final List<Family> families = new ArrayList<>();
    private final Map<String, Bitmap> avatarCache = new HashMap<>();

    private float scrollX;
    private float scrollY;
    private float lastTouchX;
    private float lastTouchY;
    private float downX;
    private float downY;
    private boolean dragging;
    private int touchSlop;
    private int desiredWidth;
    private int desiredHeight;

    public PedigreeView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    public PedigreeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    public PedigreeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    private void init() {
        linePaint.setColor(Color.rgb(76, 130, 84));
        linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE);
        dotPaint.setColor(Color.rgb(39, 92, 48));
        dotPaint.setStyle(Paint.Style.FILL);
        borderPaint.setColor(Color.rgb(46, 90, 54));
        borderPaint.setStrokeWidth(5f);
        borderPaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(Color.rgb(30, 30, 30));
        textPaint.setTextSize(34f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        placeholderPaint.setColor(Color.rgb(224, 236, 229));
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);
    }

    public void setProfiles(List<ProfileDetails> newProfiles) {
        clearGraph();
        if (newProfiles == null || newProfiles.isEmpty()) {
            desiredWidth = Math.max(1, getWidth());
            desiredHeight = Math.max(1, getHeight());
            requestLayout();
            invalidate();
            return;
        }
        for (ProfileDetails details : newProfiles) {
            if (details == null || details.profile == null || details.profile.id == null) {
                continue;
            }
            profileMap.put(details.profile.id, details);
        }
        buildRelations();
        computeGenerations();
        buildFamilies();
        layoutGraph();
        loadAvatarsAsync();
        requestLayout();
        invalidate();
    }

    private void clearGraph() {
        nodes.clear();
        profileMap.clear();
        nodeById.clear();
        generationMap.clear();
        fatherByChild.clear();
        motherByChild.clear();
        familyOfOrigin.clear();
        familiesByFather.clear();
        familiesByMother.clear();
        families.clear();
        avatarCache.clear();
        scrollX = 0f;
        scrollY = 0f;
    }

    private void computeGenerations() {
        generationMap.clear();
        for (String id : profileMap.keySet()) {
            computeGeneration(id, new HashSet<>());
        }
    }

    private int computeGeneration(String id, Set<String> visiting) {
        Integer cached = generationMap.get(id);
        if (cached != null) {
            return cached;
        }
        ProfileDetails details = profileMap.get(id);
        if (details == null || visiting.contains(id)) {
            generationMap.put(id, 0);
            return 0;
        }
        visiting.add(id);
        int maxParentGeneration = 0;
        String father = fatherByChild.get(id);
        String mother = motherByChild.get(id);
        if (father != null) {
            maxParentGeneration = Math.max(maxParentGeneration, computeGeneration(father, visiting));
        }
        if (mother != null) {
            maxParentGeneration = Math.max(maxParentGeneration, computeGeneration(mother, visiting));
        }
        visiting.remove(id);
        int generation = (father == null && mother == null) ? 0 : maxParentGeneration + 1;
        generationMap.put(id, generation);
        return generation;
    }

    private void buildRelations() {
        fatherByChild.clear();
        motherByChild.clear();
        for (ProfileDetails details : profileMap.values()) {
            String childId = details.profile.id;
            String father = presentParent(resolveFather(details));
            String mother = presentParent(resolveMother(details));
            if (father == null && mother == null && details.parentIds != null && details.parentIds.size() >= 2) {
                List<String> sorted = new ArrayList<>(details.parentIds);
                Collections.sort(sorted);
                father = presentParent(sorted.get(0));
                mother = presentParent(sorted.get(1));
            }
            fatherByChild.put(childId, father);
            motherByChild.put(childId, mother);
        }
    }

    private String resolveFather(ProfileDetails details) {
        if (details.fatherId != null && !details.fatherId.trim().isEmpty()) {
            return details.fatherId;
        }
        return findByGender(details, "MALE");
    }

    private String resolveMother(ProfileDetails details) {
        if (details.motherId != null && !details.motherId.trim().isEmpty()) {
            return details.motherId;
        }
        return findByGender(details, "FEMALE");
    }

    private String findByGender(ProfileDetails details, String gender) {
        if (details.parentIds == null) {
            return null;
        }
        for (String parentId : details.parentIds) {
            ProfileDetails parent = profileMap.get(parentId);
            if (parent != null && gender.equalsIgnoreCase(parent.profile.gender)) {
                return parentId;
            }
        }
        return null;
    }

    private String presentParent(String parentId) {
        if (parentId == null || parentId.isEmpty() || !profileMap.containsKey(parentId)) {
            return null;
        }
        return parentId;
    }

    private void buildFamilies() {
        families.clear();
        familyOfOrigin.clear();
        familiesByFather.clear();
        familiesByMother.clear();
        Map<String, Family> familyByKey = new LinkedHashMap<>();
        for (ProfileDetails details : profileMap.values()) {
            String childId = details.profile.id;
            String father = fatherByChild.get(childId);
            String mother = motherByChild.get(childId);
            if (father == null && mother == null) {
                continue;
            }
            String key = familyKey(father, mother);
            Family family = familyByKey.get(key);
            if (family == null) {
                family = new Family();
                family.fatherId = father;
                family.motherId = mother;
                familyByKey.put(key, family);
                families.add(family);
            }
            family.childIds.add(childId);
            familyOfOrigin.put(childId, family);
        }
        for (Family family : families) {
            family.childIds.sort(Comparator.comparingLong((String id) -> {
                ProfileDetails details = profileMap.get(id);
                return details == null ? 0L : details.profile.createdAt;
            }).thenComparing(id -> id == null ? "" : id));
            family.childGeneration = generationMap.getOrDefault(family.childIds.get(0), 0);
            family.minChildCreatedAt = Long.MAX_VALUE;
            for (String childId : family.childIds) {
                ProfileDetails details = profileMap.get(childId);
                if (details != null) {
                    family.minChildCreatedAt = Math.min(family.minChildCreatedAt, details.profile.createdAt);
                }
            }
            if (family.fatherId != null) {
                familiesByFather.computeIfAbsent(family.fatherId, ignored -> new ArrayList<>()).add(family);
            }
            if (family.motherId != null) {
                familiesByMother.computeIfAbsent(family.motherId, ignored -> new ArrayList<>()).add(family);
            }
        }
        families.sort(Comparator.comparingInt((Family f) -> f.childGeneration)
                .thenComparingLong(f -> f.minChildCreatedAt)
                .thenComparing(f -> familyKey(f.fatherId, f.motherId)));
        for (int i = 0; i < families.size(); i++) {
            families.get(i).sortIndex = i;
        }
    }

    private String familyKey(String fatherId, String motherId) {
        return (fatherId == null ? "" : fatherId) + "\u0000" + (motherId == null ? "" : motherId);
    }

    private void layoutGraph() {
        nodes.clear();
        nodeById.clear();
        if (profileMap.isEmpty()) {
            desiredWidth = Math.max(1, getWidth());
            desiredHeight = Math.max(1, getHeight());
            return;
        }
        for (String id : profileMap.keySet()) {
            Node node = new Node();
            node.details = profileMap.get(id);
            node.generation = generationMap.getOrDefault(id, 0);
            nodes.add(node);
            nodeById.put(id, node);
        }

        List<Family> unitOrder = orderFamilyUnits();
        List<String> gen0Order = orderGeneration0(unitOrder);
        Map<Integer, List<Family>> blocksByGeneration = buildGenerationBlocks(unitOrder);
        Map<String, Integer> gridX = solveConstraints(gen0Order, blocksByGeneration);

        int minGrid = Integer.MAX_VALUE;
        int maxGrid = Integer.MIN_VALUE;
        int maxGeneration = 0;
        for (Node node : nodes) {
            Integer x = gridX.get(node.details.profile.id);
            node.gridX = x == null ? 0 : x;
            minGrid = Math.min(minGrid, node.gridX);
            maxGrid = Math.max(maxGrid, node.gridX);
            maxGeneration = Math.max(maxGeneration, node.generation);
        }
        for (Node node : nodes) {
            node.gridX -= minGrid;
            node.centerX = LEFT_PADDING + node.gridX * HORIZONTAL_SPACING;
            node.updateBounds();
        }

        float maxRight = 0f;
        for (Node node : nodes) {
            maxRight = Math.max(maxRight, node.bounds.right);
        }
        desiredWidth = Math.max(getWidth(), (int) (maxRight + RIGHT_PADDING));
        desiredHeight = Math.max(getHeight(), (int) (TOP_PADDING + (maxGeneration + 1) * ROW_HEIGHT + BOTTOM_PADDING));
    }

    private List<Family> orderFamilyUnits() {
        Map<Family, List<Family>> outgoing = new HashMap<>();
        Map<Family, Integer> indegree = new HashMap<>();
        for (Family family : families) {
            indegree.putIfAbsent(family, 0);
        }
        for (Family family : families) {
            Family fatherFamily = family.fatherId == null ? null : familyOfOrigin.get(family.fatherId);
            if (fatherFamily != null && fatherFamily != family) {
                addFamilyEdge(outgoing, indegree, fatherFamily, family);
            }
            for (String childId : family.childIds) {
                List<Family> asFather = familiesByFather.get(childId);
                if (asFather != null) {
                    for (Family childFamily : asFather) {
                        if (childFamily != family) {
                            addFamilyEdge(outgoing, indegree, family, childFamily);
                        }
                    }
                }
                List<Family> asMother = familiesByMother.get(childId);
                if (asMother != null) {
                    for (Family childFamily : asMother) {
                        if (childFamily != family) {
                            addFamilyEdge(outgoing, indegree, childFamily, family);
                        }
                    }
                }
            }
            Family motherFamily = family.motherId == null ? null : familyOfOrigin.get(family.motherId);
            if (motherFamily != null && motherFamily != family) {
                addFamilyEdge(outgoing, indegree, family, motherFamily);
            }
        }

        PriorityQueue<Family> queue = new PriorityQueue<>(
                Comparator.comparingInt((Family f) -> f.sortIndex).thenComparing(f -> familyKey(f.fatherId, f.motherId)));
        for (Family family : families) {
            if (indegree.getOrDefault(family, 0) == 0) {
                queue.add(family);
            }
        }
        List<Family> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Family current = queue.poll();
            result.add(current);
            List<Family> children = outgoing.get(current);
            if (children != null) {
                List<Family> sorted = new ArrayList<>(children);
                sorted.sort(Comparator.comparingInt((Family f) -> f.sortIndex)
                        .thenComparing(f -> familyKey(f.fatherId, f.motherId)));
                for (Family child : sorted) {
                    int next = indegree.get(child) - 1;
                    indegree.put(child, next);
                    if (next == 0) {
                        queue.add(child);
                    }
                }
            }
        }
        if (result.size() != families.size()) {
            return new ArrayList<>(families);
        }
        return result;
    }

    private void addFamilyEdge(Map<Family, List<Family>> outgoing, Map<Family, Integer> indegree,
                               Family from, Family to) {
        outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
        indegree.put(to, indegree.getOrDefault(to, 0) + 1);
    }

    private List<String> orderGeneration0(List<Family> unitOrder) {
        List<String> gen0 = new ArrayList<>();
        for (String id : profileMap.keySet()) {
            if (generationMap.getOrDefault(id, 0) == 0) {
                gen0.add(id);
            }
        }
        Map<String, Integer> anchorOrder = new HashMap<>();
        for (int i = 0; i < unitOrder.size(); i++) {
            Family family = unitOrder.get(i);
            if (family.fatherId != null) {
                anchorOrder.putIfAbsent(family.fatherId, i);
            }
            if (family.motherId != null) {
                anchorOrder.putIfAbsent(family.motherId, i);
            }
        }
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (Family family : unitOrder) {
            if (family.fatherId == null || family.motherId == null) {
                continue;
            }
            if (generationMap.getOrDefault(family.fatherId, -1) == 0
                    && generationMap.getOrDefault(family.motherId, -1) == 0) {
                outgoing.computeIfAbsent(family.fatherId, ignored -> new ArrayList<>()).add(family.motherId);
                indegree.put(family.motherId, indegree.getOrDefault(family.motherId, 0) + 1);
            }
        }
        PriorityQueue<String> queue = new PriorityQueue<>(
                Comparator.comparingInt((String id) -> anchorOrder.getOrDefault(id, Integer.MAX_VALUE))
                        .thenComparing(id -> id == null ? "" : id));
        for (String id : gen0) {
            if (indegree.getOrDefault(id, 0) == 0) {
                queue.add(id);
            }
        }
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            List<String> children = outgoing.get(current);
            if (children != null) {
                List<String> sorted = new ArrayList<>(children);
                sorted.sort(Comparator.comparingInt((String id) -> anchorOrder.getOrDefault(id, Integer.MAX_VALUE))
                        .thenComparing(id -> id == null ? "" : id));
                for (String child : sorted) {
                    int next = indegree.get(child) - 1;
                    indegree.put(child, next);
                    if (next == 0) {
                        queue.add(child);
                    }
                }
            }
        }
        for (String id : gen0) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private Map<Integer, List<Family>> buildGenerationBlocks(List<Family> unitOrder) {
        Map<Integer, List<Family>> blocks = new HashMap<>();
        for (Family family : unitOrder) {
            blocks.computeIfAbsent(family.childGeneration, ignored -> new ArrayList<>()).add(family);
        }
        return blocks;
    }

    private Map<String, Integer> solveConstraints(List<String> gen0Order,
                                                  Map<Integer, List<Family>> blocksByGeneration) {
        List<ConstraintEdge> edges = new ArrayList<>();
        for (int i = 0; i < gen0Order.size() - 1; i++) {
            edges.add(new ConstraintEdge(gen0Order.get(i), gen0Order.get(i + 1), 1));
        }
        int maxGeneration = 0;
        for (Node node : nodes) {
            maxGeneration = Math.max(maxGeneration, node.generation);
        }
        for (int generation = 1; generation <= maxGeneration; generation++) {
            List<Family> blocks = blocksByGeneration.get(generation);
            if (blocks == null || blocks.isEmpty()) {
                continue;
            }
            for (Family family : blocks) {
                List<String> children = family.childIds;
                for (int i = 0; i < children.size() - 1; i++) {
                    edges.add(new ConstraintEdge(children.get(i), children.get(i + 1), 1));
                }
                if (family.fatherId != null) {
                    edges.add(new ConstraintEdge(family.fatherId, children.get(0), 1));
                }
                if (family.motherId != null) {
                    edges.add(new ConstraintEdge(children.get(children.size() - 1), family.motherId, 1));
                }
            }
            for (int i = 0; i < blocks.size() - 1; i++) {
                Family left = blocks.get(i);
                Family right = blocks.get(i + 1);
                String leftLast = left.childIds.get(left.childIds.size() - 1);
                String rightFirst = right.childIds.get(0);
                edges.add(new ConstraintEdge(leftLast, rightFirst, 1));
            }
        }
        return solveLongestPaths(edges);
    }

    private Map<String, Integer> solveLongestPaths(List<ConstraintEdge> edges) {
        Map<String, List<ConstraintEdge>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Set<String> allIds = new HashSet<>(profileMap.keySet());
        for (String id : allIds) {
            indegree.put(id, 0);
        }
        for (ConstraintEdge edge : edges) {
            outgoing.computeIfAbsent(edge.from, ignored -> new ArrayList<>()).add(edge);
            indegree.put(edge.to, indegree.getOrDefault(edge.to, 0) + 1);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String id : allIds) {
            if (indegree.get(id) == 0) {
                queue.add(id);
            }
        }
        List<String> topo = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            topo.add(current);
            List<ConstraintEdge> children = outgoing.get(current);
            if (children != null) {
                for (ConstraintEdge edge : children) {
                    int next = indegree.get(edge.to) - 1;
                    indegree.put(edge.to, next);
                    if (next == 0) {
                        queue.add(edge.to);
                    }
                }
            }
        }
        Map<String, Integer> x = new HashMap<>();
        for (String id : allIds) {
            x.put(id, 0);
        }
        if (topo.size() == allIds.size()) {
            for (String current : topo) {
                List<ConstraintEdge> children = outgoing.get(current);
                if (children == null) {
                    continue;
                }
                int currentX = x.get(current);
                for (ConstraintEdge edge : children) {
                    int nextX = currentX + edge.weight;
                    if (nextX > x.get(edge.to)) {
                        x.put(edge.to, nextX);
                    }
                }
            }
        } else {
            int passes = Math.min(400, allIds.size() * 6);
            for (int pass = 0; pass < passes; pass++) {
                boolean changed = false;
                for (ConstraintEdge edge : edges) {
                    int nextX = x.get(edge.from) + edge.weight;
                    if (nextX > x.get(edge.to)) {
                        x.put(edge.to, Math.min(nextX, 1_000_000));
                        changed = true;
                    }
                }
                if (!changed) {
                    break;
                }
            }
        }
        int min = Integer.MAX_VALUE;
        for (Integer value : x.values()) {
            min = Math.min(min, value);
        }
        for (Map.Entry<String, Integer> entry : x.entrySet()) {
            entry.setValue(entry.getValue() - min);
        }
        return x;
    }

    private void loadAvatarsAsync() {
        avatarCache.clear();
        for (Node node : nodes) {
            node.bitmap = null;
        }
        List<Node> snapshot = new ArrayList<>(nodes);
        new Thread(() -> {
            for (Node node : snapshot) {
                String uriText = node.details.profile.avatarUri;
                if (uriText != null && !uriText.trim().isEmpty()) {
                    node.bitmap = decodeAvatar(uriText);
                }
            }
            postInvalidate();
        }).start();
    }

    private Bitmap decodeAvatar(String uriText) {
        try {
            Uri uri = Uri.parse(uriText);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                try (InputStream input = getContext().getContentResolver().openInputStream(uri)) {
                    if (input == null) {
                        return null;
                    }
                    BitmapFactory.decodeStream(input, null, options);
                }
            } else {
                BitmapFactory.decodeFile(uri.getPath(), options);
            }
            options.inSampleSize = calculateSampleSize(options, (int) NODE_SIZE);
            options.inJustDecodeBounds = false;
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                try (InputStream input = getContext().getContentResolver().openInputStream(uri)) {
                    if (input == null) {
                        return null;
                    }
                    return BitmapFactory.decodeStream(input, null, options);
                }
            } else {
                return BitmapFactory.decodeFile(uri.getPath(), options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private int calculateSampleSize(BitmapFactory.Options options, int requestedSize) {
        int sample = 1;
        int width = options.outWidth;
        int height = options.outHeight;
        while (width / 2 >= requestedSize || height / 2 >= requestedSize) {
            width /= 2;
            height /= 2;
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(-scrollX, -scrollY);
        drawFamilyLines(canvas);
        for (Node node : nodes) {
            drawNode(canvas, node);
        }
        canvas.restore();
    }

    private void drawFamilyLines(Canvas canvas) {
        Map<Integer, List<Family>> twoParentByGeneration = new HashMap<>();
        List<Family> singleParentFamilies = new ArrayList<>();
        for (Family family : families) {
            if (family.fatherId != null && family.motherId != null) {
                twoParentByGeneration.computeIfAbsent(family.childGeneration, ignored -> new ArrayList<>()).add(family);
            } else {
                singleParentFamilies.add(family);
            }
        }

        List<Family> twoParentFamilies = new ArrayList<>();
        for (Map.Entry<Integer, List<Family>> entry : twoParentByGeneration.entrySet()) {
            List<Family> group = entry.getValue();
            group.sort(Comparator.comparingDouble(this::familySpan)
                    .thenComparing(f -> familyKey(f.fatherId, f.motherId)));

            int childGeneration = entry.getKey();
            float childTop = centerYForGeneration(childGeneration) - NODE_SIZE / 2f;
            float parentBottom = Float.MIN_VALUE;
            for (Family family : group) {
                parentBottom = Math.max(parentBottom,
                        centerYForGeneration(parentGenerationMax(family)) + NODE_SIZE / 2f);
            }
            float base = parentBottom + Math.max(18f, (childTop - parentBottom) / 2f);
            int count = group.size();
            for (int i = 0; i < count; i++) {
                Family family = group.get(i);
                int order = count - 1 - i;
                family.lineY = matingLineY(base, parentBottom, order);
                twoParentFamilies.add(family);
            }
        }

        drawParentTrunks(canvas, twoParentFamilies);
        for (Family family : twoParentFamilies) {
            drawMarriageLine(canvas, family);
        }
        for (Family family : twoParentFamilies) {
            drawChildConnections(canvas, family);
        }
        for (Family family : singleParentFamilies) {
            drawSingleParentFamily(canvas, family);
        }
    }

    private void drawParentTrunks(Canvas canvas, List<Family> families) {
        Map<String, Float> trunkTop = new HashMap<>();
        Map<String, Float> trunkBottom = new HashMap<>();
        for (Family family : families) {
            addParentTrunkPoint(trunkTop, trunkBottom, family.fatherId, family.lineY);
            addParentTrunkPoint(trunkTop, trunkBottom, family.motherId, family.lineY);
        }
        for (Map.Entry<String, Float> entry : trunkTop.entrySet()) {
            Node parent = nodeById.get(entry.getKey());
            Float bottom = trunkBottom.get(entry.getKey());
            if (parent == null || bottom == null) {
                continue;
            }
            canvas.drawLine(parent.centerX, entry.getValue(), parent.centerX, bottom, linePaint);
        }
        for (Family family : families) {
            drawParentLineDot(canvas, family.fatherId, family.lineY);
            drawParentLineDot(canvas, family.motherId, family.lineY);
        }
    }

    private void addParentTrunkPoint(Map<String, Float> trunkTop, Map<String, Float> trunkBottom,
                                     String parentId, float lineY) {
        if (parentId == null) {
            return;
        }
        Node parent = nodeById.get(parentId);
        if (parent == null) {
            return;
        }
        trunkTop.putIfAbsent(parentId, parent.bounds.bottom);
        trunkBottom.merge(parentId, lineY, Math::max);
    }

    private void drawParentLineDot(Canvas canvas, String parentId, float lineY) {
        if (parentId == null) {
            return;
        }
        Node parent = nodeById.get(parentId);
        if (parent != null) {
            canvas.drawCircle(parent.centerX, lineY, 8f, dotPaint);
        }
    }

    private void drawMarriageLine(Canvas canvas, Family family) {
        Node father = family.fatherId == null ? null : nodeById.get(family.fatherId);
        Node mother = family.motherId == null ? null : nodeById.get(family.motherId);
        if (father == null || mother == null) {
            return;
        }
        float mateX = familyChildMetrics(family)[2];
        canvas.drawLine(father.centerX, family.lineY, mother.centerX, family.lineY, linePaint);
        canvas.drawCircle(mateX, family.lineY, 8f, dotPaint);
    }

    private void drawChildConnections(Canvas canvas, Family family) {
        float[] metrics = familyChildMetrics(family);
        float minChildX = metrics[0];
        float maxChildX = metrics[1];
        float mateX = metrics[2];
        if (maxChildX < minChildX) {
            return;
        }
        float siblingY = siblingLineY(family);
        canvas.drawLine(mateX, family.lineY, mateX, siblingY, linePaint);
        canvas.drawLine(minChildX, siblingY, maxChildX, siblingY, linePaint);
        for (String childId : family.childIds) {
            Node child = nodeById.get(childId);
            if (child == null) {
                continue;
            }
            canvas.drawLine(child.centerX, siblingY, child.centerX, child.bounds.top, linePaint);
            canvas.drawCircle(child.centerX, siblingY, 8f, dotPaint);
        }
        canvas.drawCircle(mateX, siblingY, 8f, dotPaint);
    }

    private void drawSingleParentFamily(Canvas canvas, Family family) {
        Node parent = family.fatherId != null ? nodeById.get(family.fatherId) : nodeById.get(family.motherId);
        if (parent == null) {
            return;
        }
        float[] metrics = familyChildMetrics(family);
        float minChildX = metrics[0];
        float maxChildX = metrics[1];
        float mateX = metrics[2];
        if (maxChildX < minChildX) {
            return;
        }
        float siblingY = siblingLineY(family);
        canvas.drawLine(parent.centerX, parent.bounds.bottom, parent.centerX, siblingY, linePaint);
        if (family.fatherId != null) {
            canvas.drawLine(parent.centerX, siblingY, maxChildX, siblingY, linePaint);
        } else {
            canvas.drawLine(minChildX, siblingY, parent.centerX, siblingY, linePaint);
        }
        canvas.drawCircle(parent.centerX, siblingY, 8f, dotPaint);
        for (String childId : family.childIds) {
            Node child = nodeById.get(childId);
            if (child == null) {
                continue;
            }
            canvas.drawLine(child.centerX, siblingY, child.centerX, child.bounds.top, linePaint);
            canvas.drawCircle(child.centerX, siblingY, 8f, dotPaint);
        }
        canvas.drawCircle(mateX, siblingY, 8f, dotPaint);
    }

    private float[] familyChildMetrics(Family family) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float sum = 0f;
        int count = 0;
        for (String childId : family.childIds) {
            Node child = nodeById.get(childId);
            if (child == null) {
                continue;
            }
            minX = Math.min(minX, child.centerX);
            maxX = Math.max(maxX, child.centerX);
            sum += child.centerX;
            count++;
        }
        float mateX = count > 0 ? sum / count : 0f;
        return new float[]{minX, maxX, mateX};
    }

    private float familySpan(Family family) {
        Node father = family.fatherId == null ? null : nodeById.get(family.fatherId);
        Node mother = family.motherId == null ? null : nodeById.get(family.motherId);
        if (father != null && mother != null) {
            return Math.abs(father.centerX - mother.centerX);
        }
        return 0f;
    }

    private float matingLineY(float base, float parentBottom, int order) {
        float candidate = base - order * 30f;
        if (candidate < parentBottom + 8f) {
            candidate = parentBottom + 8f;
        }
        return candidate;
    }

    private int parentGenerationMax(Family family) {
        int max = 0;
        if (family.fatherId != null) {
            max = Math.max(max, generationMap.getOrDefault(family.fatherId, 0));
        }
        if (family.motherId != null) {
            max = Math.max(max, generationMap.getOrDefault(family.motherId, 0));
        }
        return max;
    }

    private float siblingLineY(Family family) {
        int parentGenMax = parentGenerationMax(family);
        float parentBottom = centerYForGeneration(parentGenMax) + NODE_SIZE / 2f;
        float childTop = centerYForGeneration(family.childGeneration) - NODE_SIZE / 2f;
        float marriageY = family.lineY;
        if (family.fatherId == null || family.motherId == null) {
            marriageY = parentBottom + Math.max(18f, (childTop - parentBottom) / 2f);
        }
        return (marriageY + childTop) / 2f;
    }

    private static float centerYForGeneration(int generation) {
        return TOP_PADDING + generation * ROW_HEIGHT + NODE_SIZE / 2f;
    }

    private void drawNode(Canvas canvas, Node node) {
        node.updateBounds();
        Path path = node.shapePath();
        canvas.save();
        canvas.clipPath(path);
        if (node.bitmap != null) {
            RectF dest = new RectF(node.bounds);
            canvas.drawBitmap(node.bitmap, null, dest, placeholderPaint);
        } else {
            canvas.drawPath(path, placeholderPaint);
            String initial = node.nickname();
            if (initial.isEmpty()) {
                initial = "?";
            }
            if (initial.length() > 1) {
                initial = initial.substring(0, 1);
            }
            textPaint.setTextSize(42f);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float textY = node.bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(initial, node.bounds.centerX(), textY, textPaint);
        }
        canvas.restore();
        canvas.drawPath(path, borderPaint);
        textPaint.setTextSize(34f);
        List<String> nicknameLines = splitNickname(node.nickname());
        float lineHeight = textPaint.getFontSpacing();
        float startY = node.bounds.bottom + NICKNAME_OFFSET;
        for (int i = 0; i < nicknameLines.size(); i++) {
            canvas.drawText(nicknameLines.get(i), node.bounds.centerX(), startY + i * lineHeight, textPaint);
        }
    }

    private List<String> splitNickname(String nickname) {
        List<String> lines = new ArrayList<>();
        if (nickname == null) {
            nickname = "";
        }
        lines.add(nickname.length() > 4 ? nickname.substring(0, 4) : nickname);
        if (nickname.length() > 4) {
            String rest = nickname.substring(4);
            if (rest.length() > 4) {
                lines.add(rest.substring(0, 3) + "…");
            } else {
                lines.add(rest);
            }
        }
        return lines;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                downX = x;
                downY = y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                if (!dragging && (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop)) {
                    dragging = true;
                }
                if (dragging) {
                    float maxX = Math.max(0f, desiredWidth - getWidth());
                    float maxY = Math.max(0f, desiredHeight - getHeight());
                    scrollX = Math.max(0f, Math.min(maxX, scrollX - dx));
                    scrollY = Math.max(0f, Math.min(maxY, scrollY - dy));
                    invalidate();
                }
                lastTouchX = x;
                lastTouchY = y;
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    float hitX = x + scrollX;
                    float hitY = y + scrollY;
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        Node node = nodes.get(i);
                        if (node.bounds.contains(hitX, hitY)) {
                            openProfile(node.details.profile.id);
                            break;
                        }
                    }
                }
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void openProfile(String profileId) {
        Intent intent = new Intent(getContext(), ProfileEditActivity.class);
        intent.putExtra(ProfileEditActivity.EXTRA_PROFILE_ID, profileId);
        getContext().startActivity(intent);
    }

    private static class Node {
        ProfileDetails details;
        int generation;
        float centerX;
        int gridX;
        RectF bounds = new RectF();
        Bitmap bitmap;

        void updateBounds() {
            bounds.set(centerX - NODE_SIZE / 2f, centerYForGeneration(generation) - NODE_SIZE / 2f,
                    centerX + NODE_SIZE / 2f, centerYForGeneration(generation) + NODE_SIZE / 2f);
        }

        String nickname() {
            if (details.customFields != null) {
                for (ProfileCustomFieldEntity field : details.customFields) {
                    if ("nickname".equalsIgnoreCase(field.fieldKey)
                            || "nickname".equalsIgnoreCase(field.fieldName)) {
                        if (field.textValue != null && !field.textValue.trim().isEmpty()) {
                            return field.textValue.trim();
                        }
                    }
                }
            }
            String species = TaxonomyUtil.speciesDisplay(details.profile);
            return species == null || species.isEmpty() ? "?" : species;
        }

        Path shapePath() {
            Path path = new Path();
            String gender = details.profile.gender == null ? "UNKNOWN" : details.profile.gender;
            if ("MALE".equals(gender)) {
                path.addRect(bounds, Path.Direction.CW);
            } else if ("FEMALE".equals(gender)) {
                path.addCircle(bounds.centerX(), bounds.centerY(), NODE_SIZE / 2f, Path.Direction.CW);
            } else {
                path.moveTo(bounds.centerX(), bounds.top);
                path.lineTo(bounds.right, bounds.top + NODE_SIZE * 0.25f);
                path.lineTo(bounds.right, bounds.bottom - NODE_SIZE * 0.25f);
                path.lineTo(bounds.centerX(), bounds.bottom);
                path.lineTo(bounds.left, bounds.bottom - NODE_SIZE * 0.25f);
                path.lineTo(bounds.left, bounds.top + NODE_SIZE * 0.25f);
                path.close();
            }
            return path;
        }
    }

    private static class Family {
        String fatherId;
        String motherId;
        List<String> childIds = new ArrayList<>();
        int childGeneration;
        long minChildCreatedAt;
        int sortIndex;
        float lineY;
    }

    private static class ConstraintEdge {
        final String from;
        final String to;
        final int weight;

        ConstraintEdge(String from, String to, int weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
    }
}
