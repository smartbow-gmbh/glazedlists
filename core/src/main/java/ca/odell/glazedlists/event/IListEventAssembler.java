package ca.odell.glazedlists.event;

import ca.odell.glazedlists.EventList;

import java.util.List;

public interface IListEventAssembler<E> {
  /**
   * Indicate whether or not an event is in progress. Intended for testing purposes.
   */
  boolean isEventInProgress();

  /**
   * Starts a new atomic change to this list change queue.
   *
   * <p>This simple change event does not support change events nested within.
   * To allow other methods to nest change events within a change event, use
   * beginEvent(true).
   */
  void beginEvent();

  /**
   * Starts a new atomic change to this list change queue. This signature
   * allows you to specify allowing nested changes. This simply means that
   * you can call other methods that contain a beginEvent(), commitEvent()
   * block and their changes will be recorded but not fired. This allows
   * the creation of list modification methods to call simpler list modification
   * methods while still firing a single ListEvent to listeners.
   *
   * @see <a href="https://glazedlists.dev.java.net/issues/show_bug.cgi?id=52">Bug 52</a>
   *
   * @param allowNestedEvents false to throw an exception
   *                          if another call to beginEvent() is made before
   *                          the next call to commitEvent(). Nested events allow
   *                          multiple method's events to be composed into a single
   *                          event.
   */
  void beginEvent(boolean allowNestedEvents);

  /**
   * Add to the current ListEvent the insert of the element at
   * the specified index, with the specified previous value.
   */
  void elementInserted(int index, E newValue);

  /**
   * Adds a block of insert events.
   */
  void elementInserted(int index, List<E> newValues);

  /**
   * Add to the current ListEvent the removal of the element at the specified
   * index, with the specified previous value.
   */
  void elementDeleted(int index, E oldValue);

  /**
   * Adds a block of removal events
   */
  void elementDeleted(int index, List<E> oldValues);

  /**
   * Adds a block of update events
   */
  void elementUpdated(int index, List<ObjectChange<E>> changes);

  /**
   * Add to the current ListEvent the update of the element at the specified
   * index, with the specified previous value.
   */
  void elementUpdated(int index, E oldValue, E newValue);

  void elementUpdated(int index, List<E> oldValues, List<E> newValues);

  void elementUpdated(int index, ObjectChange<E> change);

  /**
   * Convenience method for appending a single change of the specified type.
   */
  void addChange(int type, int index, ObjectChange<E> event);

  /**
   * Adds a block of changes to the set of list changes. The change block
   * allows a range of changes to be grouped together for efficiency.
   */
  void addChange(int type, int startIndex, List<ObjectChange<E>> changeEvents);

  /**
   * Sets the current event as a reordering. Reordering events cannot be
   * combined with other events.
   */
  void reorder(int[] reorderMap, List<ObjectChange<E>> changes);

  /**
   * Forwards the event. This is a convenience method that does the following:
   * <br>1. beginEvent()
   * <br>2. For all changes in sourceEvent, apply those changes to this
   * <br>3. commitEvent()
   *
   * <p>Note that this method should be preferred to manually forwarding events
   * because it is heavily optimized.
   *
   * <p>Note that currently this implementation does a best effort to preserve
   * reorderings. This means that a reordering is lost if it is combined with
   * any other ListEvent.
   */
  void forwardEvent(ListEvent<?> listChanges);

  /**
   * Commits the current atomic change to this list change queue. This will
   * notify all listeners about the change.
   *
   * <p>If the current event is nested within a greater event, this will simply
   * change the nesting level so that further changes are applied directly to the
   * parent change.
   */
  void commitEvent();

  /**
   * Discards the current atomic change to this list change queue. This does
   * not notify any listeners about any changes.
   *
   * <p>The caller of this method is responsible for returning the EventList
   * to its state before the event began. If they fail to do so, the EventList
   * pipeline may be in an inconsistent state.
   *
   * <p>If the current event is nested within a greater event, this will
   * discard changes at the current nesting level and that further changes
   * are still applied directly to the parent change.
   */
  void discardEvent();

  /**
   * Returns <tt>true</tt> if the current atomic change to this list change
   * queue is empty; <tt>false</tt> otherwise.
   *
   * @return <tt>true</tt> if the current atomic change to this list change
   * queue is empty; <tt>false</tt> otherwise
   */
  boolean isEventEmpty();

  /**
   * Registers the specified listener to be notified whenever new changes
   * are appended to this list change sequence.
   *
   * <p>For each listener, a ListEvent is created, which provides
   * a read-only view to the list changes in the list. The same
   * ListChangeView object is used for all notifications to the specified
   * listener, so if a listener does not process a set of changes, those
   * changes will persist in the next notification.
   *
   * @param listChangeListener event listener != null
   * @throws NullPointerException if the specified listener is null
   */
  void addListEventListener(ListEventListener<? super E> listChangeListener);

  /**
   * Removes the specified listener from receiving notification when new
   * changes are appended to this list change sequence.
   *
   * <p>This uses the <code>==</code> identity comparison to find the listener
   * instead of <code>equals()</code>. This is because multiple Lists may be
   * listening and therefore <code>equals()</code> may be ambiguous.
   *
   * @param listChangeListener event listener != null
   * @throws NullPointerException     if the specified listener is null
   * @throws IllegalArgumentException if the specified listener wasn't added before
   */
  void removeListEventListener(ListEventListener<? super E> listChangeListener);

  /**
   * Get all {@link ListEventListener}s observing the {@link EventList}.
   */
  List<ListEventListener<E>> getListEventListeners();

  /**
   * Returns the uncommited changes of the current event level
   * @return
   */
  ListEvent<E> getCurrentChanges();

  ListEvent<E> emptyEvent();

  ListEventAssembler<E> newAssembler();

  /**
   * Create a new {@link ListEventPublisher} for an {@link EventList} not attached
   * to any other {@link EventList}s.
   */
  static ListEventPublisher createListEventPublisher() {
    return new SequenceDependenciesEventPublisher();
  }
}
