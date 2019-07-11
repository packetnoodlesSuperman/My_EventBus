package com.bob.okio.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListMap<K, V> extends AbstractMultimap<K, V, List<V>> {

    private final boolean threadSafeLists;

    public ListMap() {
        this(new HashMap<K, List<V>>(), false);
    }

    public ListMap(Map<K, List<V>> map, boolean threadSafeLists) {
        super(map);
        this.threadSafeLists = threadSafeLists;
    }

    protected List<V> createNewCollection() {
        return threadSafeLists ? new CopyOnWriteArrayList<V>() : new ArrayList<V>();
    }

}
