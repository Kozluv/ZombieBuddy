package me.zed_0xff.zombie_buddy.transformers.bytebuddy;

import me.zed_0xff.zombie_buddy.annotations.Internal;
import me.zed_0xff.zombie_buddy.annotations.Internal.Meta;
import me.zed_0xff.zombie_buddy.annotations.Patch;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;

/*
 * base class for transformers that rewrite patch annotations (e.g. AlternativeResolver, Converter)
 */
public abstract class AbstractPatchAnnotationTransformerV2 extends AbstractTransformer {
    static boolean isZBdesc(String desc) {
        return desc != null && desc.startsWith("Lme/zed_0xff/zombie_buddy/");
    }

    /** @return loaded {@link Internal.Flags} on {@code method}, or {@code null} if absent */
    protected static Internal.Flags getMethodFlags(MethodDescription method) {
        AnnotationDescription.Loadable<Internal.Flags> ld = method
            .getDeclaredAnnotations()
            .ofType(Internal.Flags.class);
            
        return ld == null ? null : ld.load();
    }

    record MetaInfo(Meta meta, AnnotationDescription annDesc) {}

    MetaInfo getMetaInfo(AnnotationSource src) {
        Patch patch = m_ctx.getPatch();
        if (patch == null) return null;

        boolean patchAdvice = patch.isAdvice();
        for (AnnotationDescription zb : src.getDeclaredAnnotations().filter(a -> isZBdesc(a.getAnnotationType().getDescriptor()))) {
            for (AnnotationDescription metaAnn : zb.getAnnotationType().getDeclaredAnnotations().filter(ann -> ann.getAnnotationType().represents(Meta.class))) {
                Internal.Meta meta = metaAnn.prepare(Meta.class).load();
                if (meta.isAdvice() == patchAdvice) {
                    return new MetaInfo(meta, zb);
                }
            }
        }

        return null;
    }
}
