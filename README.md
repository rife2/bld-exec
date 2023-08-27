#  [b<span style="color:orange">l</span>d](https://rife2.com/bldb) Command Line Execution Extension

[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![bld](https://img.shields.io/badge/1.7.2-FA9052?label=bld&labelColor=2392FF)](https://rife2.com/bld)
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
            .command("./start.sh")
            .execute();
}
```

### Failure Modes

Use the `fail` function to specify whether data returned to the standard streams and/or an abnormal exit value
constitute a failure.

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
            .fail(ExecFail.STDERR)
            .execute();
}
```

The following predefined values are available:

| Name              | Failure When                                                     |
|:------------------|:-----------------------------------------------------------------|
| `ExecFail.EXIT`   | Exit value > 0                                                   |
| `ExecFail.NORMAL` | Exit value > 0 or any data to the standard error stream (stderr) |
| `ExecFail.OUTPUT` | Any data to the standard output stream (stdout) or stderr.       |
| `ExecFail.STDERR` | Any data to stderr.                                              |
| `ExecFail.STDOUT` | Any data to stdout.                                              |
| `ExecFail.ALL`    | Any of the conditions above.                                     |
| `ExecFail.NONE`   | Never fails.                                                     |

`ExecFail.NORMAL` is the default value.

## Working Directory

You can also specify the working directory:

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





