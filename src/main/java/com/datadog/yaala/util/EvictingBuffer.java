package com.datadog.yaala.util;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A naive, non-thread safe collection of elements which are bound by <i>capacity</i>. Once the size
 * reaches the latter, the oldest element is <i>evicted</i>.
 *
 * @author Nicolas Estrada.
 */
public class EvictingBuffer<E> implements Iterable<E> {

    public static final int DEFAULT_CAPACITY = 10;
    private final LinkedList<E> list;
    private int capacity;

    public EvictingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public EvictingBuffer(int capacity) {
        this.list = new LinkedList<>();
        this.capacity = capacity;
    }

    @Nonnull
    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        list.forEach(action);
    }

    @Override
    public Spliterator<E> spliterator() {
        return list.spliterator();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void add(E elmt) {
        list.addLast(elmt);
        if (list.size() > capacity) {
            list.removeFirst();
        }
    }
}
