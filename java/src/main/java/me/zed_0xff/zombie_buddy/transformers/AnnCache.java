package me.zed_0xff.zombie_buddy.transformers;

import static org.objectweb.asm.Type.getDescriptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.annotations.Internal.AnnConverter;
import me.zed_0xff.zombie_buddy.annotations.Internal.Meta;
import me.zed_0xff.zombie_buddy.annotations.Patch;
import me.zed_0xff.zombie_buddy.annotations.Shadow;
import net.bytebuddy.description.type.TypeDescription;

public class AnnCache {
    private static final Map<String, AnnInfo> _cache = new HashMap<>();

    public record AnnInfo(
            Class<?> cls, 
            TypeDescription td, 
            Meta[] metas,
            AnnConverter annConverter
    ) {
        public Meta getMeta(boolean isAdvice) {
            for (Meta meta : metas) {
                if (meta.isAdvice() == isAdvice) {
                    return meta;
                }
            }
            return null;
        }
    }

    static {
        parseAnnotations(Patch.class);
        parseAnnotations(Shadow.class);
    }

    private static void parseAnnotations(Class<?> cls) {
        for (Class<?> c : cls.getDeclaredClasses()) {
            if (!c.isAnnotation()) continue;

            var annConverterCls = Arrays.stream(c.getDeclaredClasses())
                .filter(dc -> AnnConverter.class.isAssignableFrom(dc)) // assuming there is only one AnnConverter
                .findFirst()
                .orElse(null);

            AnnConverter annConverter = null;
            if (annConverterCls != null) {
                try {
                    annConverter = (AnnConverter) annConverterCls.getDeclaredConstructor().newInstance();
                } catch (Throwable t) {
                    Logger.error("Failed to instantiate AnnConverter for " + c.getName(), t);
                }
            }

            _cache.put(
                    getDescriptor(c),
                    new AnnInfo(
                        c,
                        TypeDescription.ForLoadedType.of(c),
                        c.getDeclaredAnnotationsByType(Meta.class),
                        annConverter
                    )
            );
        }
    }
    
    public static AnnInfo get(String desc) {
        return _cache.get(desc);
    }

    public static Meta getMeta(String desc, boolean isAdvice) {
        AnnInfo ai = get(desc);
        return (ai == null) ? null : ai.getMeta(isAdvice);
    }
}
