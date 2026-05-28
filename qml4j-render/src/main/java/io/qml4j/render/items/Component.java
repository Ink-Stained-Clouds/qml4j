package io.qml4j.render.items;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.DelegateHost;

public class Component extends Item implements DelegateHost {
    private DelegateFactory factory;

    @Override
    public void setDelegate(DelegateFactory factory) {
        this.factory = factory;
    }

    public DelegateFactory factory() {
        return factory;
    }
}
