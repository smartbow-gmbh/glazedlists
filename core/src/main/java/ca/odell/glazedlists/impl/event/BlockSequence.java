/* Glazed Lists                                                 (c) 2003-2006 */
/* http://publicobject.com/glazedlists/                      publicobject.com,*/
/*                                                     O'Dell Engineering Ltd.*/
package ca.odell.glazedlists.impl.event;

import ca.odell.glazedlists.event.ListEvent;
import ca.odell.glazedlists.event.ObjectChange;
import ca.odell.glazedlists.impl.adt.IntArrayList;

import java.util.*;

/**
 * Manage a very simple list of list event blocks that occur in
 * increasing-only order.
 *
 * @author <a href="mailto:jesse@swank.ca">Jesse Wilson</a>
 */
public class BlockSequence<E> {
  private static final Class<?> UNMODIFIABLE_COLLECTION;

  static {
    Class<?> clazz = null;
    try {
      clazz = Class.forName("java.util.Collections$UnmodifiableCollection");
    } catch (ClassNotFoundException e) {
    }
    UNMODIFIABLE_COLLECTION = clazz;
  }

  /**
   * the start indices of the change blocks, inclusive
   */
  private final IntArrayList starts;
  /**
   * the change types
   */
  private final IntArrayList types;
  /**
   * the impacted values
   */
  private final List<List<ObjectChange<E>>> values;
  /**
   * internal handlers for overwriting existing blocks out of order
   */
  private final List<OverwriteBlockHandler> blockHandlers;

  private boolean allowContradictingEvents = false;

  public BlockSequence() {
    this.starts = new IntArrayList();
    this.types = new IntArrayList();
    this.values = new ArrayList<>();
    this.blockHandlers = Arrays.asList(new OverwriteUpdateBlockHandler(), new OverwriteInsertDeleteBlockHandler(), new OverwriteInsertUpdateBlockHandler(), new OverwriteDeleteInsertBlockHandler());
  }

  private BlockSequence(BlockSequence sequence) {
    this.starts = new IntArrayList(sequence.starts);
    this.types = new IntArrayList(sequence.types);
    this.values = new ArrayList<>(sequence.values);
    this.blockHandlers = new ArrayList<>(sequence.blockHandlers);
  }

  public boolean getAllowContradictingEvents() {
    return allowContradictingEvents;
  }

  public void setAllowContradictingEvents(boolean allowContradictingEvents) {
    this.allowContradictingEvents = allowContradictingEvents;
  }

  /**
   * @param startIndex the first updated element, inclusive
   * @param endIndex   the last index, exclusive
   */
  public boolean update(int startIndex, int endIndex) {
    return this.addChange(startIndex, ListEvent.UPDATE, ObjectChange.unknownChange(endIndex - startIndex));
  }

  /**
   * @param startIndex the first inserted element, inclusive
   * @param endIndex   the last index, exclusive
   */
  public boolean insert(int startIndex, int endIndex) {
    return this.addChange(startIndex, ListEvent.INSERT, ObjectChange.unknownChange(endIndex - startIndex));
  }

  /**
   * @param startIndex the index of the first element to remove
   * @param endIndex   the last index, exclusive
   */
  public boolean delete(int startIndex, int endIndex) {
    return this.addChange(startIndex, ListEvent.DELETE, ObjectChange.unknownChange(endIndex - startIndex));
  }

  /**
   * Add this change to the list, or return <code>false</code> if that failed
   * because the change is not in increasing order.
   *
   * @return true if the change was successfully applied, or <code>false</code>
   * if no change was made because this change could not be handled.
   * @deprecated use {@link #addChange(int, int, List)}
   */
  @Deprecated
  public boolean addChange(int type, int startIndex, int endIndex, E oldValue, E newValue) {
    return this.addChange(startIndex, type, ObjectChange.create(endIndex - startIndex, oldValue, newValue));
  }

