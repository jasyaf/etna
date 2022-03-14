package org.etnaframework.jdbc;

/**
 * @author ZengDong
 * @since 2011-12-14 上午10:26:49
 */
public class Process {

    private static final String FORMAT = "%-10s %-10s %-20s %-25s %-15s %-5s %-15s %s\n";

    public static final String HEADER = String.format(FORMAT, "ID", "USER", "HOST", "DB", "COMMNAD", "TIME", "STATE", "INFO");

    private long id;

    private String user = "";

    private String host = "";

    private String db = "";

    private String command = "";

    private long time;

    private String state = "";

    private String info = "";

    public long getId() {
        return id;
    }

    public String getUser() {
        return user;
    }

    public String getHost() {
        return host;
    }

    public String getDb() {
        return db;
    }

    public String getCommand() {
        return command;
    }

    public long getTime() {
        return time;
    }

    public String getState() {
        return state;
    }

    public String getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return String.format(FORMAT, id, user, host, db, command, time, state, info);
    }
}
