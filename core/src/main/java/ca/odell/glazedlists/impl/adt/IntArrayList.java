package ca.odell.glazedlists.impl.adt;

import java.util.*;

/**
 * An array-backed list of ints. A single array is used to store and manipulate
 * all elements. Reallocations are governed by a {@link ArraySizingStrategy}
 * and may be expensive if they move around really large chunks of memory.
 * It also has some dedicated methods to easily push, pop, and discard elements
 * from the end of the array, emulating so a {@linkplain Stack java.util.Stack}.
 * (see "Stack-emulating methods)
 * <p>
 * Collections.
 */
public class IntArrayList implements Cloneable {
  private final static int DEFAULT_EXPECTED_ELEMENTS = 1 << 3;
  private final static int[] EMPTY = (new int[(0)]);

  /**
   * Internal array for storing the list. The array may be larger than the current size
   * ({@link #size()}).
   *
   * <p>
   * Direct list iteration: iterate buffer[i] for i in [0; size()[
   * </p>
   */
  public int[]

          buffer;

  /**
   * Current number of elements stored in {@link #buffer}.
   */
  protected int elementsCount;

  /**
   * Buffer resizing strategy.
   */
  protected final ArraySizingStrategy resizer;

  /**
   * Default constructor: Create with default sizing strategy and initial capacity for storing
   * {@link IntArrayList#DEFAULT_EXPECTED_ELEMENTS} elements.
   *
   * @see BoundedProportionalArraySizingStrategy
   */
  public IntArrayList() {
    this(DEFAULT_EXPECTED_ELEMENTS);
  }

  /**
   * Create with default sizing strategy and the given initial capacity.
   *
   * @see BoundedProportionalArraySizingStrategy
   */
  public IntArrayList(final int initialCapacity) {
    this(initialCapacity, new BoundedProportionalArraySizingStrategy());
  }

  /**
   * Create with a custom buffer resizing strategy.
   */
  public IntArrayList(final int initialCapacity, final ArraySizingStrategy resizer) {
    assert resizer != null;

    this.resizer = resizer;

    //allocate internal buffer
    ensureBufferSpace(Math.max(DEFAULT_EXPECTED_ELEMENTS, initialCapacity));
  }

  /**
   * Creates a new list from elements of another container.
   */
  public IntArrayList(final IntArrayList container) {
    this(container.size());
    addAll(container);
  }


  /**
   * {@inheritDoc}
   */
  public void add(final int e1) {
    ensureBufferSpace(1);
    this.buffer[this.elementsCount++] = e1;
  }

  /**
   * Appends two elements at the end of the list. To add more than two elements,
   * use <code>add</code> (vararg-version) or access the buffer directly (tight
   * loop).
   */
  public void add(final int e1, final int e2) {
    ensureBufferSpace(2);
    this.buffer[this.elementsCount++] = e1;
    this.buffer[this.elementsCount++] = e2;
  }

  /**
   * Add all elements from a range of given array to the list.
   *
   * @param elements
   * @param start
   * @param length
   */
  public void add(final int[] elements, final int start, final int length) {
    assert length >= 0 : "Length must be >= 0";

    ensureBufferSpace(length);
    System.arraycopy(elements, start, this.buffer, this.elementsCount, length);
    this.elementsCount += length;
  }

  /**
   * Vararg-signature method for adding elements at the end of the list.
   * <p><b>This method is handy, but costly if used in tight loops (anonymous
   * array passing)</b></p>
   *
   * @param elements
   */
  public void add(final int... elements) {
    add(elements, 0, elements.length);
  }

  /**
   * Adds all elements from another container.
   * @param container
   * @return the number of elements added from the container
   */
  public int addAll(final IntArrayList container) {
    int size = 0;
    for (int i = 0; i<container.size(); i++) {
      add(container.get(i));
      size++;
    }
    return size;
  }

  public void insert(final int index, final int e1) {
    assert (index >= 0 && index <= size()) : "Index " + index + " out of bounds [" + 0 + ", " + size() + "].";

    ensureBufferSpace(1);
    System.arraycopy(this.buffer, index, this.buffer, index + 1, this.elementsCount - index);
    this.buffer[index] = e1;
    this.elementsCount++;
  }

