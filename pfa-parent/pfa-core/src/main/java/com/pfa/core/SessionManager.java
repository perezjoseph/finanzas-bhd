package com.pfa.core;

import java.util.List;

/**
 * Manages saving, loading, listing, and deleting analysis sessions.
 */
public interface SessionManager {

    /**
     * Saves a session snapshot under the given name. Returns a handle to the saved session.
     */
    SessionHandle save(String name, SessionSnapshot snapshot);

    /**
     * Loads the session snapshot identified by the given handle.
     */
    SessionSnapshot load(SessionHandle handle);

    /**
     * Lists all available saved sessions.
     */
    List<SessionHandle> list();

    /**
     * Deletes the session identified by the given handle.
     */
    void delete(SessionHandle handle);
}
