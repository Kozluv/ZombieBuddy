package me.zed_0xff.zombie_buddy.transformers.bytebuddy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import me.zed_0xff.zombie_buddy.annotations.Internal;
import me.zed_0xff.zombie_buddy.annotations.Patch;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.Type;

/*
 * base class for transformers that rewrite patch annotations (e.g. AlternativeResolver, AnnotationConverter)
 */
public abstract class AbstractPatchAnnotationTransformer extends AbstractParamAwareTransformer {
    protected static final Map<String, AnnInfo> PATCH_ANN_MAP = new HashMap<>();
    static {
        for (Class<?> ann : Patch.class.getDeclaredClasses()) {
            if (!ann.isAnnotation()) continue;

            Internal.Meta[] metas = ann.getAnnotationsByType(Internal.Meta.class);
            if (metas.length == 0) continue;

            Map<String, MapBoolInfo> mapBools          = new HashMap<>();
            Map<String, Internal.Flags> mapFlags = new HashMap<>();

            // annotation elements (parameters)
            for (Method elem : ann.getDeclaredMethods()) {
                Internal.MapBool mb = elem.getAnnotation(Internal.MapBool.class);
                if (mb != null) mapBools.put(elem.getName(), new MapBoolInfo(resolveType(mb.onTrue()), resolveType(mb.onFalse())));

                Internal.Flags flags = elem.getAnnotation(Internal.Flags.class);
                if (flags != null) mapFlags.put(elem.getName(), flags);
            }

            String desc = Type.getDescriptor(ann);
            PATCH_ANN_MAP.put(desc, new AnnInfo(desc, metas, mapBools, mapFlags, false));

            // for (var meta : metas) {
            //     if (meta.targetAnnotation() != null && meta.targetAnnotation() != void.class) {
            //         desc = Type.getDescriptor(meta.targetAnnotation());
            //         PATCH_ANN_MAP.put(desc, new AnnInfo(desc, new Internal.Meta[]{ meta }, mapBools, mapFlags, true));
            //     }
            // }
        }
    }

    protected static Type resolveType(Class<?> cls) {
        return (cls == Internal.DropAnnParam.class) ? null : Type.getType(cls);
    }

    protected record MapBoolInfo(Type onTrue, Type onFalse) {} // null means drop annotation element, i.e. fallback to default
    protected record AnnInfo(
            String                            descriptor,
            Internal.Meta[]             metas,
            Map<String, MapBoolInfo>          mapBools,
            Map<String, Internal.Flags> mapFlags,
            boolean                           isTarget
    ) {}

    // override in subclasses
    protected abstract AnnotationVisitor visitMappedAnnotation(     AnnInfo ai, boolean visible, BiFunction<String, Boolean, AnnotationVisitor> visitor);
    protected abstract AnnotationVisitor visitMappedParamAnnotation(AnnInfo ai, boolean visible, TriFunction<Integer, String, Boolean, AnnotationVisitor> visitor, int pidx, String paramName);

    @Override
    protected final AnnotationVisitor processClassAnnotation(String desc, boolean visible, BiFunction<String, Boolean, AnnotationVisitor> visitor) {
        AnnInfo ai = PATCH_ANN_MAP.get(desc);
        AnnotationVisitor result = (ai == null) ? null : visitMappedAnnotation(ai, visible, visitor);
        return (result == null) ? visitor.apply(desc, visible) : result;
    }

    @Override
    protected final AnnotationVisitor processMethodAnnotation(String desc, boolean visible, BiFunction<String, Boolean, AnnotationVisitor> visitor) {
        AnnInfo ai = PATCH_ANN_MAP.get(desc);
        AnnotationVisitor result = (ai == null) ? null : visitMappedAnnotation(ai, visible, visitor);
        return (result == null) ? visitor.apply(desc, visible) : result;
    }

    @Override
    protected final AnnotationVisitor processParameterAnnotation(int pidx, String desc, boolean visible, TriFunction<Integer, String, Boolean, AnnotationVisitor> visitor, String paramName) {
        AnnInfo ai = PATCH_ANN_MAP.get(desc);
        AnnotationVisitor result = (ai == null) ? null : visitMappedParamAnnotation(ai, visible, visitor, pidx, paramName);
        return (result == null) ? visitor.apply(pidx, desc, visible) : result;
    }
}
