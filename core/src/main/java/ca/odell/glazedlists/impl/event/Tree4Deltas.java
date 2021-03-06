/* Glazed Lists                                                 (c) 2003-2006 */
/* http://publicobject.com/glazedlists/                      publicobject.com,*/
/*                                                     O'Dell Engineering Ltd.*/
package ca.odell.glazedlists.impl.event;

import ca.odell.glazedlists.event.ListEvent;
import ca.odell.glazedlists.event.ObjectChange;
import ca.odell.glazedlists.impl.adt.barcode2.Element;
import ca.odell.glazedlists.impl.adt.barcode2.FourColorTree;
import ca.odell.glazedlists.impl.adt.barcode2.FourColorTreeIterator;
import ca.odell.glazedlists.impl.adt.barcode2.ListToByteCoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manage and describe the differences between two revisions of the
 * same List, assuming either one can change at any time.
 *
 * <p>Initially, the source and target lists are equal. Over time, the
 * target list changes. It's also possible that the source list can change,
 * which is necessary for long-lived buffered changes.
 *
 * @author <a href="mailto:jesse@swank.ca">Jesse Wilson</a>
 */
public class Tree4Deltas<E> {

    /**
     * all the names of the index sets are with respect to the target
     */
    private static final ListToByteCoder<String> BYTE_CODER = new ListToByteCoder<>(Arrays.asList("+", "U", "X", "_"));
    public static final byte INSERT = BYTE_CODER.colorToByte("+");
    public static final byte UPDATE = BYTE_CODER.colorToByte("U");
    public static final byte DELETE = BYTE_CODER.colorToByte("X");
    public static final byte NO_CHANGE = BYTE_CODER.colorToByte("_");

    private static final byte SOURCE_INDICES = BYTE_CODER.colorsToByte(Arrays.asList("U", "X", "_"));
    private static final byte TARGET_INDICES = BYTE_CODER.colorsToByte(Arrays.asList("U", "+", "_"));
    private static final byte ALL_INDICES = BYTE_CODER.colorsToByte(Arrays.asList("U", "X", "+", "_"));
    private static final byte CHANGE_INDICES = BYTE_CODER.colorsToByte(Arrays.asList("U", "X", "+"));

    /**
     * the trees values include removed elements
     */
    private FourColorTree<ObjectChange<E>> tree = new FourColorTree<>(BYTE_CODER);
    private boolean allowContradictingEvents = false;

    /**
     * When the first change to a list happens, we need to guess what the list's
     * capacity is. After that change, we reliably know the list's capacity, so
     * we don't need to keep testing the capacity one index at a time.
     */
    private boolean initialCapacityKnown = false;

    public boolean horribleHackPreferMostRecentValue = false;
    /** Allow dynamically setting size of the tree according to the incoming events **/
    public boolean dynamicSizing = true;

    public Tree4Deltas(){
    }

    protected Tree4Deltas(Tree4Deltas<E> deltas){
        this.tree = deltas.tree.copy();
        this.allowContradictingEvents = deltas.allowContradictingEvents;
        this.initialCapacityKnown = deltas.initialCapacityKnown;
        this.horribleHackPreferMostRecentValue = deltas.horribleHackPreferMostRecentValue;
        this.dynamicSizing = deltas.dynamicSizing;
    }

    public boolean getAllowContradictingEvents() {
        return allowContradictingEvents;
    }

    public void setAllowContradictingEvents(boolean allowContradictingEvents) {
        this.allowContradictingEvents = allowContradictingEvents;
    }

    public int targetToSource(int targetIndex) {
        if (!initialCapacityKnown) ensureCapacity(targetIndex + 1);
        return tree.convertIndexColor(targetIndex, TARGET_INDICES, SOURCE_INDICES);
    }

    public int sourceToTarget(int sourceIndex) {
        if (!initialCapacityKnown) ensureCapacity(sourceIndex + 1);
        return tree.convertIndexColor(sourceIndex, SOURCE_INDICES, TARGET_INDICES);
    }

