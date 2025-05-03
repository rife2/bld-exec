/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rife.bld.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import rife.bld.BaseProject;
import rife.bld.Project;
import rife.bld.WebProject;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExecOperationTest {
    private static final String BAR = "bar";
    private static final String CAT_COMMAND = "cat";
    private static final String ECHO_COMMAND = "echo";
    private static final String FOO = "foo";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
    // Unix-specific commands
    private static final List<String> UNIX_CAT_COMMAND = List.of(CAT_COMMAND, FOO);
    private static final List<String> UNIX_ECHO_COMMAND = List.of(ECHO_COMMAND, FOO);
    private static final List<String> UNIX_SLEEP_COMMAND = List.of("sleep", "10");
    // Windows-specific commands
    private static final List<String> WINDOWS_CAT_COMMAND = List.of("cmd", "/c", "type", FOO);
    private static final List<String> WINDOWS_ECHO_COMMAND = List.of("cmd", "/c", ECHO_COMMAND, FOO);
    private static final List<String> WINDOWS_SLEEP_COMMAND = List.of("cmd", "/c", "timeout", "/t", "10");

    private ExecOperation createBasicExecOperation() {
        return new ExecOperation().fromProject(new BaseProject());
    }

    private List<String> getPlatformSpecificCommand(List<String> windowsCommand, List<String> unixCommand) {
        return IS_WINDOWS ? windowsCommand : unixCommand;
    }

    @Test
    void testCommand() {
        var execOperation = new ExecOperation()
                .fromProject(new WebProject())
                .command(FOO, BAR);
        assertThat(execOperation.command()).containsExactly(FOO, BAR);
    }

    @Test
    void testCommandTimeout() {
        var sleepCommand = getPlatformSpecificCommand(WINDOWS_SLEEP_COMMAND, UNIX_SLEEP_COMMAND);
        var execOperation = createBasicExecOperation()
                .timeout(5)
                .command(sleepCommand);

        assertThat(execOperation.timeout()).as("timeout should be 5 seconds").isEqualTo(5);
        assertThatCode(execOperation::execute).as("should fail execution due to timeout")
                .isInstanceOf(ExitStatusException.class);
    }

    @Test
    void testExitValue() {
        var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
        assertThatCode(() ->
                createBasicExecOperation()
                        .command(catCommand)
                        .execute())
                .isInstanceOf(ExitStatusException.class);
    }

    @Test
    void testFailOnExitConfiguration() {
        var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
        var execOperation = createBasicExecOperation()
                .command(catCommand)
                .failOnExit(false);

        assertThat(execOperation.isFailOnExit()).as("fail on exit should be false by default").isFalse();
        assertThatCode(execOperation::execute).as("should execute without failing").doesNotThrowAnyException();

        execOperation.failOnExit(true);
        assertThat(execOperation.isFailOnExit()).as("fail on exit should be true").isTrue();
    }

    @Test
    void testInvalidCommandException() {
        assertThatCode(() ->
                createBasicExecOperation()
                        .command(FOO)
                        .execute())
                .message()
                .startsWith("Cannot run program \"" + FOO + '"');
    }

    @Test
    void testInvalidWorkDirectory() {
        assertThatCode(() ->
                createBasicExecOperation()
                        .command(ECHO_COMMAND)
                        .workDir(FOO)
                        .execute())
                .isInstanceOf(ExitStatusException.class);
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testTouchCommand() throws Exception {
        var tempFile = new File("hello.tmp");
        tempFile.deleteOnExit();

        new ExecOperation()
                .fromProject(new Project())
                .timeout(10)
                .command("touch", tempFile.getName())
                .execute();

        assertThat(tempFile).exists();
    }

    @Test
    void testWorkDirectoryConfiguration() {
        var echoCommand = getPlatformSpecificCommand(WINDOWS_ECHO_COMMAND, UNIX_ECHO_COMMAND);
        var tempDirectory = new File(System.getProperty("java.io.tmpdir"));

        var execOperation = createBasicExecOperation()
                .command(echoCommand)
                .workDir(tempDirectory);

        assertThat(execOperation.workDir()).as("File-based working directory").isEqualTo(tempDirectory);
        assertThatCode(execOperation::execute)
                .as("setting a file-based working directory should execute without failing")
                .doesNotThrowAnyException();

        var buildDir = "build";
        execOperation = execOperation.workDir(buildDir);
        assertThat(execOperation.workDir()).as("String-based working directory").isEqualTo(new File(buildDir));
        assertThatCode(execOperation::execute)
                .as("setting a string-based working directory should execute without failing")
                .doesNotThrowAnyException();

        execOperation = execOperation.workDir(tempDirectory.toPath());
        assertThat(execOperation.workDir()).as("Path-based working directory").isEqualTo(tempDirectory);
        assertThatCode(execOperation::execute)
                .as("setting a path-based working directory should execute without failing")
                .doesNotThrowAnyException();
    }
}