  public int get(final int index) {
    assert (index >= 0 && index < size()) : "Index " + index + " out of bounds [" + 0 + ", " + size() + "[.";

    return ((this.buffer[index]));
  }

  public int set(final int index, final int e1) {
    assert (index >= 0 && index < size()) : "Index " + index + " out of bounds [" + 0 + ", " + size() + "[.";

    final int v = ((this.buffer[index]));
    this.buffer[index] = e1;
    return v;
  }

  public int remove(final int index) {
    assert (index >= 0 && index < size()) : "Index " + index + " out of bounds [" + 0 + ", " + size() + "[.";

    final int v = ((this.buffer[index]));
    if (index + 1 < this.elementsCount) {
      System.arraycopy(this.buffer, index + 1, this.buffer, index, this.elementsCount - index - 1);
    }
    this.elementsCount--;

    return v;
  }

  public void removeRange(final int fromIndex, final int toIndex) {

    checkRangeBounds(fromIndex, toIndex);

    System.arraycopy(this.buffer, toIndex, this.buffer, fromIndex, this.elementsCount - toIndex);

    final int count = toIndex - fromIndex;
    this.elementsCount -= count;


  }

  public int removeFirst(final int e1) {
    final int index = indexOf(e1);
    if (index >= 0) {
      remove(index);
    }
    return index;
  }

  public int removeLast(final int e1) {
    final int index = lastIndexOf(e1);
    if (index >= 0) {
      remove(index);
    }
    return index;
  }

  public int removeAll(final int e1) {
    int to = 0;
    final int[] buffer = ((this.buffer));

    for (int from = 0; from < this.elementsCount; from++) {
      if (((e1) == (buffer[from]))) {

        continue;
      }

      if (to != from) {
        buffer[to] = buffer[from];

      }
      to++;
    }

    final int deleted = this.elementsCount - to;
    this.elementsCount = to;
    return deleted;
  }


  public boolean contains(final int e1) {
    return indexOf(e1) >= 0;
  }

  public int indexOf(final int e1) {
    final int[] buffer = ((this.buffer));

    for (int i = 0; i < this.elementsCount; i++) {
      if (((e1) == (buffer[i]))) {
        return i;
      }
    }

    return -1;
  }

  public int lastIndexOf(final int e1) {
    final int[] buffer = ((this.buffer));

    for (int i = this.elementsCount - 1; i >= 0; i--) {
      if (((e1) == (buffer[i]))) {
        return i;
      }
    }

    return -1;
  }

  /**
   * Increases the capacity of this instance, if necessary, to ensure
   * that it can hold at least the number of elements specified by
   * the minimum capacity argument.
   */
  public void ensureCapacity(final int minCapacity) {
    if (minCapacity > this.buffer.length) {
      ensureBufferSpace(minCapacity - size());
    }
  }

  /**
   * Ensures the internal buffer has enough free slots to store
   * <code>expectedAdditions</code>. Increases internal buffer size if needed.
   */
  @SuppressWarnings("boxing")
  protected void ensureBufferSpace(final int expectedAdditions) {
    final int bufferLen = (this.buffer == null ? 0 : this.buffer.length);

    if (this.elementsCount > bufferLen - expectedAdditions) {
      final int newSize = this.resizer.grow(bufferLen, this.elementsCount, expectedAdditions);

      try {
        final int[] newBuffer = (new int[(newSize)]);
        if (bufferLen > 0) {
          System.arraycopy(this.buffer, 0, newBuffer, 0, this.buffer.length);
        }
        this.buffer = newBuffer;

      } catch (final OutOfMemoryError e) {
        throw new BufferAllocationException(
                "Not enough memory to allocate buffers to grow from %d -> %d elements",
                e,
                bufferLen,
                newSize);
      }
    }
  }

