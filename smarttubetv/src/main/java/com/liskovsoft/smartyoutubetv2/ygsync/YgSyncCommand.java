package com.liskovsoft.smartyoutubetv2.ygsync;

public final class YgSyncCommand {

    public static final String PING = "PING";

    public static final String CONNECT = "CONNECT";
    public static final String DISCONNECT = "DISCONNECT";

    public static final String LOAD_VIDEO = "LOAD_VIDEO";

    public static final String PLAY = "PLAY";
    public static final String PAUSE = "PAUSE";
    public static final String STOP = "STOP";

    public static final String SEEK = "SEEK";

    public static final String SET_VOLUME = "SET_VOLUME";

    public static final String SYNC = "SYNC";

    public static final String GET_STATUS = "GET_STATUS";

    private YgSyncCommand() {
    }
}
