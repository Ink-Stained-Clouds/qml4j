package io.qml4j.render;

import io.qml4j.compiler.CompiledUnit;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.compiler.bytecode.CompileScope;
import io.qml4j.compiler.bytecode.QmlCompiler;
import io.qml4j.engine.QObject;
import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.classloader.ClassLoaderBackend;
import io.qml4j.parser.Qml4j;
import io.qml4j.parser.ast.Ast;
import io.qml4j.render.items.core.Item;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Compiles a QML document to bytecode and instantiates its root object,
// resolving compound (file-based) types and singletons across imported modules.
final class Loader {

    private final QmlEngine engine;
    private final TypeRegistry types;
    private final QmlCompiler compiler = new QmlCompiler();
    // Keyed by resolved path (prefix/file), not bare type name: two modules may
    // each export a `Theme`, and they must not collide in the cache.
    private final Map<String, Class<? extends QObject>> importedTypes = new HashMap<>();
    // prefix -> (type name -> singleton class). Singletons are scoped to the
    // import that brought them in, so a doc only sees its own modules' Themes.
    private final Map<String, Map<String, Class<? extends QObject>>> singletonsByPrefix = new HashMap<>();
    private final Map<String, Map<String, QmldirEntry>> qmldirCache = new HashMap<>();
    private final Set<String> compilingNow = new HashSet<>();
    private ResourceLoader resources;

    Loader(QmlEngine engine, TypeRegistry types) {
        this.engine = engine;
        this.types = types;
    }

    void setResources(ResourceLoader resources) {
        this.resources = resources;
    }

    Item instantiate(String qml) {
        return instantiate(qml, "");
    }

    // baseDir is the importing document's directory (relative to the resource root);
    // relative file imports (import "../widgets") resolve against it.
    Item instantiate(String qml, String baseDir) {
        Ast.QmlDocument doc = Qml4j.parse(qml);
        Class<? extends QObject> rootClass = compileAndDefine(doc, baseDir);
        Object inst;
        try {
            inst = rootClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        if (!(inst instanceof Item)) {
            throw new IllegalArgumentException(
                "root QML type must extend Item, got " + inst.getClass().getName());
        }
        return (Item) inst;
    }

    private Class<? extends QObject> compileAndDefine(Ast.QmlDocument doc, String baseDir) {
        List<String> prefixes = stringImportPrefixes(doc, baseDir);
        Set<String> moduleProvided = new HashSet<>();
        for (String p : prefixes) moduleProvided.addAll(loadQmldir(p).keySet());
        TypeRegistry docTypes = types.copy()
            .withResolver(name -> resolveCompound(name, prefixes))
            .withAliases(importAliases(doc))
            .withModuleProvided(moduleProvided);
        registerKnownSingletons(docTypes, prefixes);
        CompiledUnit unit = compiler.compile(doc, docTypes);
        ClassLoaderBackend backend = engine.backend();
        Map<String, Class<?>> defined = backend.defineClasses(unit.classes());
        Class<?> rootClass = defined.get(unit.rootClassName());
        if (rootClass == null) {
            throw new IllegalStateException("compiled unit missing root class");
        }
        if (!QObject.class.isAssignableFrom(rootClass)) {
            throw new IllegalStateException(
                "compiled root class is not a QObject: " + rootClass.getName());
        }
        @SuppressWarnings("unchecked")
        Class<? extends QObject> qc = (Class<? extends QObject>) rootClass;
        return qc;
    }

    private void registerKnownSingletons(TypeRegistry docTypes, List<String> prefixes) {
        for (String p : prefixes) {
            Map<String, Class<? extends QObject>> m = singletonsByPrefix.get(p);
            if (m == null) continue;
            for (Map.Entry<String, Class<? extends QObject>> e : m.entrySet()) {
                docTypes.registerSingleton(e.getKey(), e.getValue());
            }
        }
    }

    private Class<? extends QObject> resolveCompound(String name, List<String> prefixes) {
        if (resources == null) return null;
        for (String p : prefixes) {
            QmldirEntry entry = loadQmldir(p).get(name);
            String relFile = entry != null ? entry.file : name + ".qml";
            String path = p.isEmpty() ? relFile : p + "/" + relFile;
            Class<? extends QObject> cached = importedTypes.get(path);
            if (cached != null) {
                // Re-register a cached singleton into the doc currently compiling:
                // the first resolver registered it, but later docs hit the cache
                // and would otherwise not know it's a singleton.
                registerCachedSingleton(p, name, cached);
                return cached;
            }
            byte[] bytes = resources.load(path);
            if (bytes == null) continue;
            if (!compilingNow.add(path)) {
                throw new IllegalStateException("cyclic import: " + path);
            }
            try {
                Ast.QmlDocument subDoc = Qml4j.parse(new String(bytes, StandardCharsets.UTF_8));
                boolean singleton = (entry != null && entry.singleton) || subDoc.hasPragma("Singleton");
                if (singleton) subDoc.addPragma("Singleton");
                // The imported file's own directory is this prefix; its relative imports
                // resolve against it.
                Class<? extends QObject> rootClass = compileAndDefine(subDoc, p);
                importedTypes.put(path, rootClass);
                if (singleton) {
                    singletonsByPrefix.computeIfAbsent(p, k -> new HashMap<>()).put(name, rootClass);
                    TypeRegistry current = CompileScope.currentRegistry();
                    if (current != null) current.registerSingleton(name, rootClass);
                }
                return rootClass;
            } finally {
                compilingNow.remove(path);
            }
        }
        return null;
    }

    private void registerCachedSingleton(String prefix, String name, Class<? extends QObject> cached) {
        Map<String, Class<? extends QObject>> m = singletonsByPrefix.get(prefix);
        if (m == null || m.get(name) != cached) return;
        TypeRegistry current = CompileScope.currentRegistry();
        if (current != null) current.registerSingleton(name, cached);
    }

    private Map<String, QmldirEntry> loadQmldir(String prefix) {
        Map<String, QmldirEntry> cached = qmldirCache.get(prefix);
        if (cached != null) return cached;
        Map<String, QmldirEntry> map = Collections.emptyMap();
        if (resources != null) {
            String path = prefix.isEmpty() ? "qmldir" : prefix + "/qmldir";
            byte[] bytes = resources.load(path);
            if (bytes != null) {
                map = parseQmldir(new String(bytes, StandardCharsets.UTF_8));
            }
        }
        qmldirCache.put(prefix, map);
        return map;
    }

    private static Map<String, QmldirEntry> parseQmldir(String text) {
        Map<String, QmldirEntry> out = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            int i = 0;
            boolean singleton = false;
            if ("singleton".equals(parts[i])) { singleton = true; i++; }
            if (parts.length - i < 2) continue;
            String typeName = parts[i++];
            if (i < parts.length && parts[i].matches("\\d+(\\.\\d+)?")) i++;
            if (i >= parts.length) continue;
            out.put(typeName, new QmldirEntry(parts[i], singleton));
        }
        return out;
    }