  public boolean addChange(int startIndex, int changeType, List<ObjectChange<E>> events) {
    // remind ourselves of the most recent change
    int lastType;
    int lastStartIndex;
    int lastEndIndex;
    int lastChangedIndex;
    int lastValuesIndex;
    int size = types.size();
    List<ObjectChange<E>> lastChange;
    if (size == 0) {
      lastType = -1;
      lastStartIndex = -1;
      lastChange = null;
      lastEndIndex = 0;
      lastChangedIndex = 0;
      lastValuesIndex = -1;
    } else {
      lastValuesIndex = size - 1;
      lastType = types.get(lastValuesIndex);
      lastStartIndex = starts.get(lastValuesIndex);
      lastChange = values.get(lastValuesIndex);
      lastEndIndex = lastStartIndex + lastChange.size();
      lastChangedIndex = (lastType == ListEvent.DELETE) ? lastStartIndex : lastEndIndex;
    }

    // special case for changes to the last change (which is a common usecase) handled via OverwriteBlockHandler everything else we handle via the more powerful Tree4Delta
    if (lastType != -1 && lastStartIndex <= startIndex) {
      int newEndIndex = startIndex + events.size();
      if (lastEndIndex >= newEndIndex) {
        if (this.handleBlockOverwrite(lastType, lastStartIndex, lastEndIndex, lastValuesIndex, lastChange, changeType, startIndex, newEndIndex, events)) {
          return true;
        }
      }
    }

    // this change breaks the linear-ordering requirement, convert
    // to a more powerful list blocks manager
    if (startIndex < lastChangedIndex) {
      return false;
    }

    // concatenate this change on to the previous one
    if (lastChangedIndex == startIndex && lastType == changeType) {
      this.addToList(size, lastChange, events);
      return true;
      // add this change to the end of the list
    } else {
      starts.add(startIndex);
      types.add(changeType);
      values.add(events);
      return true;
    }
  }

  private void addToList(int size, List<ObjectChange<E>> lastChange, List<ObjectChange<E>> addValues) {
    if (lastChange instanceof ArrayList) {
      lastChange.addAll(addValues);
    } else if (UNMODIFIABLE_COLLECTION != null && UNMODIFIABLE_COLLECTION.isAssignableFrom(lastChange.getClass())) {
      // lastChange is unmodifiable create a new list
      final List<ObjectChange<E>> newList = new ArrayList<>(size + addValues.size());
      newList.addAll(values.get(size - 1));
      newList.addAll(addValues);
      values.set(size - 1, newList);
    } else {
      // trial and error
      try {
        lastChange.addAll(addValues);
      } catch (UnsupportedOperationException ex) {
        final List<ObjectChange<E>> newList = new ArrayList<>(size + addValues.size());
        newList.addAll(values.get(size - 1));
        newList.addAll(addValues);
        values.set(size - 1, newList);
      }
    }
  }

  public boolean isEmpty() {
    return types.isEmpty();
  }

  public void reset() {
    starts.clear();
    types.clear();
    values.clear();
  }

  public Iterator<E> iterator() {
    return new Iterator<>(this);
  }

  private boolean handleBlockOverwrite(int lastType, int lastStartIndex, int lastEndIndex, int lastValuesIndex, List<ObjectChange<E>> lastEvents, int changeType, int newStartIndex, int newEndIndex, List<ObjectChange<E>> newEvents) {
    OverwriteBlockHandler blockHandler = this.findHandler(lastType, changeType);
    if (blockHandler != null) {
      int diffStart = newStartIndex - lastStartIndex;
      int diffEnd = lastEndIndex - newEndIndex;
      int iStart = diffStart;
      int iEnd = lastEvents.size() - diffEnd;
      int iSize = iEnd - iStart;
      int newIdx = 0;
      int updatedType = blockHandler.getNewType();
      boolean typeChanged = updatedType != -1 && updatedType != lastType;
      boolean fullBlockChange = iStart <= 0 && iEnd == lastEvents.size();
      if (typeChanged && !fullBlockChange) {
        // we currently do not allow full block changes when the type has changed
        // we have to do some benchmarking first
        return false;
      }

      int loopStart;
      int loopEnd;
      List<ObjectChange<E>> copyUpdate;
      if (fullBlockChange || !typeChanged) {
        copyUpdate = new ArrayList<>(lastEvents);
        loopStart = iStart;
        loopEnd = iEnd;
      } else {
        copyUpdate = new ArrayList<>(lastEvents.subList(iStart, iEnd));
        loopStart = 0;
        loopEnd = copyUpdate.size();
      }
      boolean removeEntries = false;
      for (int i = loopStart; i < loopEnd; i++) {
        ObjectChange<E> prevChange = copyUpdate.get(i);
        ObjectChange<E> curChange = newEvents.get(newIdx);
        ObjectChange<E> newChange = blockHandler.overwriteEvent(lastType, changeType, prevChange, curChange);
        if (newChange == null) {
          if (!allowContradictingEvents) {
            throw new IllegalStateException("Remove " + i + " undoes prior " + listTypeToString(lastType) + " at the same index! Consider enabling contradicting events.");
          }
          removeEntries = true;
        }
        copyUpdate.set(i, newChange);
        newIdx++;
      }
      if (removeEntries) {
        // remove all null values
        ListIterator<ObjectChange<E>> it = copyUpdate.listIterator();
        while (it.hasNext()) {
          if (it.next() == null) {
            it.remove();
          }
        }
      }
      if (typeChanged) {
        if (fullBlockChange) {
          // full block change is possible without much changes
          values.set(lastValuesIndex, copyUpdate);
          types.set(lastValuesIndex, updatedType);
        } else {
          this.splitEvents(lastType, lastStartIndex, lastEndIndex, lastValuesIndex, iStart, iEnd, lastEvents, updatedType, copyUpdate);
        }
      } else {
        values.set(lastValuesIndex, copyUpdate);
      }
      return true;
    }
    return false;
  }

