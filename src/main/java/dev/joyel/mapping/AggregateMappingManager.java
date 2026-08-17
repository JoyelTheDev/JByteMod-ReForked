package dev.joyel.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks all mapping layers applied in the current session and exposes a
 * merged view of every rename that has been applied so far.
 */
public final class AggregateMappingManager {

    private final List<MappingSet> layers = new ArrayList<MappingSet>();
    private MappingSet merged = new MappingSet();
    private final List<Listener> listeners = new ArrayList<Listener>();

    public interface Listener {
        void onMappingsChanged(MappingSet merged);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void push(MappingSet layer) {
        layers.add(layer);
        rebuild();
    }

    public void pop() {
        if (!layers.isEmpty()) {
            layers.remove(layers.size() - 1);
            rebuild();
        }
    }

    public void clear() {
        layers.clear();
        merged = new MappingSet();
        notifyListeners();
    }

    public MappingSet getMerged() {
        return merged;
    }

    public List<MappingSet> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public int getLayerCount() {
        return layers.size();
    }

    private void rebuild() {
        merged = new MappingSet();
        for (MappingSet layer : layers) {
            merged.merge(layer);
        }
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener l : listeners) {
            l.onMappingsChanged(merged);
        }
    }
}
