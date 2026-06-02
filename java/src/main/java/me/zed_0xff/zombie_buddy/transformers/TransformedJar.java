package me.zed_0xff.zombie_buddy.transformers;

import java.util.List;
import java.util.Map;

/** In-memory contents of a patch jar after {@link Pipeline} transformation. */
public record TransformedJar(
        Map<String, byte[]> classes,
        Map<String, byte[]> resources,
        List<String> patches,
        String mainClassName,
        String preMainClassName
) {}
