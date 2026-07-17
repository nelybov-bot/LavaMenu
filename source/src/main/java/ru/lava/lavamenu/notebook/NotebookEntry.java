package ru.lava.lavamenu.notebook;

/** Одна запись тетрадки. */
public final class NotebookEntry {
    public String nick = "";
    public String reason = "";
    public long addedAt;
    public String addedBy = "";

    public NotebookEntry() {}

    public NotebookEntry(String nick, String reason, long addedAt, String addedBy) {
        this.nick = nick == null ? "" : nick.trim();
        this.reason = reason == null ? "" : reason.trim();
        this.addedAt = addedAt;
        this.addedBy = addedBy == null ? "" : addedBy.trim();
    }
}