    private static final class QmldirEntry {
        final String file;
        final boolean singleton;
        QmldirEntry(String file, boolean singleton) {
            this.file = file;
            this.singleton = singleton;
        }
    }

    private static Set<String> importAliases(Ast.QmlDocument doc) {
        Set<String> out = new LinkedHashSet<>();
        for (Ast.ImportNode imp : doc.imports) {
            if (imp.alias != null) out.add(imp.alias);
        }
        return out;
    }

    private static List<String> stringImportPrefixes(Ast.QmlDocument doc, String baseDir) {
        List<String> out = new ArrayList<>();
        for (Ast.ImportNode imp : doc.imports) {
            String p = imp.moduleOrPath;
            if (p == null) continue;
            if (imp.isStringPath) {
                // A string import is a file path relative to the importing document
                // (import "../widgets"); resolve it against baseDir so `../` escapes the
                // importing file's directory rather than the resource root.
                String rel = ".".equals(p) ? "" : p;
                out.add(joinPath(baseDir, rel));
            } else {
                // Module URI (import md3.Core) maps to a resource dir md3/Core.
                // Built-in modules (QtQuick) just won't resolve any file.
                out.add(p.replace('.', '/'));
            }
        }
        return out;
    }

    // Normalise `base/rel`, collapsing `.`/`..` segments. A leading `..` is kept (the
    // resource loader resolves it against its own root), matching Qt's file imports.
    static String joinPath(String base, String rel) {
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String seg : (base + "/" + rel).split("/")) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg) && !stack.isEmpty() && !"..".equals(stack.peekLast())) {
                stack.removeLast();
            } else {
                stack.addLast(seg);
            }
        }
        return String.join("/", stack);
    }
}
