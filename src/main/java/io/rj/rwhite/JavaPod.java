package io.rj.rwhite;


import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class JavaPod {

    private static final ExecutorService pool = Executors.newFixedThreadPool(10);
    private static final DownloadObserver observer = new DownloadObserver();

    public static File getInstallDir() {
        String podDir = System.getProperty("podDir");
        File pod;
        if (podDir != null) pod = new File(podDir);
        else pod = new File(System.getenv("LOCALAPPDATA"), "JavaPod");
        pod.mkdirs();
        return pod;
    }

    public static void main(String[] args) throws Exception {

        Properties props = new Properties();
        props.load(JavaPod.class.getClassLoader().getResourceAsStream("javapod.properties"));

        String[] repositories; // Repositories to look in
        try {
            repositories = props.getProperty("repositories").split(",");
        } catch (NullPointerException e) {
            throw new Exception("repositories not set!", e);
        } catch (Exception e) {
            throw new Exception("Error parsing repositories", e);
        }

        String[] dependencies; // Dependencies to download
        try {
            dependencies = props.getProperty("dependencies").split(",");
        } catch (NullPointerException e) {
            throw new Exception("dependencies not set!", e);
        } catch (Exception e) {
            throw new Exception("Error parsing dependencies", e);
        }

        String[] pods = new String[0]; // list of Pods
        String jarName = props.getProperty("jarname");
        if (jarName == null) throw new Exception("jarname not set!");
        String appName = props.getProperty("appname");
        if (appName == null) throw new Exception("appname not set!");
        if (props.getProperty("pods") != null) {
            try {
                pods = props.getProperty("pods").split(",");
            } catch (Exception e) {
                throw new Exception("Error parsing pods", e);
            }
        }

        File installation = getInstallDir();
        File cache = new File(installation, "cache"); // base location of the downloaded jars
        cache.mkdirs();
        List<String> classpathList = new ArrayList<>(); // locations of jars to build the classpath
        List<Future> futures = new ArrayList<>(); // downloads to wait on

        // Create pods and give them the download observer
        for (String podClass : pods) {
            Thread thread = new Thread(() -> {
                try {
                    Pod pod = (Pod) Class.forName(podClass).getConstructor().newInstance();
                    pod.initPod(observer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        for (String dep : dependencies) {
            String[] dependencyParts = dep.split(":");
            if (dependencyParts.length != 3) throw new Exception("Invalid dependency " + dep);
            String namespace = dependencyParts[0];
            String pkgname = dependencyParts[1];
            String version = dependencyParts[2];
            String path = namespace.replaceAll("\\.", "/") + "/" + pkgname;
            String name = pkgname + "-" + version;
            File jar = new File(cache, path + "/" + name + ".jar");
            jar.getParentFile().mkdirs();

            // If the jar doesn't exist download it.
            if (!jar.exists()) {
                futures.add(pool.submit(() -> {
                    try {
                        downloadJar(repositories, path + "/" + version + "/" + jar.getName(), jar, name);
                    } catch (Exception e) {
                        e.printStackTrace();
                        observer.sendMessage(new DownloadMessage(name, DownloadState.FAILED, 0, 0));
                    }
                }));
            }

            // Add jar path so we can build the classpath later
            classpathList.add(jar.getCanonicalPath());
        }

        // Wait for all downloads to start
        for (Future future : futures) {
            future.get();
        }

        // Copy the runnable jar out of this jar
        File mainJar = new File(installation, "apps/" + appName + '/' + jarName);
        if (!mainJar.exists()) {
            mainJar.getParentFile().mkdirs();
            InputStream link = JavaPod.class.getClassLoader().getResourceAsStream(jarName);
            Files.copy(link, mainJar.getAbsoluteFile().toPath());
        }


        // Build the classpath
        StringBuilder classpath = new StringBuilder();
        for (int i = 0; i < classpathList.size(); i++) {
            classpath.append(classpathList.get(i));
            if ((classpathList.size() - 1) != i) {
                classpath.append(':');
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                new File(System.getProperty("java.home") + "/" + "bin" + "/" + "javaw").getAbsolutePath()
        ).inheritIO();
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        for (String arg : runtimeMxBean.getInputArguments()) {
            processBuilder.command().add(arg);
        }
        processBuilder.command().add("-cp");
        processBuilder.command().add(classpath.toString());
        processBuilder.command().add("-jar");
        processBuilder.command().add(mainJar.getAbsolutePath());
        processBuilder.start();
    }

    private static void downloadJar(String[] repositories, String remotePath, File localFile, String name) throws Exception {
        for (String repo : repositories) {
            BufferedInputStream in = null;
            FileOutputStream out = null;
            try {
                URL url = new URL(repo + remotePath);
                URLConnection conn = url.openConnection();
                int size = conn.getContentLength();

                observer.sendMessage(new DownloadMessage(name, DownloadState.STARTED, size, 0));

                in = new BufferedInputStream(url.openStream());
                out = new FileOutputStream(localFile);
                byte data[] = new byte[1024];
                int count;

                while ((count = in.read(data, 0, 1024)) != -1) {
                    out.write(data, 0, count);
                    observer.sendMessage(new DownloadMessage(name, DownloadState.DOWNLOADING, size, count));
                }
                observer.sendMessage(new DownloadMessage(name, DownloadState.DONE, size, count));
                return;
            } catch (IOException e2) {
                e2.printStackTrace();
            } finally {
                if (in != null)
                    try {
                        in.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                if (out != null)
                    try {
                        out.close();
                    } catch (IOException e4) {
                        e4.printStackTrace();
                    }
            }
        }
        throw new Exception("Unable to download file " + localFile.getPath() + " from any repo");
    }
}