    public void addTargetChange(int startIndex, int changeType, List<ObjectChange<E>> events) {
        final int endIndex = startIndex + events.size();
        if (!initialCapacityKnown) ensureCapacity(endIndex);
        int j = 0;
        for (int i = startIndex; i < endIndex; i++) {
            final ObjectChange<E> newChange = events.get(j);
            if (changeType == ListEvent.INSERT) {
                tree.add(startIndex, TARGET_INDICES, INSERT, newChange, 1);
            } else if (changeType == ListEvent.UPDATE) {
                final int overallIndex = tree.convertIndexColor(i, TARGET_INDICES, ALL_INDICES);
                if(tree.size(ALL_INDICES) <= 0){
                    tree.add(overallIndex, ALL_INDICES, UPDATE, newChange, 1);
                }else {
                    Element<ObjectChange<E>> standingChangeToIndex = tree.get(overallIndex, ALL_INDICES);
                    if (horribleHackPreferMostRecentValue) {
                        byte newColor = standingChangeToIndex.getColor() == INSERT ? INSERT : UPDATE;
                        tree.set(overallIndex, ALL_INDICES, newColor, newChange, 1);
                    } else {
                        if (standingChangeToIndex.getColor() == INSERT) {
                            // when updating a insert the original insert stands with the new updated value
                            tree.set(overallIndex, ALL_INDICES, INSERT, ObjectChange.create(ListEvent.unknownValue(), newChange.getNewValue()), 1);
                        } else if (standingChangeToIndex.getColor() == UPDATE) {
                            // if we're updating an update, the original replaced value stands.
                            tree.set(overallIndex, ALL_INDICES, UPDATE, ObjectChange.create(standingChangeToIndex.get().getOldValue(), newChange.getNewValue()), 1);
                        } else {
                            // apply the update to our change description
                            tree.set(overallIndex, ALL_INDICES, UPDATE, newChange, 1);
                        }
                    }
                }
            } else if (changeType == ListEvent.DELETE) {
                if (startIndex > 0 && startIndex > tree.size(TARGET_INDICES)) {
                    throw new IllegalArgumentException();
                }
                final int overallIndex = tree.convertIndexColor(startIndex, TARGET_INDICES, ALL_INDICES);
                Element<ObjectChange<E>> standingChangeToIndex = tree.get(overallIndex, ALL_INDICES);
                // if we're deleting an insert, remove that insert
                if (standingChangeToIndex.getColor() == INSERT) {
                    if (!allowContradictingEvents)
                        throw new IllegalStateException("Remove " + i + " undoes prior insert at the same index! Consider enabling contradicting events.");
                    tree.remove(overallIndex, ALL_INDICES, 1);
                } // if we're deleting an update/delete, the original replaced value stands.
                else if (standingChangeToIndex.getColor() == UPDATE || standingChangeToIndex.getColor() == DELETE) {
                    tree.set(overallIndex, ALL_INDICES, DELETE, ObjectChange.create(standingChangeToIndex.get()
                            .getOldValue(), ListEvent.unknownValue()), 1);
                } else {
                    tree.set(overallIndex, ALL_INDICES, DELETE, newChange, 1);
                }
            }
            j++;
        }

    }

    /**
     * <p>We should consider removing the loop by only setting on removed elements.
     *
     * @param oldValue   the previous value being replaced
     * @param newValue   the new value
     * @param startIndex the first updated element, inclusive
     * @param endIndex   the last index, exclusive
     * @deprecated use {@link #addTargetChange(int, int, List)}
     */
    @Deprecated
    public void targetUpdate(int startIndex, int endIndex, E oldValue, E newValue) {
        this.addTargetChange(startIndex, ListEvent.UPDATE, ObjectChange.create(endIndex - startIndex, oldValue, newValue));
    }

    /**
     * Add a value to the target only.
     *
     * <p>Since this method takes a value parameter, is is only needed
     * when the target doesn't store its value, for example with buffered
     * changes.
     *
     * @param startIndex the first inserted element, inclusive
     * @param endIndex   the last index, exclusive
     * @param newValue   the inserted value
     * @deprecated use {@link #addTargetChange(int, int, List)}
     */
    @Deprecated
    public void targetInsert(int startIndex, int endIndex, E newValue) {
        this.addTargetChange(startIndex, ListEvent.INSERT, ObjectChange.create(endIndex - startIndex, ListEvent.unknownValue(), newValue));
    }

    /**
     * <p>We should consider removing the loop from this method by counting
     * the inserted elements between startIndex and endIndex, removing those,
     * then removing everything else...
     *
     * @param startIndex the index of the first element to remove
     * @param endIndex   the last index, exclusive
     * @param value      the removed value
     * @deprecated use {@link #addTargetChange(int, int, List)}
     */
    @Deprecated
    public void targetDelete(int startIndex, int endIndex, E value) {
        this.addTargetChange(startIndex, ListEvent.DELETE, ObjectChange.create(endIndex - startIndex, value, ListEvent.unknownValue()));
    }

    public void sourceInsert(int sourceIndex) {
        tree.add(sourceIndex, SOURCE_INDICES, NO_CHANGE, ObjectChange.unknownChange(), 1);
    }

    public void sourceDelete(int sourceIndex) {
        tree.remove(sourceIndex, SOURCE_INDICES, 1);
    }

    public void sourceRevert(int sourceIndex) {
        tree.set(sourceIndex, SOURCE_INDICES, NO_CHANGE, ObjectChange.unknownChange(), 1);
    }

    public int targetSize() {
        return tree.size(TARGET_INDICES);
    }

    public int sourceSize() {
        return tree.size(SOURCE_INDICES);
    }

    public byte getChangeType(int sourceIndex) {
        return tree.get(sourceIndex, SOURCE_INDICES).getColor();
    }

    /**
     * Get the value at the specified target index.
     *
     * @return the value, or {@link ListEvent#UNKNOWN_VALUE} if this index
     * holds a value that hasn't been buffered. In this case, the value
     * can be obtained from the source list.
     */
    public E getTargetValue(int targetIndex) {
        final Element<ObjectChange<E>> change = tree.get(targetIndex, TARGET_INDICES);
        if (change == null) return null;
        if (change.getColor() == INSERT) return change.get().getNewValue();
        return change.get().getOldValue();
    }

