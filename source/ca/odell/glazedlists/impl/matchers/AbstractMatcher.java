/*
 * Copyright(c) 2002-2004, NEXVU Technologies
 * All rights reserved.
 *
 * Created: Feb 18, 2005 - 6:56:35 AM
 */
package ca.odell.glazedlists.impl.matchers;

import ca.odell.glazedlists.Matcher;
import ca.odell.glazedlists.event.MatcherListener;

import java.util.ArrayList;
import java.util.List;


/**
 * Basic building block for {@link ca.odell.glazedlists.Matcher} implementations that
 * handles the details of dealing with listeners. All <tt>Matcher</tt> implementations are
 * encouraged to extends this class rather than directly implementing <tt>Matcher</tt>.
 * <p/>
 * Extending classes can fire events to listener using "fire" methods: <ul> <li>{@link
 * #fireCleared}</li> <li>{@link #fireChanged}</li> <li>{@link #fireConstrained}</li>
 * <li>{@link #fireRelaxed}</li> </ul>
 *
 * @author <a href="mailto:rob@starlight-systems.com">Rob Eden</a>
 */
public abstract class AbstractMatcher implements Matcher {
    /**
     * Contains MatcherListeners.
     */
    private final List listener_list = new ArrayList(1);  // normally only one listener


    /**
     * {@inheritDoc}
     */
    public void addMatcherListener(MatcherListener listener) {
        synchronized(listener_list) {
            listener_list.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void removeMatcherListener(MatcherListener listener) {
        synchronized(listener_list) {
            listener_list.remove(listener);
        }
    }


    /**
     * Indicates that the filter has been cleared (i.e., all elements should now be
     * visible.
     */
    protected void fireCleared() {
        synchronized(listener_list) {
            for (int i = 0; i < listener_list.size(); i++) {
                ((MatcherListener) listener_list.get(i)).cleared(this);
            }
        }
    }

    /**
     * Indicates that the filter has changed in an inditerminate way.
     */
    protected void fireChanged() {
        synchronized(listener_list) {
            for (int i = 0; i < listener_list.size(); i++) {
                ((MatcherListener) listener_list.get(i)).changed(this);
            }
        }
    }

    /**
     * Indicates that the filter has changed to be more restrictive. This should only be
     * called if all currently filtered items will remain filtered.
     */
    protected void fireConstrained() {
        synchronized(listener_list) {
            for (int i = 0; i < listener_list.size(); i++) {
                ((MatcherListener) listener_list.get(i)).constrained(this);
            }
        }
    }

    /**
     * Indicates that the filter has changed to be less restrictive. This should only be
     * called if all currently unfiltered items will remain unfiltered.
     */
    protected void fireRelaxed() {
        synchronized(listener_list) {
            for (int i = 0; i < listener_list.size(); i++) {
                ((MatcherListener) listener_list.get(i)).relaxed(this);
            }
        }
    }
}
