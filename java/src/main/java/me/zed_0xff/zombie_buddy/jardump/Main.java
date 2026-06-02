package me.zed_0xff.zombie_buddy.jardump;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import me.zed_0xff.zombie_buddy.CLIUtil;
import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.transformers.ClassContext;
import me.zed_0xff.zombie_buddy.transformers.JarContext;
import me.zed_0xff.zombie_buddy.transformers.Pipeline;
import me.zed_0xff.zombie_buddy.transformers.Pipeline.TransInfo;

public class Main extends CLIUtil {
    static boolean _showHelp    = false;
    static boolean _changedOnly = false;
    static String _outputFile   = null;
    static HashSet<String> _classFilter = new HashSet<>();

    private static final List<TransInfo> TRANS_LIST = Pipeline.transList();
    private static final Map<String, TransInfo> TRANS_MAP = Pipeline.TRANS_MAP;
    private static final ArrayList<String> _transformers = new ArrayList<>(Pipeline.defaultTransformerIds());

    public static void showHelp() {
        System.out.println("Usage: java -jar JarDump.jar [options] <path_to_jar>");
        System.out.println("Options:");
        System.out.println("    -h, --help         Show this help message");
        System.out.println("    -t, --transformers Specify which transformers to apply (default:all)");
        System.out.println("    -c, --changed-only Dump only classes that were modified by transformers");
        System.out.println("    -C, --class CLASS  Dump only the specified class (can be used multiple times)");
        System.out.println("    -o, --output FILE  Write modified classes to a JAR file");
        System.out.println();
        System.out.println("transformers:");
        var tbl = new CompactTable(2);
        tbl.setAlign(0, CompactTable.Align.RIGHT);
        for (TransInfo t : TRANS_LIST) {
            if ("none".equals(t.id())) continue;
            tbl.addRow(t.id(), t.description());
        }
        System.out.println(indent(tbl.render()));

        System.out.println();
        System.out.println("default transformers: " + String.join(", ", _transformers));
    }

    static ArrayList<String> parseArgs(String[] args) {
        ArrayList<String> positionalArgs = new ArrayList<>();
        for(int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                positionalArgs.add(arg);
            } else {
                     if (arg.equals("-h") || arg.equals("--help"))         _showHelp    = true;
                else if (arg.equals("-c") || arg.equals("--changed-only")) _changedOnly = true;
                else if (arg.equals("-C") || arg.equals("--class"))        _classFilter.add(args[++i]);
                else if (arg.equals("-o") || arg.equals("--output"))       _outputFile = args[++i];
                else if (arg.equals("-t") || arg.equals("--transformers")) {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: Missing value for " + arg);
                        System.exit(1);
                    }
                    _transformers.clear();
                    for (String key : args[++i].split(",")) {
                        if (!TRANS_MAP.containsKey(key)) {
                            System.err.println("Unknown transformer: " + key);
                            System.exit(1);
                        }
                        _transformers.add(key);
                    }
                }
                else {
                    System.err.println("Unknown option: " + arg);
                }
            }
        }
        return positionalArgs;
    }

    public static void processClass(String className, byte[] classBytes, JarContext jctx) throws IOException {
        ClassContext classCtx = new ClassContext(className, jctx);
        byte[] rewritten = Pipeline.of(_transformers).transformClass(className, classBytes, jctx);

        if (classCtx.isChanged() || !_changedOnly) {
            var dumper = new AsmDump(jctx);
            System.out.println(dumper.dumpClass(rewritten));
        }
    }

    public static void processJar(String fname) throws IOException {
        HashMap<String, byte[]> classes = new HashMap<>();

        try (JarFile jar = new JarFile(new File(fname), false)) {
            for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); ) {
                JarEntry je = en.nextElement();
                String n = je.getName();
                if (!n.endsWith(".class") || n.startsWith("META-INF/") || n.equals("module-info.class")) continue;

                String className = n.substring(0, n.length() - 6).replace('/', '.');
                if (!_classFilter.isEmpty() && !_classFilter.contains(className)) continue;

                try (InputStream in = jar.getInputStream(je)) {
                    classes.put(className, in.readAllBytes());
                }
            }
        }

        ArrayList<String> classNames = new ArrayList<>(classes.keySet());
        Collections.sort(classNames);

        try (JarContext jctx = JarContext.forClasses(classes)) {
            for (String className : classNames) {
                try {
                    byte[] classBytes = jctx.getClassBytes(className);
                    if (classBytes != null) {
                        processClass(className, classBytes, jctx);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to read class: " + className);
                    e.printStackTrace();
                }
            }
            if (jctx.hasNew() && _outputFile != null) {
                jctx.writeTo(_outputFile);
                System.out.println("[=] Modified classes written to " + _outputFile);
            }
        }
    }

    public static void processClassFile(String fname) throws IOException {
        // String className = fname.endsWith(".class") ? fname.substring(0, fname.length() - 6).replace('/', '.') : fname;
        // byte[] bytes = Files.readAllBytes(Path.of(fname));
        //
        // ClassFileLocator locator = new ClassFileLocator.Compound(
        //         new ClassFileLocator.Simple(Map.of(className, bytes)),
        //         ClassFileLocator.ForClassLoader.ofSystemLoader()
        // );
        //
        // TypePool pool = TypePool.Default.of(locator);
        // processClass(className, bytes, pool);
    }

    public static void main(String[] args) throws IOException {
        Logger.get(null).setLevel(Logger.DEBUG);

        ArrayList<String> positionalArgs = parseArgs(args);
        if (positionalArgs.size() == 0 || _showHelp) {
            showHelp();
            return;
        }

        var t0 = System.currentTimeMillis();
        for (String fname : positionalArgs) {
            System.out.println(fname);
            if (fname.endsWith(".jar")) processJar(fname);
            else if (fname.endsWith(".class")) processClassFile(fname);
            else {
                Logger.error("Unsupported file type: ", fname);
            }
        }
        System.out.println("[=] Done in " + (System.currentTimeMillis() - t0) + "ms");
    }
}