    public E getSourceValue(int sourceIndex) {
        final Element<ObjectChange<E>> change = tree.get(sourceIndex, SOURCE_INDICES);
        if (change == null) return null;
        if (change.getColor() == INSERT) return change.get().getNewValue();
        return change.get().getOldValue();
    }

    public void reset(int size) {
        tree.clear();
        initialCapacityKnown = dynamicSizing ? false : true;
        ensureCapacity(size);
    }

    public Tree4Deltas<E> copy(){
        return new Tree4Deltas<>(this);
    }

    private void ensureCapacity(int size) {
        int currentSize = tree.size(TARGET_INDICES);
        int delta = size - currentSize;
        if (delta > 0) {
            int endOfTree = tree.size(ALL_INDICES);
            for (int i = 0; i < delta; i++) {
                tree.add(endOfTree, ALL_INDICES, NO_CHANGE, ObjectChange.unknownChange(), 1);
            }
        }
    }

    /**
     * Add all the specified changes to this.
     */
    public void addAll(BlockSequence<E> blocks) {
        for (BlockSequence.Iterator<E> i = blocks.iterator(); i.nextBlock(); ) {
            int blockStart = i.getBlockStart();
            int type = i.getType();
            List<ObjectChange<E>> blockChanges = i.getBlockChanges();
            addTargetChange(blockStart, type, blockChanges);
        }
    }

    /**
     * @return <code>true</code> if this event contains no changes.
     */
    public boolean isEmpty() {
        return tree.size(CHANGE_INDICES) == 0;
    }

    public Iterator<E> iterator() {
        return new Iterator<>(tree);
    }

    @Override
    public String toString() {
        return tree.asSequenceOfColors();
    }

    /**
     * Iterate through the list of changes in this tree.
     */
    public static class Iterator<E> {

        private final FourColorTree<ObjectChange<E>> tree;
        private final FourColorTreeIterator<ObjectChange<E>> treeIterator;

        private Iterator(FourColorTree<ObjectChange<E>> tree) {
            this.tree = tree;
            this.treeIterator = new FourColorTreeIterator<>(tree);
        }

        private Iterator(FourColorTree<ObjectChange<E>> tree, FourColorTreeIterator<ObjectChange<E>> treeIterator) {
            this.tree = tree;
            this.treeIterator = treeIterator;
        }

        public Iterator<E> copy() {
            return new Iterator<>(tree, treeIterator.copy());
        }

        public Iterator<E> deepCopy(Tree4Deltas<E> deltas){
            FourColorTree<ObjectChange<E>> copy = this.tree.copy();
            FourColorTreeIterator<ObjectChange<E>> iterator = this.treeIterator.copyWithTree(copy);
            return new Iterator<>(copy, iterator);
        }

        public Iterator<E> withDeltas(Tree4Deltas<E> deltas){
            FourColorTree<ObjectChange<E>> tree = deltas.tree;
            FourColorTreeIterator<ObjectChange<E>> iterator = this.treeIterator.copyWithTree(tree);
            return new Iterator<>(tree, iterator);
        }

        public int getIndex() {
            return treeIterator.index(TARGET_INDICES);
        }

        public int getStartIndex() {
            return treeIterator.nodeStartIndex(TARGET_INDICES);
        }

        public int getEndIndex() {
            // this is peculiar. We add mixed types - an index of current indices
            // plus the size of "all indices". . . this is because we describe the
            // range of deleted indices from its start to finish, although it's
            // finish will ultimately go to zero once the change is applied.
            return treeIterator.nodeStartIndex(TARGET_INDICES) + treeIterator.nodeSize(ALL_INDICES);
        }

        public int getType() {
            byte color = treeIterator.color();
            if (color == INSERT) return ListEvent.INSERT;
            else if (color == UPDATE) return ListEvent.UPDATE;
            else if (color == DELETE) return ListEvent.DELETE;
            else throw new IllegalStateException();
        }

        public boolean next() {
            if (!hasNext()) return false;
            treeIterator.next(CHANGE_INDICES);
            return true;
        }

        public boolean nextNode() {
            if (!hasNextNode()) return false;
            treeIterator.nextNode(CHANGE_INDICES);
            return true;
        }

        public boolean hasNext() {
            return treeIterator.hasNext(CHANGE_INDICES);
        }

        public boolean hasNextNode() {
            return treeIterator.hasNextNode(CHANGE_INDICES);
        }

        public E getOldValue() {
            return getChange().getOldValue();
        }

        public E getNewValue() {
            return getChange().getNewValue();
        }

        public ObjectChange<E> getChange(){
            final Element<ObjectChange<E>> element = treeIterator.node();
            return element.get();
        }

        public List<ObjectChange<E>> getBlockChanges() {
            final Element<ObjectChange<E>> node = treeIterator.node();
            final int size = treeIterator.nodeSize(ALL_INDICES);
            if (size == 1) return Collections.singletonList(node.get());
            return ObjectChange.create(size, node.get());
        }
    }
}