  /**
   * Truncate or expand the list to the new size. If the list is truncated, the buffer
   * will not be reallocated (use {@link #trimToSize()} if you need a truncated buffer).
   * If the list is expanded, the elements beyond the current size are initialized with JVM-defaults
   * (zero or <code>null</code> values).
   */
  public void resize(final int newSize) {
    if (newSize <= this.buffer.length) {
      if (newSize < this.elementsCount) {
        //there is no point in resetting to "null" elements
        //that becomes non-observable anyway. Still,
        //resetting is needed for GC in case of Objects because they may become "free"
        //if not referenced anywhere else.

      } else {
        //in all cases, the contract of resize if that new elements
        //are set to default values.
        Arrays.fill(this.buffer, this.elementsCount, newSize, (0));
      }
    } else {
      ensureCapacity(newSize);
    }

    this.elementsCount = newSize;
  }

  public int size() {
    return this.elementsCount;
  }


  public int capacity() {

    return this.buffer.length;
  }

  /**
   * Trim the internal buffer to the current size.
   */
  /*  */
  public void trimToSize() {
    if (size() != this.buffer.length) {
      this.buffer = (int[]) toArray();
    }
  }

  public void clear() {

    this.elementsCount = 0;
  }

  /**
   * Sets the number of stored elements to zero and releases the internal storage array.
   */
  /*  */
  public void release() {
    this.buffer = (int[]) EMPTY;
    this.elementsCount = 0;
  }

