package com.pzmoodle.immersion.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理员编辑的职业/特质覆盖数据；服务端保存为 config/pz_moodle_immersion_definitions.json。 */
public final class DefOverrides {
    /**
     * 一条携带物品配置。id/count 保留给旧格式与兜底显示；
     * raw 是官方 ItemStack.CODEC 序列化出的完整条目（含全部 DataComponents），
     * 任何带组件的模组物品（TaCZ 的 GunId/MeleeId 等）都原样往返，无特判。
     */
    public record ItemGrant(String id, int count, JsonObject raw) {
        /** 旧的纯 id+count 构造入口，保持既有调用点不变。 */
        public ItemGrant(String id, int count) { this(id, count, null); }

        /** 由完整 ItemStack 生成条目：优先用官方编解码器携带全部组件。 */
        public static ItemGrant of(ItemStack stack, HolderLookup.Provider registries) {
            String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            JsonObject full = null;
            if (registries != null) {
                try {
                    DataResult<JsonElement> encoded =
                            ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), stack);
                    JsonElement element = encoded.result().orElse(null);
                    if (element != null && element.isJsonObject()) full = element.getAsJsonObject().deepCopy();
                } catch (Exception ignored) {
                    // 编码失败退回 id+count，物品仍然可发放，只是不带组件。
                }
            }
            return new ItemGrant(key, Math.max(1, stack.getCount()), full);
        }

        /** 还原完整 ItemStack：先按官方格式解码全部组件，失败再退回旧的 id+count。 */
        public ItemStack resolve(HolderLookup.Provider registries) {
            if (raw != null && registries != null) {
                try {
                    ItemStack decoded = ItemStack.CODEC
                            .parse(RegistryOps.create(JsonOps.INSTANCE, registries), raw)
                            .result().orElse(ItemStack.EMPTY);
                    if (!decoded.isEmpty()) return decoded;
                } catch (Exception ignored) {
                    // 组件引用的物品/注册表项缺失时退回基础堆叠。
                }
            }
            ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
            if (key == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item, Math.max(1, Math.min(64, count)));
        }
    }

    public static final class OccOverride {
        public int traitPoints;
        public int[] stats = new int[6];
        public String description = "";
        public String mechanics = "";
        public List<ItemGrant> items = new ArrayList<>();
    }

    public static final class TraitOverride {
        public int cost;
        public int[] stats = new int[6];
        public String description = "";
        public String mechanics = "";
        public List<ItemGrant> items = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "pz_moodle_immersion_definitions.json");
    private static final Map<String, OccOverride> OCCUPATIONS = new LinkedHashMap<>();
    private static final Map<String, TraitOverride> TRAITS = new LinkedHashMap<>();
    /**
     * 机制参数解析缓存：mechanic() 原本每次调用都要拷贝覆盖对象 + split 字符串，
     * 但它处于「每玩家每结算 tick」的热路径上，重复解析代价高。这里按 kind:id 缓存解析好的
     * 键值表，只在配置真正变化（fromJson / apply / put*）时清空。
     */
    private static final Map<String, Map<String, Double>> MECHANIC_CACHE = new LinkedHashMap<>();

    private static void invalidateMechanicCache() {
        MECHANIC_CACHE.clear();
    }

    public static void load() {
        if (!Files.isRegularFile(FILE)) return;
        try {
            fromJson(Files.readString(FILE, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 损坏的管理员配置不应阻止游戏启动，保留 Java 默认值。
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, toJson(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 服务端无写权限时保留内存中的即时修改，并由日志外层处理。
        }
    }

    public static String toJson() {
        JsonObject root = new JsonObject();
        JsonObject occupations = new JsonObject();
        OCCUPATIONS.forEach((id, value) -> occupations.add(id, occupationJson(value)));
        JsonObject traits = new JsonObject();
        TRAITS.forEach((id, value) -> traits.add(id, traitJson(value)));
        root.add("occupations", occupations);
        root.add("traits", traits);
        return GSON.toJson(root);
    }

    public static void fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        OCCUPATIONS.clear();
        TRAITS.clear();
        JsonObject occupations = object(root, "occupations");
        for (String id : occupations.keySet()) {
            if (!Occupation.ALL.containsKey(id)) continue;
            OCCUPATIONS.put(id, readOccupation(occupations.getAsJsonObject(id)));
        }
        JsonObject traits = object(root, "traits");
        for (String id : traits.keySet()) {
            if (!OccupationTrait.ALL.containsKey(id)) continue;
            TRAITS.put(id, readTrait(traits.getAsJsonObject(id)));
        }
        apply();
    }

    public static void apply() {
        invalidateMechanicCache();
        for (Map.Entry<String, OccOverride> entry : OCCUPATIONS.entrySet()) {
            Occupation.Def old = Occupation.ALL.get(entry.getKey());
            if (old == null) continue;
            OccOverride value = entry.getValue();
            int[] s = six(value.stats, new int[]{old.stats().strength(), old.stats().fitness(), old.stats().sprinting(),
                    old.stats().lightfooted(), old.stats().nimble(), old.stats().aiming()});
            Occupation.ALL.put(old.id(), new Occupation.Def(old.id(), value.traitPoints,
                    old.nameKey(), old.bonusKey(), new Occupation.Stats(s[0], s[1], s[2], s[3], s[4], s[5]), old.majors()));
        }
        for (Map.Entry<String, TraitOverride> entry : TRAITS.entrySet()) {
            OccupationTrait.Def old = OccupationTrait.ALL.get(entry.getKey());
            if (old == null) continue;
            TraitOverride value = entry.getValue();
            OccupationTrait.ALL.put(old.id(), new OccupationTrait.Def(old.id(), value.cost,
                    old.positive(), old.nameKey(), old.icon()));
            OccupationTrait.setStatBonus(old.id(), value.stats);
        }
    }

    public static OccOverride occupation(String id) {
        OccOverride value = OCCUPATIONS.get(id);
        if (value != null) return copy(value);
        Occupation.Def def = Occupation.ALL.get(id);
        OccOverride out = new OccOverride();
        if (def != null) {
            out.traitPoints = def.traitPoints();
            out.stats = new int[]{def.stats().strength(), def.stats().fitness(), def.stats().sprinting(),
                    def.stats().lightfooted(), def.stats().nimble(), def.stats().aiming()};
        }
        return out;
    }

    public static TraitOverride trait(String id) {
        TraitOverride value = TRAITS.get(id);
        if (value != null) return copy(value);
        OccupationTrait.Def def = OccupationTrait.ALL.get(id);
        TraitOverride out = new TraitOverride();
        if (def != null) {
            out.cost = def.cost();
            out.stats = OccupationTrait.statBonus(id);
        }
        return out;
    }

    public static void putOccupation(String id, OccOverride value) { OCCUPATIONS.put(id, copy(value)); apply(); }
    public static void putTrait(String id, TraitOverride value) { TRAITS.put(id, copy(value)); apply(); }

    public static List<ItemGrant> itemsForOccupation(String id) {
        return List.copyOf(OCCUPATIONS.containsKey(id) ? OCCUPATIONS.get(id).items : List.of());
    }

    public static List<ItemGrant> itemsForTrait(String id) {
        return List.copyOf(TRAITS.containsKey(id) ? TRAITS.get(id).items : List.of());
    }

    public static double mechanic(String kind, String id, String key, double fallback) {
        String cacheKey = kind + ':' + id;
        // 命中缓存则直接查表，避免每次拷贝覆盖对象并 split 字符串。
        Map<String, Double> table = MECHANIC_CACHE.get(cacheKey);
        if (table == null) {
            String text = "occupation".equals(kind) ? occupation(id).mechanics : trait(id).mechanics;
            table = parseMechanics(text);
            MECHANIC_CACHE.put(cacheKey, table);
        }
        Double v = table.get(key);
        return v == null ? fallback : v;
    }

    /** 把「a=1; b=2」解析成表；空串返回空表（只解析一次，之后走缓存）。 */
    private static Map<String, Double> parseMechanics(String text) {
        if (text == null || text.isBlank()) return Map.of();
        Map<String, Double> out = new LinkedHashMap<>();
        for (String part : text.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2) {
                try {
                    out.put(pair[0].trim(), Double.parseDouble(pair[1].trim()));
                } catch (NumberFormatException ignored) {
                    // 非法数值直接忽略，取用时回退默认值。
                }
            }
        }
        return out;
    }

    public static String mechanicsText(String kind, String id) {
        String value = "occupation".equals(kind) ? occupation(id).mechanics : trait(id).mechanics;
        return value == null || value.isBlank() ? com.pzmoodle.immersion.admin.Mechanics.defaultParameters(kind, id) : value;
    }

    public static MutableComponent occupationDescription(Occupation.Def def) {
        OccOverride value = OCCUPATIONS.get(def.id());
        return value == null || value.description.isBlank() ? Component.translatable(def.bonusKey()) : Component.literal(value.description);
    }

    public static MutableComponent traitDescription(OccupationTrait.Def def) {
        TraitOverride value = TRAITS.get(def.id());
        return value == null || value.description.isBlank() ? Component.translatable(def.descKey()) : Component.literal(value.description);
    }

    public static String editJson(String kind, String id, int points, int[] stats, String description,
                                  String mechanics, List<ItemGrant> items) {
        JsonObject root = new JsonObject();
        root.addProperty("kind", kind);
        root.addProperty("id", id);
        root.addProperty("points", points);
        JsonArray values = new JsonArray();
        for (int value : six(stats, new int[6])) values.add(value);
        root.add("stats", values);
        root.addProperty("description", description == null ? "" : description);
        root.addProperty("mechanics", mechanics == null ? "" : mechanics);
        root.add("items", itemsJson(items));
        return GSON.toJson(root);
    }

    public static void applyEditJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String kind = root.get("kind").getAsString();
        String id = root.get("id").getAsString();
        int points = root.get("points").getAsInt();
        int[] stats = new int[6];
        JsonArray values = root.getAsJsonArray("stats");
        for (int i = 0; i < Math.min(6, values.size()); i++) stats[i] = values.get(i).getAsInt();
        List<ItemGrant> items = readItems(root.getAsJsonArray("items"));
        String description = root.has("description") ? root.get("description").getAsString() : "";
        String mechanics = root.has("mechanics") ? root.get("mechanics").getAsString() : "";
        if ("occupation".equals(kind)) {
            OccOverride value = new OccOverride(); value.traitPoints = points; value.stats = stats; value.description = description; value.mechanics = mechanics; value.items = items;
            putOccupation(id, value);
        } else if ("trait".equals(kind)) {
            TraitOverride value = new TraitOverride(); value.cost = points; value.stats = stats; value.description = description; value.mechanics = mechanics; value.items = items;
            putTrait(id, value);
        }
    }

    private static JsonObject occupationJson(OccOverride value) {
        JsonObject out = new JsonObject(); out.addProperty("points", value.traitPoints); out.add("stats", array(value.stats));
        out.addProperty("description", value.description); out.addProperty("mechanics", value.mechanics);
        out.add("items", itemsJson(value.items)); return out;
    }

    private static JsonObject traitJson(TraitOverride value) {
        JsonObject out = new JsonObject(); out.addProperty("points", value.cost); out.add("stats", array(value.stats));
        out.addProperty("description", value.description); out.addProperty("mechanics", value.mechanics);
        out.add("items", itemsJson(value.items)); return out;
    }

    private static JsonArray array(int[] values) { JsonArray out = new JsonArray(); for (int v : six(values, new int[6])) out.add(v); return out; }
    private static JsonArray itemsJson(List<ItemGrant> values) {
        JsonArray out = new JsonArray();
        for (ItemGrant item : values) {
            if (item.raw() != null) { out.add(item.raw().deepCopy()); continue; }
            JsonObject o = new JsonObject();
            o.addProperty("id", item.id());
            o.addProperty("count", item.count());
            out.add(o);
        }
        return out;
    }
    private static JsonObject object(JsonObject root, String key) { return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject(); }
    private static OccOverride readOccupation(JsonObject o) { OccOverride v = new OccOverride(); v.traitPoints = integer(o, "points", 0); v.stats = ints(o.getAsJsonArray("stats")); v.description = text(o, "description"); v.mechanics = text(o, "mechanics"); v.items = readItems(o.getAsJsonArray("items")); return v; }
    private static TraitOverride readTrait(JsonObject o) { TraitOverride v = new TraitOverride(); v.cost = integer(o, "points", 0); v.stats = ints(o.getAsJsonArray("stats")); v.description = text(o, "description"); v.mechanics = text(o, "mechanics"); v.items = readItems(o.getAsJsonArray("items")); return v; }
    private static int integer(JsonObject o, String key, int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
    private static String text(JsonObject o, String key) { return o.has(key) ? o.get(key).getAsString() : ""; }
    private static int[] ints(JsonArray a) { int[] out = new int[6]; if (a != null) for (int i = 0; i < Math.min(6, a.size()); i++) out[i] = a.get(i).getAsInt(); return out; }
    private static List<ItemGrant> readItems(JsonArray a) {
        List<ItemGrant> out = new ArrayList<>();
        if (a == null) return out;
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            String id = idOf(o);
            int count = Math.max(1, Math.min(64, integer(o, "count", 1)));
            JsonElement components = o.get("components");
            boolean full = components != null && components.isJsonObject() && components.getAsJsonObject().size() > 0;
            out.add(full ? new ItemGrant(id, count, o.deepCopy()) : new ItemGrant(id, count));
        }
        return out;
    }
    /** 兼容字符串与 {namespace,path} 两种 id 写法，取不到就返回空串（完整条目仍可解码）。 */
    private static String idOf(JsonObject o) {
        JsonElement id = o.get("id");
        if (id == null) return "";
        if (id.isJsonPrimitive()) return id.getAsString();
        if (id.isJsonObject()) {
            JsonObject parts = id.getAsJsonObject();
            JsonElement ns = parts.get("namespace"), path = parts.get("path");
            if (ns != null && path != null) return ns.getAsString() + ":" + path.getAsString();
        }
        return "";
    }
    private static int[] six(int[] values, int[] fallback) { int[] out = fallback.clone(); if (values != null) System.arraycopy(values, 0, out, 0, Math.min(6, values.length)); return out; }
    private static OccOverride copy(OccOverride in) { OccOverride out = new OccOverride(); out.traitPoints = in.traitPoints; out.stats = six(in.stats, new int[6]); out.description = in.description; out.mechanics = in.mechanics; out.items = new ArrayList<>(in.items); return out; }
    private static TraitOverride copy(TraitOverride in) { TraitOverride out = new TraitOverride(); out.cost = in.cost; out.stats = six(in.stats, new int[6]); out.description = in.description; out.mechanics = in.mechanics; out.items = new ArrayList<>(in.items); return out; }
    private DefOverrides() {}
}
