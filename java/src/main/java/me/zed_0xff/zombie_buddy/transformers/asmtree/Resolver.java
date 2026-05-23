package me.zed_0xff.zombie_buddy.transformers.asmtree;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.Utils;
import me.zed_0xff.zombie_buddy.annotations.Internal.Flags;
import me.zed_0xff.zombie_buddy.transformers.AnnCache;
import net.bytebuddy.description.type.TypeDescription;

/*
 * Pre-Converter pass: rewrites ZB annotations in-place using element {@link Flags}. Does not require {@link Internal.Meta}.
 */
public class Resolver extends AbstractTransformer {
    @Override
    protected boolean transformNode(ClassNode cn) {
        // Logger.debug("Resolver.transformNode", cn, m_ctx.getTarget());
        if (m_ctx.getTarget() == null) return false;

        boolean changed = false;
        for (FieldNode fn : cn.fields) {
            changed |= resolveFieldAnns(fn);
        }

        for (MethodNode mn : cn.methods) {
            changed |= resolveMethodAnns(mn);
            changed |= resolveMethodParams(mn);
        }

        return changed;
    }

    private boolean resolveFieldAnns(FieldNode fn) {
        return resolveAnns(fn.visibleAnnotations, fn.name);
    }

    private boolean resolveMethodAnns(MethodNode mn) {
        return resolveAnns(mn.visibleAnnotations, mn.name);
    }

    private boolean resolveMethodParams(MethodNode mn) {
        Type[] argTypes = Type.getArgumentTypes(mn.desc);
        if (argTypes.length == 0) return false;

        boolean changed = false;
        for (int pidx = 0; pidx < argTypes.length; pidx++) {
            String paramName = getArgName(mn, pidx);
            if (Utils.isBlank(paramName)) continue;

            changed |= resolveParamAnns(mn.visibleParameterAnnotations, pidx, paramName);
        }

        return changed;
    }

    private boolean resolveParamAnns(List<AnnotationNode>[] lists, int pidx, String paramName) {
        // Logger.debug("resolveParamAnns", lists, pidx, paramName);
        if (lists == null || pidx >= lists.length) return false;

        List<AnnotationNode> plist = lists[pidx];
        if (Utils.isBlank(plist)) return false;

        return resolveAnns(plist, paramName);
    }

    private boolean resolveAnns(List<AnnotationNode> list, String targetName) {
        if (Utils.isBlank(list)) return false;

        boolean changed = false;
        for (AnnotationNode ann : list) {
            changed |= resolveAnn(targetName, ann);
        }

        return changed;
    }

    private boolean resolveAnn(String targetName, AnnotationNode ann) {
        var ai = AnnCache.get(ann.desc);
        if (ai == null) return false;

        boolean changed = false;

        for (var elem : ai.td().getDeclaredMethods().asDefined()) {
            var elemAnns = elem.getDeclaredAnnotations();

            var flags_ = elemAnns.ofType(Flags.class);
            if (flags_ != null) {
                Flags flags = flags_.load();
                String desc = elem.getReturnType().asErasure().getDescriptor();
                changed |= processFlags(ann, elem.getName(), flags, targetName, desc);
            }
        }
        return changed;
    }

    private boolean processFlags(AnnotationNode ann, String elemName, Flags flags, String targetName, String elemDesc) {
        AnnElements els = AnnElements.fromValues(ann.values);
        boolean changed = false;
        boolean hasList = false;

        while (flags.probeField()) {
            Object raw = els.get(elemName);
            if (!(raw instanceof List<?> values) || Utils.isBlank(values)) break;

            hasList = true;

            if (values.size() == 1) {
                break;
            }

            TypeDescription td = m_ctx.getTarget();
            if (td == null) {
                Logger.once.warn("cannot find patch target class for", m_ctx.className());
                break;
            }
            var fields = td.getDeclaredFields();
            for (var f : values) {
                if (!(f instanceof String fieldName)) continue;

                var r = fields.filter(named(fieldName));
                if (r.size() == 1) {
                    ann.visit(elemName, List.of(fieldName));
                    changed = true;
                    break;
                }
            }
            break;
        }

        if (!hasList && flags.inferFromTargetName() && Utils.isBlank(els.get(elemName))) {
            ann.visit(elemName, inferredValue(elemDesc, targetName));
            changed = true;
        }

        return changed;
    }

    private static Object inferredValue(String elemDesc, String targetName) {
        return "[Ljava/lang/String;".equals(elemDesc) ? List.of(targetName) : targetName;
    }
}

