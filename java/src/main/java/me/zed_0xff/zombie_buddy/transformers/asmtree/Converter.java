package me.zed_0xff.zombie_buddy.transformers.asmtree;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.Reflect;
import me.zed_0xff.zombie_buddy.Utils;
import me.zed_0xff.zombie_buddy.annotations.Internal;
import me.zed_0xff.zombie_buddy.annotations.Internal.AnnConverter;
import me.zed_0xff.zombie_buddy.annotations.Internal.Meta;
import me.zed_0xff.zombie_buddy.annotations.Patch;
import me.zed_0xff.zombie_buddy.transformers.AnnCache;
import me.zed_0xff.zombie_buddy.transformers.AnnCache.AnnInfo;

/**
 * For each {@code @Patch.*} annotation that declares {@link Internal.Meta}, appends the matching target annotation
 * (same visibility list; element values shallow-copied)
 * ZombieBuddy annotations are left in place.
 */
public class Converter extends AbstractTransformer {
    private boolean m_isAdvice;

    @Override
    protected boolean transformNode(ClassNode cn) {
        Patch patch = m_ctx.getPatch();
        if (patch == null) return false;

        m_isAdvice = patch.isAdvice();

        boolean changed = convertAnns(cn, cn.visibleAnnotations);

        for (FieldNode fn : cn.fields) {
            changed |= convertAnns(fn, fn.visibleAnnotations); // |= does not short-circuit
        }

        for (MethodNode mn : cn.methods) {
            changed |= convertAnns(mn, mn.visibleAnnotations);
            changed |= convertParamAnns(mn);
        }

        return changed;
    }

    private boolean convertParamAnns(MethodNode mn) {
        if (Utils.isBlank(mn.visibleParameterAnnotations))
            return false;
        if (Utils.isBlank(mn.parameters))
            return convertParamAnnsNoParameterNodes(mn);

        boolean changed = false;
        for (int i = 0; i < mn.parameters.size(); i++) {
            List<AnnotationNode> anns = mn.visibleParameterAnnotations[i];
            if (Utils.isBlank(anns)) continue;

            changed |= convertAnns(mn.parameters.get(i), anns);
        }
        return changed;
    }

    private boolean convertParamAnnsNoParameterNodes(MethodNode mn) {
        // Type[] argTypes = Type.getArgumentTypes(mn.desc);
        boolean changed = false;
        for (int i = 0; i < mn.visibleParameterAnnotations.length; i++) {
            List<AnnotationNode> anns = mn.visibleParameterAnnotations[i];
            if (Utils.isBlank(anns)) continue;

            ParameterNode pn = new ParameterNode(getArgName(mn, i), 0); // XXX access flags is 0 for now
            changed |= convertAnns(pn, anns);
        }
        return changed;
    }

    private boolean convertAnns(Object node, List<AnnotationNode> list) {
        if (Utils.isBlank(list)) return false;

        List<AnnotationNode> snapshot = List.copyOf(list);
        List<AnnotationNode> toAdd = new ArrayList<>();
        for (AnnotationNode ann : snapshot) {
            AnnInfo ai = AnnCache.get(ann.desc);
            if (ai == null) continue;

            var translated = translated(node, ann, ai);
            if (translated == null) continue;

            toAdd.add(translated);
        }
        list.addAll(toAdd);
        return !toAdd.isEmpty();
    }

    // {"name"} => "name"
    private Object transformValue(Object value) {
        if (value == null) return null;

        if (value.getClass().isArray() && Array.getLength(value) == 1) {
            return Array.get(value, 0);
        }

        if (value instanceof List<?> list && list.size() == 1) {
            return list.get(0);
        }
        return value;
    }

    private AnnotationNode translated(Object node, AnnotationNode src, AnnInfo ai) {
        AnnConverter conv = ai.annConverter();
        if (conv != null) {
            try {
                return conv.convert(src, node);
            } catch (Throwable t) {
                Logger.error("Failed to convert annotation " + src.desc + " on node " + node, t);
                return null;
            }
        }

        Meta meta = ai.getMeta(m_isAdvice);
        if (meta == null) return null;

        Set<String> dstElements = Set.copyOf(
                Reflect.on(meta.targetAnnotation())
                .methods(Reflect.PUBLIC, Reflect.DECLARED)
                .stream()
                .map(m -> m.getName())
                .toList()
        );

        AnnElements srcEls = AnnElements.fromValues(src.values);
        AnnElements dstEls = new AnnElements();
        for (var e : srcEls.entrySet()) {
            if (dstElements.contains(e.getKey())) {
                dstEls.put(e.getKey(), transformValue(e.getValue()));
            }
        }

        AnnotationNode dst = new AnnotationNode(Type.getDescriptor(meta.targetAnnotation()));
        dst.values = dstEls.toValues();
        return dst;
    }
}
