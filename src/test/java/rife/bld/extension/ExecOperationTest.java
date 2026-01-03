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
import rife.bld.extension.tools.SystemUtils;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(LoggingExtension.class)
class ExecOperationTest {
    private static final String BAR = "bar";
    private static final String CAT_COMMAND = "cat";
    private static final String ECHO_COMMAND = "echo";
    private static final String FOO = "foo";

    @SuppressWarnings("LoggerInitializedWithForeignClass")
    private static final Logger LOGGER = Logger.getLogger(ExecOperation.class.getName());
    private static final TestLogHandler TEST_LOG_HANDLER = new TestLogHandler();

    @RegisterExtension
    @SuppressWarnings("unused")
    private static final LoggingExtension LOGGING_EXTENSION = new LoggingExtension(
            LOGGER,
            TEST_LOG_HANDLER,
            Level.ALL
    );

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
        return SystemUtils.isWindows() ? windowsCommand : unixCommand;
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
        void commandTimeout() {
            var sleepCommand = getPlatformSpecificCommand(WINDOWS_SLEEP_COMMAND, UNIX_SLEEP_COMMAND);
            var execOperation = createBasicExecOperation()
                    .timeout(5)
                    .command(sleepCommand);

            assertThat(execOperation.timeout()).as("timeout should be 5 seconds").isEqualTo(5);
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
            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
        }

        @Test
        void commandTimeoutWithoutLogging() {
            LOGGER.setLevel(Level.OFF);
            var sleepCommand = getPlatformSpecificCommand(WINDOWS_SLEEP_COMMAND, UNIX_SLEEP_COMMAND);
            var execOperation = createBasicExecOperation()
                    .timeout(1)
                    .command(sleepCommand);

            assertThatCode(execOperation::execute).as("should fail execution due to timeout")
                    .isInstanceOf(ExitStatusException.class);
            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
        }

        @Test
        void executeWithoutProject() {
            var op = new ExecOperation().command(ECHO_COMMAND, FOO);

            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
        }

        @Test
        void executeWithoutProjectNoLogging() {
            LOGGER.setLevel(Level.OFF);
            var op = new ExecOperation().command(ECHO_COMMAND, FOO);

            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);

            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
        }

        @Test
        void executeWithoutProjectWIthSilent() {
            var op = new ExecOperation()
                    .command(ECHO_COMMAND, FOO)
                    .silent(true);

            assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);

            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
        }

        @Test
        void invalidCommandException() {
            assertThatCode(() ->
                    createBasicExecOperation()
                            .command(FOO)
                            .execute())
                    .message()
                    .startsWith("Cannot run program \"" + FOO + '"');
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
            LOGGER.setLevel(Level.OFF);
            var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
            var execOperation = createBasicExecOperation()
                    .command(catCommand)
                    .failOnExit(true);

            assertThatCode(execOperation::execute).isInstanceOf(ExitStatusException.class);

            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
        }

        @Test
        void failOnExitWithSilent() {
            var catCommand = getPlatformSpecificCommand(WINDOWS_CAT_COMMAND, UNIX_CAT_COMMAND);
            var execOperation = createBasicExecOperation()
                    .command(catCommand)
                    .silent(true)
                    .failOnExit(true);

            assertThatCode(execOperation::execute).isInstanceOf(ExitStatusException.class);
            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("OS Detection Tests")
    class OsDetectionTests {
        @Test
        void verifyIsAix() {
            assertSame(SystemUtils.isAix(), ExecOperation.isAix());
        }

        @Test
        void verifyIsCygwin() {
            assertSame(SystemUtils.isCygwin(), ExecOperation.isCygwin());
        }

        @Test
        void verifyIsFreeBsd() {
            assertSame(SystemUtils.isFreeBsd(), ExecOperation.isFreeBsd());
        }

        @Test
        void verifyIsLinux() {
            assertSame(SystemUtils.isLinux(), ExecOperation.isLinux());
        }

        @Test
        void verifyIsMacOS() {
            assertSame(SystemUtils.isMacOS(), ExecOperation.isMacOS());
        }

        @Test
        void verifyIsMingw() {
            assertSame(SystemUtils.isMingw(), ExecOperation.isMingw());
        }

        @Test
        void verifyIsOpenVms() {
            assertSame(SystemUtils.isOpenVms(), ExecOperation.isOpenVms());
        }

        @Test
        void verifyIsSolaris() {
            assertSame(SystemUtils.isSolaris(), ExecOperation.isSolaris());
        }

        @Test
        void verifyIsWindows() {
            assertSame(SystemUtils.isWindows(), ExecOperation.isWindows());
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
            LOGGER.setLevel(Level.OFF);
            assertThatCode(() ->
                    createBasicExecOperation()
                            .command(ECHO_COMMAND)
                            .workDir(FOO)
                            .execute())
                    .isInstanceOf(ExitStatusException.class);

            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
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

            assertThat(TEST_LOG_HANDLER.isEmpty()).isTrue();
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