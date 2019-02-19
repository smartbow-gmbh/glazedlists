package ca.odell.glazedlists;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.TransactionEventAssembler;
import ca.odell.glazedlists.event.ListEventAssembler;
import ca.odell.glazedlists.impl.testing.GlazedListsTests;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TransactionEventAssemblerTest {

    private BasicEventList<String> target;
    private TransactionEventAssembler<String> txList;
    private GlazedListsTests.ListEventCounter<String> counter;

    @Before
    public void setUp() {
        target = new BasicEventList<>();
        txList = new TransactionEventAssembler<>();
        counter = new GlazedListsTests.ListEventCounter<>();
        txList.addListEventListener(evt -> ListEventAssembler.applyListEvent(evt, target));
        txList.addBufferedListEventListener(evt -> counter.listChanged(evt));
    }

    @After
    public void tearDown() {
        txList.removeListEventListener(counter);
        txList.dispose();

        counter = null;
        txList = null;
        target = null;
    }

    @Test
    public void testRollbackSimpleListEvent() {
        assertState(GlazedListsTests.stringToList(""), 0);

        // 1. rollback simple add
        txList.beginEvent();
        txList.elementInserted(0, "0");
        assertState(GlazedListsTests.stringToList("0"), 0);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList(""), 0);

        txList.elementInserted(0, "0");
        assertEquals(1, counter.getCountAndReset());

        // 2. rollback simple remove
        txList.beginEvent();
        assertState(GlazedListsTests.stringToList("0"), 0);
        txList.elementDeleted(0, "0");
        assertState(GlazedListsTests.stringToList(""), 0);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList("0"), 0);

        // 3. rollback simple update
        txList.beginEvent();
        assertState(GlazedListsTests.stringToList("0"), 0);
        txList.elementUpdated(0, "0", "1");
        assertState(GlazedListsTests.stringToList("1"), 0);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList("0"), 0);
    }

    @Test
    public void testRollbackComplexListEvent() {
        txList.elementInserted(0, GlazedListsTests.stringToList("ABC"));
        assertState(GlazedListsTests.stringToList("ABC"), 1);

        // 1. rollback add, remove, set as a single transaction
        txList.beginEvent();
        txList.elementInserted(3, "D");
        txList.elementDeleted(1, "B");
        txList.elementUpdated(0, "A", "B");
        assertState(GlazedListsTests.stringToList("BCD"), 0);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList("ABC"), 0);
    }

    @Test
    public void testCommitComplex() {
        txList.elementInserted(0, GlazedListsTests.stringToList("ABC"));
        assertState(GlazedListsTests.stringToList("ABC"), 1);

        // 1. commit add, remove, set as a single transaction
        txList.beginEvent();
        txList.elementInserted(3, "D");
        txList.elementDeleted(1, "B");
        txList.elementUpdated(0, "A", "B");
        assertState(GlazedListsTests.stringToList("BCD"), 0);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("BCD"), 1);
    }

    /**
     * If a transaction is "buffered" it accumulates all ListEvents during the
     * transaction into a single uber-ListEvent and fires it on commit.
     */
    @Test
    public void testCommitOnBufferedTransactionFiresEvent() {
        assertState(GlazedListsTests.stringToList(""), 0);

        // 1. commit should fire a single ListEvent
        txList.beginEvent(true);
        txList.elementInserted(0, "A");
        txList.elementInserted(1, "B");
        assertState(GlazedListsTests.stringToList("AB"), 0);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("AB"), 1);

        // 2. do it again to ensure that the txList is still usable
        txList.beginEvent(true);
        txList.elementInserted(2, "C");
        txList.elementInserted(3, "D");
        assertState(GlazedListsTests.stringToList("ABCD"), 0);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("ABCD"), 1);
    }

    /**
     * If a transaction is "buffered" it accumulates all ListEvents during the
     * transaction into a single uber-ListEvent and thus fires no event on rollback.
     */
    @Test
    public void testRollbackOnBufferedTransactionFiresNoEvent() {
        assertState(GlazedListsTests.stringToList(""), 0);

        // 1. rollback should no ListEvent
        txList.beginEvent(true);
        txList.elementInserted(0, "A");
        txList.elementInserted(1, "B");
        assertState(GlazedListsTests.stringToList("AB"), 0);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList(""), 0);

        // 2. do it again to ensure that the txList is still usable
        txList.beginEvent(true);
        txList.elementInserted(0, "C");
        txList.elementInserted(1, "D");
        assertState(GlazedListsTests.stringToList("CD"), 0);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList(""), 0);
    }

    /**
     * If a transaction is "live" it forwards all ListEvents as they are
     * received and thus no ListEvent is fired on commit.
     */
    @Test
    public void testCommitOnLiveTransactionFiresNoEvent() {
        assertState(GlazedListsTests.stringToList(""), 0);

        // 1. commit should fire no ListEvent
        txList.beginEvent(false);
        txList.elementInserted(0, "A");
        txList.elementInserted(1, "B");
        assertState(GlazedListsTests.stringToList("AB"), 2);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("AB"), 0);

        // 2. do it again to ensure that the txList is still usable
        txList.beginEvent(false);
        txList.elementInserted(2,"C");
        txList.elementInserted(3, "D");
        assertState(GlazedListsTests.stringToList("ABCD"), 2);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("ABCD"), 0);
    }

    /**
     * If a transaction is "live" it forwards all ListEvents as they are
     * received and thus a ListEvent must be fired on rollback.
     */
    @Test
    public void testRollbackOnLiveTransactionFiresEvent() {
        assertState(GlazedListsTests.stringToList(""), 0);

        // 1. rollback should fire ListEvent
        txList.beginEvent(false);
        txList.elementInserted(0, "A");
        txList.elementInserted(1, "B");
        assertState(GlazedListsTests.stringToList("AB"), 2);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList(""), 1);

        // 2. do it again to ensure that the txList is still usable
        txList.beginEvent(false);
        txList.elementInserted(0,"C");
        txList.elementInserted(1,"D");
        assertState(GlazedListsTests.stringToList("CD"), 2);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList(""), 1);
    }

    @Test
    public void testDoubleBegin() {
        txList.beginEvent();

        try {
            txList.beginEvent();
        } catch (IllegalStateException e) {
            assertEquals("Unable to begin a new transaction before committing or rolling back the previous transaction", e.getMessage());
        }
    }

    @Test
    public void testCommitNothing() {
        try {
            txList.commitEvent();
        } catch (IllegalStateException e) {
            assertEquals("No ListEvent exists to commit", e.getMessage());
        }
    }

    @Test
    public void testRollbackNothing() {
        try {
            txList.rollbackEvent();
        } catch (IllegalStateException e) {
            assertEquals("No ListEvent exists to roll back", e.getMessage());
        }
    }

    @Test
    public void testNoRollbackSupport() {
        BasicEventList<String> source = new BasicEventList<>();
        txList = new TransactionEventAssembler<>(source, false);
        counter = new GlazedListsTests.ListEventCounter<>();
        txList.addListEventListener(evt -> ListEventAssembler.applyListEvent(evt, target));
        txList.addBufferedListEventListener(evt -> counter.listChanged(evt));

        txList.beginEvent();

        try {
            txList.rollbackEvent();
        } catch (IllegalStateException e) {
            assertEquals("This TransactionList does not support rollback", e.getMessage());
        }
    }

    @Test
    public void testRollbackNestedTransactions() {
        txList.beginEvent(false);
        txList.elementInserted(0, "A");
        txList.elementInserted(1, "B");
        assertState(GlazedListsTests.stringToList("AB"), 2);

        // nest a transaction that does delay its events
        txList.beginEvent(true);
        txList.elementInserted(2, "C");
        txList.elementInserted(3, "D");
        assertState(GlazedListsTests.stringToList("ABCD"), 0);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList("AB"), 0);

        // nest a transaction that does not delay its events
        txList.beginEvent(false);
        txList.elementInserted(2, "E");
        txList.elementInserted(3, "F");
        assertState(GlazedListsTests.stringToList("ABEF"), 2);
        txList.rollbackEvent();
        assertState(GlazedListsTests.stringToList("AB"), 1);

        txList.elementInserted(2, "G");
        txList.elementInserted(3, "H");
        assertState(GlazedListsTests.stringToList("ABGH"), 2);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("ABGH"), 0);
    }

    @Test
    public void testCommitNestedTransactions() {
        txList.beginEvent(true);
        txList.elementInserted(0, "A");
        txList.elementInserted(1, "B");
        assertState(GlazedListsTests.stringToList("AB"), 0);

        // nest a transaction that does delay its events
        txList.beginEvent(true);
        txList.elementInserted(2, "C");
        txList.elementInserted(3, "D");
        assertState(GlazedListsTests.stringToList("ABCD"), 0);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("ABCD"), 0);

        // nest a transaction that does not delay its events
        txList.beginEvent(false);
        txList.elementInserted(4, "E");
        txList.elementInserted(5, "F");
        assertState(GlazedListsTests.stringToList("ABCDEF"), 0);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("ABCDEF"), 0);

        txList.elementInserted(6, "G");
        txList.elementInserted(7,"H");
        assertState(GlazedListsTests.stringToList("ABCDEFGH"), 0);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("ABCDEFGH"), 1);
    }

    @Test
    public void testIgnoreChangesOutsideOfTransaction() {
        txList.elementInserted(0,"A");
        assertState(GlazedListsTests.stringToList("A"), 1);

        txList.elementUpdated(0, "A", "B");
        assertState(GlazedListsTests.stringToList("B"), 1);

        txList.beginEvent();
        txList.elementInserted(1, "C");
        assertState(GlazedListsTests.stringToList("BC"), 0);
        txList.commitEvent();
        assertState(GlazedListsTests.stringToList("BC"), 1);

        txList.elementInserted(2,"D");
        assertState(GlazedListsTests.stringToList("BCD"), 1);
    }

    private void assertState(List expected, int numEvents) {
        assertEquals(expected, target);
        assertEquals(numEvents, counter.getCountAndReset());
    }
}
