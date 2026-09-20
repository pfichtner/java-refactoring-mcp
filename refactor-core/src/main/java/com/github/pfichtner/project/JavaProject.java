package com.github.pfichtner.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface JavaProject {
    Path root();
    List<Path> sourceRoots();
    String javaVersion();
    String[] classpath() throws IOException, InterruptedException;
}
