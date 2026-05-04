/*
 * Copyright 2023-2026 the original author or authors.
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import rife.bld.BaseProject;
import rife.bld.Project;
import rife.bld.WebProject;
import rife.bld.extension.testing.LoggingExtension;
import rife.bld.extension.testing.TestLogHandler;
import rife.bld.extension.tools.SystemTools;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(LoggingExtension.class)
@SuppressWarnings({"PMD.AvoidDuplicateLiterals"})
class ExecOperationTest {

    private static final String BAR = "bar";
    private static final String CAT_COMMAND = "cat";
    private static final String ECHO_COMMAND = "echo";
    private static final String FOO = "foo";

    private static final List<String> UNIX_CAT_COMMAND = List.of(CAT_COMMAND, FOO);
    private static final List<String> UNIX_ECHO_COMMAND = List.of(ECHO_COMMAND, FOO);
    private static final List<String> UNIX_SLEEP_COMMAND = List.of("yes");

    private static final List<String> WINDOWS_CAT_COMMAND = List.of("cmd", "/c", "type", FOO);
    private static final List<String> WINDOWS_ECHO_COMMAND = List.of("cmd", "/c", ECHO_COMMAND, FOO);
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final List<String> WINDOWS_SLEEP_COMMAND = List.of("cmd", "/c", "ping", "-t", "127.0.0.1");

    @SuppressWarnings("LoggerInitializedWithForeignClass")
    private static final Logger logger = Logger.getLogger(ExecOperation.class.getName());
    private static final TestLogHandler testLogHandler = new TestLogHandler();
    @RegisterExtension
    @SuppressWarnings("unused")
    private static final LoggingExtension loggingExtension = new LoggingExtension(
            logger,
            testLogHandler,
            Level.ALL
    );

    private ExecOperation createBasicExecOperation() {
        return new ExecOperation().fromProject(new BaseProject());
    }

    private List<String> getPlatformSpecificCommand(List<String> windowsCommand, List<String> unixCommand) {
        return SystemTools.isWindows() ? windowsCommand : unixCommand;
    }

    @Nested
    @DisplayName("Command Tests")
    class CommandTests {

        @Test
        void command() {
            var execOperation = new ExecOperation()
                    .fromProject(new WebProject())
                    .command(FOO, BAR);
            assertThat(execOperation.command()).containsExactly(FOO, BAR);
        }

        @Test
        void commandMutableList() {
            var op = createBasicExecOperation().command("git", "status");
            op.command().add("--verbose");
            assertThat(op.command()).containsExactly("git", "status", "--verbose");
        }

        @Test
        void commandTimeout() {
            var sleepCommand = getPlatformSpecificCommand(WINDOWS_SLEEP_COMMAND, UNIX_SLEEP_COMMAND);
            var execOperation = createBasicExecOperation()
                    .timeout(2)
                    .command(sleepCommand);

            assertThat(execOperation.timeout()).as("timeout should be 2 seconds").isEqualTo(2);
            assertThatCode(execOperation::execute).as("should fail execution due to timeout")
                    .isInstanceOf(ExitStatusException.class);
        }

        @Test
        void commandTimeoutWithSilent() {
            var sleepCommand = getPlatformSpecificCommand(WINDOWS_SLEEP_COMMAND, UNIX_SLEEP_COMMAND);
            var execOperation = createBasicExecOperation()
                    .timeout(1)
                    .silent(true)
                    .command(sleepCommand);

            assertThatCode(execOperation::execute).as("should fail execution due to timeout")
                    .isInstanceOf(ExitStatusException.class);
            assertThat(testLogHandler.isEmpty()).isTrue();
        }

        @Test
        void commandTimeoutWithoutLogging() {
            logger.setLevel(Level.OFF);
            var sleepCommand = getPlatformSpecificCommand(WINDOWS_SLEEP_COMMAND, UNIX_SLEEP_COMMAND);
            var execOperation = createBasicExecOperation()
                    .timeout(1)
                    .command(sleepCommand);

            assertThatCode(execOperation::execute).as("should fail execution due to timeout")
                    .isInstanceOf(ExitStatusException.class);
            assertThat(testLogHandler.isEmpty()).isTrue();
        }

        @Test
        void executeWithoutProject() {
            var op = new ExecOperation().command(ECHO_COMMAND, FOO);

            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
        }

        @Test
        void executeWithoutProjectNoLogging() {
            logger.setLevel(Level.OFF);
            var op = new ExecOperation().command(ECHO_COMMAND, FOO);

            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);

            assertThat(testLogHandler.isEmpty()).isTrue();
        }

        @Test
        void executeWithoutProjectWIthSilent() {
            var op = new ExecOperation()
                    .command(ECHO_COMMAND, FOO)
                    .silent(true);

            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);

            assertThat(testLogHandler.isEmpty()).isTrue();
        }

        @Test
        void invalidCommandException() {
            assertThatCode(() ->
                    createBasicExecOperation()
                            .command(FOO)
                            .execute())
                    .isInstanceOf(ExitStatusException.class);
            testLogHandler.printLogMessages();
            assertThat(testLogHandler.containsExactMessage("Failed to execute command.")).isTrue();
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void touchCommand() {
            var tempFile = new File("hello.tmp");
            tempFile.deleteOnExit();

            assertThatCode(() ->
                    new ExecOperation()
                            .fromProject(new Project())
                            .timeout(10)
                            .command("touch", tempFile.getName())
                            .execute())
                    .doesNotThrowAnyException();

            assertThat(tempFile).exists();
        }
    }

    @Nested
    @DisplayName("Environment Tests")
    class EnvironmentTests {

        @Test
        void envFromMap() {
            var op = createBasicExecOperation().env(Map.of("KEY1", "val1", "KEY2", "val2"));
            assertThat(op.env()).containsEntry("KEY1", "val1").containsEntry("KEY2", "val2");
        }

        @Test
        void envMultipleVariables() {
            var op = createBasicExecOperation()
                    .env("KEY1", "val1")
                    .env("KEY2", "val2");
            assertThat(op.env()).containsEntry("KEY1", "val1").containsEntry("KEY2", "val2");
        }

        @Test
        void envMutableMap() {
            var op = createBasicExecOperation().env("KEY1", "val1");
            op.env().put("KEY2", "val2");
            assertThat(op.env()).containsEntry("KEY2", "val2");
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void envPassedToProcess() {
            var op = createBasicExecOperation()
                    .command("sh", "-c", "echo $TEST_VAR")
                    .env("TEST_VAR", "hello_env");

            assertThatCode(op::execute).doesNotThrowAnyException();
            testLogHandler.printLogMessages();
            assertThat(testLogHandler.containsMessage("hello_env")).isTrue();
        }

        @Test
        @EnabledOnOs(OS.WINDOWS)
        void envPassedToProcessWindows() {
            var op = createBasicExecOperation()
                    .command("cmd", "/c", "echo %TEST_VAR%")
                    .env("TEST_VAR", "hello_env");

            assertThatCode(op::execute).doesNotThrowAnyException();
            testLogHandler.printLogMessages();
            assertThat(testLogHandler.containsMessage("hello_env")).isTrue();
        }

        @Test
        void envSingleVariable() {
            var op = createBasicExecOperation().env("TEST_KEY", "test_value");
            assertThat(op.env()).containsEntry("TEST_KEY", "test_value");
        }
    }

    @Nested
    @DisplayName("Exit Tests")
    class ExitTests {

        @Test
        void exitValue() {
            var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
            assertThatCode(() ->
                    createBasicExecOperation()
                            .command(catCommand)
                            .execute())
                    .isInstanceOf(ExitStatusException.class);
        }

        @Test
        void failOnExitConfiguration() {
            var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
            var execOperation = createBasicExecOperation()
                    .command(catCommand)
                    .failOnExit(false);

            assertThat(execOperation.isFailOnExit()).as("fail on exit should be false by default").isFalse();
            assertThatCode(execOperation::execute)
                    .as("should execute without failing")
                    .doesNotThrowAnyException();

            execOperation.failOnExit(true);
            assertThat(execOperation.isFailOnExit()).as("fail on exit should be true").isTrue();
        }

        @Test
        void failOnExitNoLogging() {
            logger.setLevel(Level.OFF);
            var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
            var execOperation = createBasicExecOperation()
                    .command(catCommand)
                    .failOnExit(true);

            assertThatCode(execOperation::execute).isInstanceOf(ExitStatusException.class);

            assertThat(testLogHandler.isEmpty()).isTrue();
        }

        @Test
        void failOnExitWithSilent() {
            var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
            var execOperation = createBasicExecOperation()
                    .command(catCommand)
                    .silent(true)
                    .failOnExit(true);

            assertThatCode(execOperation::execute).isInstanceOf(ExitStatusException.class);
            assertThat(testLogHandler.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Fluent OS Command Tests")
    class FluentOsCommandTests {

        @Test
        void fluentChain() {
            var op = createBasicExecOperation();
            op.onWindows("firstWin").onWindows("secondWin");
            op.onUnix("first").onUnix("second");
            if (ExecOperation.isWindows()) {
                assertThat(op.command()).containsExactlyInAnyOrder("firstWin", "secondWin");
            } else {
                assertThat(op.command()).containsExactlyInAnyOrder("first", "second");
            }
        }

        @Test
        void onLinuxCollectionWithNullThrows() {
            assertThatCode(() -> createBasicExecOperation().onLinux(Arrays.asList("ls", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("command values must not be null or empty");
        }

        @Test
        void onLinuxEmptyElementThrows() {
            assertThatCode(() -> createBasicExecOperation().onLinux("ls", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("command values must not be null or empty");
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        void onLinuxFromCollection() {
            var cmd = List.of("ls", "-la");
            var op = createBasicExecOperation().onLinux(cmd);
            assertThat(op.command()).containsExactlyElementsOf(cmd);
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        void onLinuxNullArgsThrows() {
            assertThatCode(() -> createBasicExecOperation().onLinux((String[]) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        void onLinuxNullCollectionThrows() {
            assertThatCode(() -> createBasicExecOperation().onLinux((Collection<String>) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        void onLinuxSetsCommand() {
            var op = createBasicExecOperation().onLinux("ls", "-la");
            assertThat(op.command()).containsExactly("ls", "-la");
        }

        @Test
        @EnabledOnOs(OS.MAC)
        void onMacOSFromCollection() {
            var cmd = List.of("ls", "-G");
            var op = createBasicExecOperation().onMacOS(cmd);
            assertThat(op.command()).containsExactlyElementsOf(cmd);
        }

        @Test
        @EnabledOnOs(OS.MAC)
        void onMacOSSetsCommand() {
            var op = createBasicExecOperation().onMacOS("ls", "-G");
            assertThat(op.command()).containsExactly("ls", "-G");
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void onUnixFromCollection() {
            var cmd = List.of("echo", "unix");
            var op = createBasicExecOperation()
                    .onWindows(List.of("cmd", "/c", "echo", "win"))
                    .onUnix(cmd);
            assertThat(op.command()).containsExactlyElementsOf(cmd);
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void onUnixSetsCommand() {
            var op = createBasicExecOperation()
                    .onWindows("cmd", "/c", "echo", "win")
                    .onUnix("echo", "unix");
            assertThat(op.command()).containsExactly("echo", "unix");
        }

        @Test
        void onWindowsFromCollection() {
            var cmd = List.of("cmd", "/c", "dir");
            var op = createBasicExecOperation().onWindows(cmd);
            if (ExecOperation.isWindows()) {
                assertThat(op.command()).containsExactlyElementsOf(cmd);
            } else {
                assertThat(op.command()).isEmpty();
            }
        }

        @Test
        @EnabledOnOs(OS.WINDOWS)
        void onWindowsSetsCommand() {
            var op = createBasicExecOperation()
                    .onWindows("cmd", "/c", "echo", "win")
                    .onUnix("echo", "unix");
            assertThat(op.command()).containsExactly("cmd", "/c", "echo", "win");
        }
    }

    @Nested
    @DisplayName("InheritIO Tests")
    class InheritIOTests {

        @Test
        void inheritIOConfiguration() {
            var op = createBasicExecOperation().inheritIO(true);
            assertThat(op.isInheritIO()).isTrue();

            op.inheritIO(false);
            assertThat(op.isInheritIO()).isFalse();
        }

        @Test
        void inheritIOFalseCapturesOutput() {
            var echoCommand = getPlatformSpecificCommand(WINDOWS_ECHO_COMMAND, UNIX_ECHO_COMMAND);
            var op = createBasicExecOperation()
                    .command(echoCommand)
                    .inheritIO(false);

            assertThatCode(op::execute).doesNotThrowAnyException();
            testLogHandler.printLogMessages();
            assertThat(testLogHandler.containsMessage(FOO)).isTrue();
        }
    }

    @Nested
    @DisplayName("OS Detection Tests")
    class OsDetectionTests {

        @Test
        void verifyIsAix() {
            assertSame(SystemTools.isAix(), ExecOperation.isAix());
        }

        @Test
        void verifyIsCygwin() {
            assertSame(SystemTools.isCygwin(), ExecOperation.isCygwin());
        }

        @Test
        void verifyIsFreeBsd() {
            assertSame(SystemTools.isFreeBsd(), ExecOperation.isFreeBsd());
        }

        @Test
        void verifyIsLinux() {
            assertSame(SystemTools.isLinux(), ExecOperation.isLinux());
        }

        @Test
        void verifyIsMacOS() {
            assertSame(SystemTools.isMacOS(), ExecOperation.isMacOS());
        }

        @Test
        void verifyIsMingw() {
            assertSame(SystemTools.isMinGw(), ExecOperation.isMingw());
        }

        @Test
        void verifyIsOpenVms() {
            assertSame(SystemTools.isOpenVms(), ExecOperation.isOpenVms());
        }

        @Test
        void verifyIsSolaris() {
            assertSame(SystemTools.isSolaris(), ExecOperation.isSolaris());
        }

        @Test
        void verifyIsWindows() {
            assertSame(SystemTools.isWindows(), ExecOperation.isWindows());
        }
    }

    @Nested
    @DisplayName("Work Directory Tests")
    class WorkingDirTests {

        private final List<String> echoCommand = getPlatformSpecificCommand(WINDOWS_ECHO_COMMAND, UNIX_ECHO_COMMAND);
        private final ExecOperation op = createBasicExecOperation().command(echoCommand);
        private final File tmpDir = new File(System.getProperty("java.io.tmpdir"));

        @Test
        void invalidWorkDir() {
            assertThatCode(() ->
                    createBasicExecOperation()
                            .command(ECHO_COMMAND)
                            .workDir(FOO)
                            .execute())
                    .isInstanceOf(ExitStatusException.class);
        }

        @Test
        void invalidWorkDirNoLogging() {
            logger.setLevel(Level.OFF);
            assertThatCode(() ->
                    createBasicExecOperation()
                            .command(ECHO_COMMAND)
                            .workDir(FOO)
                            .execute())
                    .isInstanceOf(ExitStatusException.class);

            assertThat(testLogHandler.isEmpty()).isTrue();
        }

        @Test
        void invalidWorkDirWithSilent() {
            assertThatCode(() ->
                    createBasicExecOperation()
                            .command(ECHO_COMMAND)
                            .workDir(FOO)
                            .silent(true)
                            .execute())
                    .isInstanceOf(ExitStatusException.class);

            assertThat(testLogHandler.isEmpty()).isTrue();
        }

        @Test
        void workDirAsFile() {
            op.workDir(tmpDir);
            assertThat(op.workDir()).as("File-based working directory").isEqualTo(tmpDir);
            assertThatCode(op::execute)
                    .as("setting a file-based working directory should execute without failing")
                    .doesNotThrowAnyException();
        }

        @Test
        void workDirAsPath() {
            var op = createBasicExecOperation()
                    .command(echoCommand)
                    .workDir(tmpDir.toPath());
            assertThat(op.workDir()).as("Path-based working directory").isEqualTo(tmpDir);
            assertThatCode(op::execute)
                    .as("setting a path-based working directory should execute without failing")
                    .doesNotThrowAnyException();
        }

        @Test
        void workDirAsString() {
            var buildDir = "build";
            op.workDir(buildDir);
            assertThat(op.workDir()).as("String-based working directory").isEqualTo(new File(buildDir));
            assertThatCode(op::execute)
                    .as("setting a string-based working directory should execute without failing")
                    .doesNotThrowAnyException();
        }
    }
}