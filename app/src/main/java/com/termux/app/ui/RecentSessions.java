package com.termux.app.ui;

import com.termux.terminal.TerminalSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers when each terminal session was last shown, for the "Recent Sessions" list. */
public final class RecentSessions {

    private static final Map<String, Long> LAST_ACTIVE = new ConcurrentHashMap<>();

    private RecentSessions() { }

    public static void touch(TerminalSession session) {
        if (session != null) LAST_ACTIVE.put(session.mHandle, System.currentTimeMillis());
    }

    public static long lastActive(TerminalSession session) {
        Long value = session == null ? null : LAST_ACTIVE.get(session.mHandle);
        return value == null ? 0L : value;
    }
}
