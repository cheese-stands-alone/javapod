package io.rj.rwhite;

public class DownloadMessage {
    private final DownloadState state;
    private final String name;
    private final int total;
    private final int download;

    DownloadMessage(String name, DownloadState state, int total, int download) {
        this.name = name;
        this.state = state;
        this.total = total;
        this.download = download;
    }

    public String getName() {
        return name;
    }

    public DownloadState getState() {
        return state;
    }

    public int getDownload() {
        return download;
    }

    public int getTotal() {
        return total;
    }

    @Override
    public String toString() {
        System.out.println("calling tostring");
        return "Name: " + name + " State: " + state.toString() + "Total: " + total + " Downloaded: " + download;
    }
}
