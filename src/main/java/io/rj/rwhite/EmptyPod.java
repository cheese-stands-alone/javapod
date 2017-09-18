package io.rj.rwhite;

public class EmptyPod implements Pod {
    @Override
    public void initPod(DownloadObserver observer) {
        observer.register(System.out::println);
    }
}