  // currently unused could be used to split events but it does not worth it atm.
  private void splitEvents(int lastType, int lastStartIndex, int lastEndIndex, int lastValuesIndex, int iStart, int iEnd, List<ObjectChange<E>> lastEvents, int updatedType, List<ObjectChange<E>> updatedEvents) {
    // type has changed we have to split the change into multiple blocks
    final List<ObjectChange<E>> prevList;
    if (iStart > 0) {
      prevList = new ArrayList<>(lastEvents.subList(0, iStart));
    } else {
      prevList = Collections.emptyList();
    }
    final List<ObjectChange<E>> endList;
    if (iEnd <= lastEvents.size() - 1) {
      endList = new ArrayList<>(lastEvents.subList(iEnd, lastEvents.size()));
    } else {
      endList = Collections.emptyList();
    }
    if (prevList.isEmpty() && endList.isEmpty()) {
      values.set(lastValuesIndex, updatedEvents);
      types.set(lastValuesIndex, updatedType);
    } else {
      if (!endList.isEmpty()) {
        types.insert(lastValuesIndex + 1, lastType);
        values.add(lastValuesIndex + 1, endList);
        starts.insert(lastValuesIndex + 1, lastEndIndex - endList.size());
      }
      types.set(lastValuesIndex, updatedType);
      values.set(lastValuesIndex, updatedEvents);
      starts.set(lastValuesIndex, lastStartIndex + prevList.size());
      if (!prevList.isEmpty()) {
        types.insert(lastValuesIndex, lastType);
        values.add(lastValuesIndex, prevList);
        starts.insert(lastValuesIndex, lastStartIndex);
      }
    }
  }

  //TODO: find a more generic solution but this is fast for now
  private OverwriteBlockHandler findHandler(int oldType, int newType) {
    if (oldType == ListEvent.UPDATE && newType == ListEvent.UPDATE) {
      return this.blockHandlers.get(0);
    } else if (oldType == ListEvent.INSERT && newType == ListEvent.DELETE) {
      return this.blockHandlers.get(1);
    } else if (oldType == ListEvent.INSERT && newType == ListEvent.UPDATE) {
      return this.blockHandlers.get(2);
    } else if (oldType == ListEvent.DELETE && newType == ListEvent.INSERT) {
      return this.blockHandlers.get(3);
    }
    return null;
  }

  private String listTypeToString(int type) {
    switch (type) {
      case ListEvent.INSERT:
        return "insert";
      case ListEvent.DELETE:
        return "delete";
      case ListEvent.UPDATE:
        return "update";
      default:
        return "unknown";
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < types.size(); i++) {
      if (i != 0) {
        result.append(", ");
      }

      // write the type
      int type = types.get(i);
      if (type == ListEvent.INSERT) result.append("+");
      else if (type == ListEvent.UPDATE) result.append("U");
      else if (type == ListEvent.DELETE) result.append("X");

      // write the range
      int start = starts.get(i);
      int end = start + values.get(i).size();
      result.append(start);
      if (end != start) {
        result.append("-");
        result.append(end);
      }
    }

    return result.toString();
  }

  private static interface OverwriteBlockHandler {
    int getNewType();

    <E> ObjectChange<E> overwriteEvent(int lastType, int newType, ObjectChange<E> prevChange, ObjectChange<E> curChange);
  }

  private static class OverwriteUpdateBlockHandler implements OverwriteBlockHandler {
    @Override
    public int getNewType() {
      return ListEvent.UPDATE;
    }

    @Override
    public <E> ObjectChange<E> overwriteEvent(int lastType, int newType, ObjectChange<E> prevChange, ObjectChange<E> curChange) {
      // create new update event by merging the previous and new event
      return ObjectChange.create(prevChange.getOldValue(), curChange.getNewValue());
    }
  }

  private static class OverwriteInsertDeleteBlockHandler implements OverwriteBlockHandler {
    @Override
    public int getNewType() {
      return -1;
    }

    @Override
    public <E> ObjectChange<E> overwriteEvent(int lastType, int newType, ObjectChange<E> prevChange, ObjectChange<E> curChange) {
      return null;
    }
  }

  private static class OverwriteInsertUpdateBlockHandler implements OverwriteBlockHandler {
    @Override
    public int getNewType() {
      return ListEvent.INSERT;
    }

    @Override
    public <E> ObjectChange<E> overwriteEvent(int lastType, int newType, ObjectChange<E> prevChange, ObjectChange<E> curChange) {
      // update the old insert event with the new value
      return ObjectChange.create(ListEvent.unknownValue(), curChange.getNewValue());
    }
  }

