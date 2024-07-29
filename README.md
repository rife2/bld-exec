# [b<span style="color:orange">l</span>d](https://rife2.com/bld) Command Line Execution Extension

[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![bld](https://img.shields.io/badge/2.0.1-FA9052?label=bld&labelColor=2392FF)](https://rife2.com/bld)
[![Release](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/releases/com/uwyn/rife2/bld-exec/maven-metadata.xml?color=blue)](https://repo.rife2.com/#/releases/com/uwyn/rife2/bld-exec)
[![Snapshot](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/snapshots/com/uwyn/rife2/bld-exec/maven-metadata.xml?label=snapshot)](https://repo.rife2.com/#/snapshots/com/uwyn/rife2/bld-exec)
[![GitHub CI](https://github.com/rife2/bld-exec/actions/workflows/bld.yml/badge.svg)](https://github.com/rife2/bld-exec/actions/workflows/bld.yml)

To install, please refer to the [extensions documentation](https://github.com/rife2/bld/wiki/Extensions).

To execute a command at the command line, add the following to your build file:

```java
@BuildCommand
public void startServer() throws Exception {
    new ExecOperation()
            .fromProject(this)
            .command("./start.sh", "--port", "8080")
            .execute();
}
```

## Exit Value

Use the `failOnExit` function to specify whether a command non-zero exit value (status) constitutes a failure.

```java
@BuildCommand
public void startServer() throws Exception {
    final List<String> cmds;
    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
        cmds = List.of("cmd", "/c", "stop.bat");
    } else {
        cmds = List.of("./stop.sh");
    }
    new ExecOperation()
            .fromProject(this)
            .command(cmds)
            .failOneExit(false)
            .execute();
}
```

## Work Directory

You can also specify the work directory:

```java
@BuildCommand
public void startServer() throws Exception {
    new ExecOperation()
            .fromProject(this)
            .command("touch", "foo.txt")
            .workDir(System.getProperty("java.io.tmpdir"))
            .execute();
}
```

Please check the [ExecOperation documentation](https://rife2.github.io/bld-exec/rife/bld/extension/ExecOperation.html#method-summary) for all available configuration options.
