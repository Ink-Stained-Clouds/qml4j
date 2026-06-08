package io.qml4j.demo;

import java.util.LinkedHashMap;
import java.util.Map;

// The AppFeatures context property the upstream MD3 app reads (ProPage gates its
// extra cards on it). Upstream's C++ host derives these from build-time macros that
// depend on optional src/Extras/* modules; our host enables every extra whose page
// QML ships in the repo, so the full ProPage card set renders.
final class AppFeaturesMap {
    private AppFeaturesMap() {}

    static Map<String, Object> all() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : new String[]{
                "charts", "dataGrid", "hotReload", "performance", "mathSymbols",
                "markdown", "nodeGraph", "gantt", "reportDesigner", "videoWall"}) {
            m.put(k, Boolean.TRUE);
        }
        return m;
    }
}
