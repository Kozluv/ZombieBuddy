package me.zed_0xff.zombie_buddy.transformers;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import me.zed_0xff.zombie_buddy.Utils;

public class AnnElements extends HashMap<String, Object> {
    public static AnnElements fromValues(List<Object> values) {
        if (Utils.isBlank(values)) return new AnnElements();

        AnnElements map = new AnnElements();
        for (int i = 0; i < values.size(); i += 2) {
            map.put((String)values.get(i), values.get(i + 1));
        }
        return map;
    }

    public List<Object> toValues() {
        if (Utils.isBlank(this)) return List.of();

        return this.entrySet().stream()
            .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
            .toList();
    }

    /** ASM runtime may store annotation booleans as {@link Integer} ({@code 0}/{@code 1}) instead of {@link Boolean}. */
    public Boolean getBoolean(String name) {
        Object val = get(name);
        if (val instanceof Boolean b) return b;
        if (val instanceof Integer j) return j != 0;

        return null;
    }

    public List<String> getListStr(String name) {
        Object val = get(name);
        if (val instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return null;
    }
}