  private static class OverwriteDeleteInsertBlockHandler implements OverwriteBlockHandler {
    @Override
    public int getNewType() {
      return ListEvent.UPDATE;
    }

    @Override
    public <E> ObjectChange<E> overwriteEvent(int lastType, int newType, ObjectChange<E> prevChange, ObjectChange<E> curChange) {
      // create new update event by merging the previous and new event
      return ObjectChange.create(prevChange.getOldValue(), curChange.getNewValue());
    }
  }

  /**
   * Iterate through the list of changes in this sequence.
   */
  public static class Iterator<E> {
    private final BlockSequence<E> source;

    private int blockIndex = -1;
    private int offset = 0;

    private int startIndex = -1;
    private int endIndex = -1;
    private int type = -1;

    private List<ObjectChange<E>> blockChanges = null;

    public Iterator(BlockSequence<E> source) {
      this.source = Objects.requireNonNull(source);
    }

    public Iterator<E> copy() {
      Iterator<E> result = new Iterator<>(this.source);
      this.copy(result);
      return result;
    }

    public Iterator<E> deepCopy() {
      Iterator<E> result = new Iterator<>(new BlockSequence<>(this.source));
      this.copy(result);
      return result;
    }

    public Iterator<E> withBlockSequence(BlockSequence<E> sequence){
      Iterator<E> result = new Iterator<>(sequence);
      this.copy(result);
      return result;
    }

    public int getIndex() {
      if (type == ListEvent.INSERT || type == ListEvent.UPDATE) {
        return startIndex + offset;
      } else if (type == ListEvent.DELETE) {
        return startIndex;
      } else {
        throw new IllegalStateException();
      }
    }

    public int getBlockStart() {
      if (startIndex == -1)
        throw new IllegalStateException("The ListEvent is not currently in a state to return a block start index");
      return startIndex;
    }

    public int getBlockEnd() {
      if (endIndex == -1)
        throw new IllegalStateException("The ListEvent is not currently in a state to return a block end index");
      return endIndex;
    }

    public int getType() {
      if (type == -1) throw new IllegalStateException("The ListEvent is not currently in a state to return a type");
      return type;
    }

    public E getOldValue() {
      if (blockChanges == null) throw new IllegalStateException("The ListEvent is not currently in a state to " +
              "return a old value");
      return this.blockChanges.get(this.offset).getOldValue();
    }

    public E getNewValue() {
      if (blockChanges == null) throw new IllegalStateException("The ListEvent is not currently in a state to " +
              "return a new value");
      return this.blockChanges.get(this.offset).getNewValue();
    }

    public ObjectChange<E> getChange() {
      if (blockChanges == null) throw new IllegalStateException("The ListEvent is not currently in a state to " +
              "return the current change");
      return this.blockChanges.get(this.offset);
    }

    public List<ObjectChange<E>> getBlockChanges() {
      if (blockChanges == null) throw new IllegalStateException("The ListEvent is not currently in a state to " +
              "return a new value");
      return this.blockChanges;
    }

    /**
     * Move to the next changed index, possibly within the same block.
     */
    public boolean next() {
      // increment within the block
      if (offset + 1 < endIndex - startIndex) {
        offset++;
        return true;
      } else if (this.nextBlock()) {
        return true;
      } else {
        return false;
      }
    }

    /**
     * Move to the next changed block.
     */
    public boolean nextBlock() {
      // increment to the next block
      if (blockIndex + 1 < source.types.size()) {
        blockIndex++;
        offset = 0;
        startIndex = source.starts.get(blockIndex);
        blockChanges = Collections.unmodifiableList(source.values.get(blockIndex));
        endIndex = startIndex + blockChanges.size();
        type = source.types.get(blockIndex);
        // skip empty blocks
        if (blockChanges.size() <= 0) {
          return this.nextBlock();
        }
        return true;
        // no more left
      } else {
        return false;
      }
    }

    /**
     * @return true if theres another changed index
     */
    public boolean hasNext() {
      // increment within the block
      if (offset + 1 < endIndex - startIndex) return true;

      // increment to the next block
      if (blockIndex + 1 < source.types.size()) return true;

      // no more left
      return false;
    }

    /**
     * @return true if theres another changed block
     */
    public boolean hasNextBlock() {
      // increment to the next block
      if (blockIndex + 1 < source.types.size()) return true;

      // no more left
      return false;
    }

    private void copy(Iterator<E> it) {
      it.blockIndex = this.blockIndex;
      it.offset = this.offset;
      it.startIndex = this.startIndex;
      it.endIndex = this.endIndex;
      it.type = this.type;
      it.blockChanges = this.blockChanges;
    }
  }
}