  public int[] toArray(final int[] target) {
    System.arraycopy(this.buffer, 0, target, 0, this.elementsCount);
    return target;
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("boxing")
  public int[] toArray() {
    try {

      return toArray((new int[(size())]));
    } catch (final OutOfMemoryError e) {

      throw new BufferAllocationException(
              "Not enough memory to allocate a '%s'.toArray() of  %d elements",
              e,
              this.getClass().toString(),
              size());
    }
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Clone this object.
   */
  @Override
  public IntArrayList clone() {
    //placeholder
    final IntArrayList cloned = new IntArrayList(DEFAULT_EXPECTED_ELEMENTS, this.resizer);

    //clone raw buffers
    cloned.buffer = this.buffer.clone();

    cloned.elementsCount = this.elementsCount;

    return cloned;
  }

  /**
   * Convert the contents of this container to a human-friendly string.
   */
  @Override
  public String toString() {
    return Arrays.toString(this.toArray());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    int h = 1;
    final int max = this.elementsCount;
    final int[] buffer = ((this.buffer));

    for (int i = 0; i < max; i++) {
      h = 31 * h + mix32(buffer[i]);
    }
    return h;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  /*  */
  public boolean equals(final Object obj) {
    if (obj != null) {
      if (obj == this) {
        return true;
      }

      //optimized for IntArrayList
      if (obj instanceof IntArrayList) {

        final IntArrayList other = (IntArrayList) obj;
        return other.size() == this.size() && rangeEquals(other.buffer, this.buffer, size());

      }
    }
    return false;
  }

  /**
   * Compare a range of values in two arrays.
   */
  private boolean rangeEquals(int[] b1, int[] b2, int length) {
    for (int i = 0; i < length; i++) {
      if (!((b1[i]) == (b2[i]))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Mix a 4-byte sequence (Java int), MH3's plain finalization step.
   */
  private static int mix32(int k) {
    k = (k ^ (k >>> 16)) * 0x85ebca6b;
    k = (k ^ (k >>> 13)) * 0xc2b2ae35;

    return k ^ (k >>> 16);
  }

  /**
   * Returns a new object of this class with no need to declare generic type (shortcut
   * instead of using a constructor).
   */
  public static/*  */
  IntArrayList newInstance() {
    return new IntArrayList();
  }

  /**
   * Returns a new object of this class with no need to declare generic type (shortcut
   * instead of using a constructor).
   */
  public static/*  */
  IntArrayList newInstance(final int initialCapacity) {
    return new IntArrayList(initialCapacity);
  }

  /**
   * Create a list from a variable number of arguments or an array of
   * <code>int</code>.
   */
  public static/*  */
  IntArrayList from(final int... elements) {
    final IntArrayList list = new IntArrayList(elements.length);
    list.add(elements);
    return list;
  }


  ///////////////////////////////////////////////////////////////////////
  // Stack-like methods
  ///////////////////////////////////////////////////////////////////////

  /**
   * Stack emulating method: Adds one int at the end of the array,
   * equivalent of {@link #add(int)}
   *
   * @param e1
   */
  public void pushLast(final int e1) {
    add(e1);
  }

  /**
   * Stack emulating method: Adds two ints at the end of the array,
   * synonym of {@link #add(int, int)}
   *
   * @param e1
   * @param e2
   */
  public void pushLast(final int e1, final int e2) {
    add(e1, e2);
  }

  /**
   * Stack emulating method: Adds three ints at the end of the array
   *
   * @param e1
   * @param e2
   * @param e3
   */
  public void pushLast(final int e1, final int e2, final int e3) {
    ensureBufferSpace(3);
    this.buffer[this.elementsCount++] = e1;
    this.buffer[this.elementsCount++] = e2;
    this.buffer[this.elementsCount++] = e3;
  }

  /**
   * Stack emulating method: Adds four ints at the end of the array
   *
   * @param e1
   * @param e2
   * @param e3
   * @param e4
   */
  public void pushLast(final int e1, final int e2, final int e3, final int e4) {
    ensureBufferSpace(4);
    this.buffer[this.elementsCount++] = e1;
    this.buffer[this.elementsCount++] = e2;
    this.buffer[this.elementsCount++] = e3;
    this.buffer[this.elementsCount++] = e4;
  }

  /**
   * Stack emulating method: Add a range of array elements at the end of array,
   * synonym of {@link #add(int[], int, int)}
   */
  public void pushLast(final int[] elements, final int start, final int len) {
    add(elements, start, len);
  }

  /**
   * Stack emulating method: Vararg-signature method for pushing elements at the end of the array.
   * <p>
   * <b>This method is handy, but costly if used in tight loops (anonymous array
   * passing)</b>
   * </p>
   */
  public final void pushLast(final int... elements) {
    add(elements, 0, elements.length);
  }


  /**
   * Stack emulating method: Discard an arbitrary number of elements from the end of the array.
   *
   * @param count
   */
  public void discardLast(final int count) {
    assert this.elementsCount >= count;

    this.elementsCount -= count;
    /*  */
  }

  /**
   * Stack emulating method: Discard the last element of the array.
   */
  public void discardLast() {
    assert this.elementsCount > 0;

    this.elementsCount--;
    /*  */
  }

  /**
   * Stack emulating method: Discard the last element of the array, and return it.
   */
  public int popLast() {
    assert this.elementsCount > 0;

    final int v = ((this.buffer[--this.elementsCount]));
    /*  */
    return v;
  }

  /**
   * Stack emulating method: Peek at the last element on the array.
   */
  public int peekLast() {

    assert this.elementsCount > 0;
    return ((this.buffer[this.elementsCount - 1]));
  }

  private void checkRangeBounds(final int beginIndex, final int endIndex) {

    if (beginIndex > endIndex) {

      throw new IllegalArgumentException("Index beginIndex " + beginIndex + " is > endIndex " + endIndex);
    }

    if (beginIndex < 0) {

      throw new IndexOutOfBoundsException("Index beginIndex < 0");
    }

    if (endIndex > this.elementsCount) {

      throw new IndexOutOfBoundsException("Index endIndex " + endIndex + " out of bounds [" + 0 + ", " + this.elementsCount + "].");
    }
  }

  /**
   * Resizing (growth) strategy for array-backed buffers.
   */
  public interface ArraySizingStrategy {
    /**
     * @param currentBufferLength Current size of the array (buffer). This number
     *                            should comply with the strategy's policies (it is a result of initial rounding
     *                            or further growths). It can also be zero, indicating the growth from an empty
     *                            buffer.
     * @param elementsCount       Number of elements stored in the buffer.
     * @param expectedAdditions   Expected number of additions (resize hint).
     * @return Must return a new size at least as big as to hold
     * <code>elementsCount + expectedAdditions</code>.
     * @throws BufferAllocationException
     */
    int grow(int currentBufferLength, int elementsCount, int expectedAdditions) throws BufferAllocationException;
  }

  /**
   * Array resizing proportional to the current buffer size, optionally kept within the
   * given minimum and maximum growth limits. Java's {@link ArrayList} uses:
   * <pre>
   * minGrow = 1
   * maxGrow = Integer.MAX_VALUE (unbounded)
   * growRatio = 1.5f
   * </pre>
   */
  public final static class BoundedProportionalArraySizingStrategy
          implements ArraySizingStrategy {
    public static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - /* aligned array header + slack */32;

    /**
     * Minimum grow count.
     */
    public final static int DEFAULT_MIN_GROW_COUNT = 10;

    /**
     * Maximum grow count (unbounded).
     */
    public final static int DEFAULT_MAX_GROW_COUNT = BoundedProportionalArraySizingStrategy.MAX_ARRAY_LENGTH;

    /**
     * Default resize is by half the current buffer's size.
     */
    public final static float DEFAULT_GROW_RATIO = 1.5f;

    /**
     * Minimum number of elements to grow, if limit exceeded.
     */
    public final int minGrowCount;

    /**
     * Maximum number of elements to grow, if limit exceeded.
     */
    public final int maxGrowCount;

    /**
     * The current buffer length is multiplied by this ratio to get the
     * first estimate for the new size. To double the size of the current
     * buffer, for example, set to <code>2</code>.
     */
    public final float growRatio;

    /**
     * Create the default sizing strategy.
     */
    public BoundedProportionalArraySizingStrategy() {
      this(BoundedProportionalArraySizingStrategy.DEFAULT_MIN_GROW_COUNT, BoundedProportionalArraySizingStrategy.DEFAULT_MAX_GROW_COUNT, BoundedProportionalArraySizingStrategy.DEFAULT_GROW_RATIO);
    }

    /**
     * Create the sizing strategy with custom policies.
     */
    public BoundedProportionalArraySizingStrategy(final int minGrow, final int maxGrow, final float ratio) {
      assert minGrow >= 1 : "Min grow must be >= 1.";
      assert maxGrow >= minGrow : "Max grow must be >= min grow.";
      assert ratio >= 1f : "Growth ratio must be >= 1 (was " + ratio + ").";

      this.minGrowCount = minGrow;
      this.maxGrowCount = maxGrow;
      this.growRatio = ratio - 1.0f;
    }

    /**
     * Grow according to {@link #growRatio}, {@link #minGrowCount} and {@link #maxGrowCount}.
     */
    @SuppressWarnings("boxing")
    @Override
    public int grow(final int currentBufferLength, final int elementsCount, final int expectedAdditions) {
      long growBy = (long) (currentBufferLength * this.growRatio);

      growBy = Math.max(growBy, this.minGrowCount);
      growBy = Math.min(growBy, this.maxGrowCount);
      final long growTo = Math.min(BoundedProportionalArraySizingStrategy.MAX_ARRAY_LENGTH, growBy + currentBufferLength);

      final long newSize = Math.max((long) elementsCount + expectedAdditions, growTo);

      if (newSize > BoundedProportionalArraySizingStrategy.MAX_ARRAY_LENGTH) {

        throw new BufferAllocationException(
                "Java array size exceeded (current length: %d, elements: %d, expected additions: %d)",
                currentBufferLength,
                elementsCount,
                expectedAdditions);
      }

      return (int) newSize;
    }
  }

  @SuppressWarnings("serial")
  public static class BufferAllocationException extends RuntimeException {
    BufferAllocationException(final String message) {
      super(message);
    }

    public BufferAllocationException(final String message, final Object... args) {
      this(message, null, args);
    }

    public BufferAllocationException(final String message, final Throwable t, final Object... args) {
      super(BufferAllocationException.formatMessage(message, t, args), t);
    }

    private static String formatMessage(final String message, final Throwable t, final Object... args) {

      String formattedMessage = "";

      try {

        formattedMessage = String.format(message, args);
      } catch (final IllegalFormatException e) {

        //something bad happened , replace by a default message
        formattedMessage = "'" + message + "' message has ILLEGAL FORMAT, ARGS SUPPRESSED !";

        //Problem is, this IllegalFormatException may have masked the originally sent exception t,
        //so be it.
        //We can't use Throwable.setSuppressed() (Java 1.7+) because we want to continue
        //to accomodate Java 1.5+.
      }

      return formattedMessage;
    }
  }
}
