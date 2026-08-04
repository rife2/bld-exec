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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import rife.bld.BaseProject;
import rife.bld.extension.tools.*;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a command on the command line.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
@NullMarked
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "intentional and documented")
public class ExecOperation extends AbstractOperation<ExecOperation> {

    private static final Logger logger = Logger.getLogger(ExecOperation.class.getName());
    private static final Consumer<String> DEFAULT_OUTPUT_CONSUMER = logger::info;
    private final List<String> args_ = new ArrayList<>();
    private final Map<String, String> env_ = new HashMap<>();
    private boolean failOnExit_ = true;
    private boolean inheritIO_ = true;
    private Consumer<String> outputConsumer_ = DEFAULT_OUTPUT_CONSUMER;
    private long timeout_ = ProcessExecutor.DEFAULT_TIMEOUT_SECONDS;
    private @Nullable File workDir_;

    /**
     * Performs the operation.
     *
     * @throws IllegalArgumentException if {@link #workDir() workDir} or a {@code command} are not specified,
     *                                  or a {@link #outputConsumer(Consumer) custom output consumer} is used with
     *                                  {@link #isInheritIO() inerhitIO(true)}
     * @throws Exception                when an exception occurs during the execution
     */
    @Override
    @SuppressWarnings({"PMD.PreserveStackTrace"})
    @SuppressFBWarnings("LEST_LOST_EXCEPTION_STACK_TRACE")
    public void execute() throws Exception {
        var workDir = ObjectTools.requireNonNull(workDir_, "workDir");

        validatePreconditions();

        logExecutionStart(workDir_);

        try {
            var executor = new ProcessExecutor()
                    .command(args_)
                    .workDir(workDir)
                    .timeout(timeout_)
                    .inheritIO(inheritIO_);

            if (!env_.isEmpty()) {
                executor.env(env_);
            }

            if (!inheritIO_) {
                executor.outputConsumer(outputConsumer_);
            }

            var result = executor.execute();
            handleExitCode(result.exitCode());

            if (result.timedOut() && logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("The command timed out after " + timeout_ + " seconds.");
                throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
            }
        } catch (IOException e) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, "Failed to execute command.", e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }

    /**
     * Determines if the current operating system is AIX.
     *
     * @return {@code true} if the operating system is identified as AIX, {@code false} otherwise
     * @see SystemTools#isAix()
     */
    public static boolean isAix() {
        return SystemTools.isAix();
    }

    /**
     * Determines if the current operating system is Cygwin.
     *
     * @return {@code true} if the operating system is identified as Cygwin, {@code false} otherwise
     * @see SystemTools#isCygwin()
     */
    public static boolean isCygwin() {
        return SystemTools.isCygwin();
    }

    /**
     * Determines if the current operating system is FreeBSD.
     *
     * @return {@code true} if the operating system is FreeBSD, {@code false} otherwise
     * @see SystemTools#isFreeBsd()
     */
    public static boolean isFreeBsd() {
        return SystemTools.isFreeBsd();
    }

    /**
     * Determines if the operating system is Linux.
     *
     * @return {@code true} if the operating system is Linux, {@code false} otherwise
     * @see SystemTools#isLinux()
     */
    public static boolean isLinux() {
        return SystemTools.isLinux();
    }

    /**
     * Determines if the current operating system is macOS.
     *
     * @return {@code true} if the OS is macOS, {@code false} otherwise
     * @see SystemTools#isMacOS()
     */
    public static boolean isMacOS() {
        return SystemTools.isMacOS();
    }

    /**
     * Determines if the current operating system is MinGW.
     *
     * @return {@code true} if the operating system is identified as MinGW, {@code false} otherwise
     * @see SystemTools#isMinGw()
     */
    public static boolean isMingw() {
        return SystemTools.isMinGw();
    }

    /**
     * Determines if the current operating system is OpenVMS.
     *
     * @return {@code true} if the operating system is OpenVMS, {@code false} otherwise
     * @see SystemTools#isOpenVms()
     */
    public static boolean isOpenVms() {
        return SystemTools.isOpenVms();
    }

    /**
     * Determines if the current operating system is Solaris.
     *
     * @return {@code true} if the operating system is Solaris, {@code false} otherwise
     * @see SystemTools#isSolaris()
     */
    public static boolean isSolaris() {
        return SystemTools.isSolaris();
    }

    /**
     * Determines if the current operating system is Windows.
     *
     * @return {@code true} if the operating system is Windows, {@code false} otherwise
     * @see SystemTools#isWindows()
     */
    public static boolean isWindows() {
        return SystemTools.isWindows();
    }

    /**
     * Configures the command and arguments to be executed.
     * <p>
     * For example:
     * <ul>
     *     <li>{@code command("cmd", "/c", "stop.bat")}</li>
     *     <li>{@code command("./stop.sh"}</li>
     * </ul>
     *
     * @param args one or more arguments, must not be {@code null}, empty, or contain {@code null} elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty
     * @see #command(Collection)
     */
    public ExecOperation command(String... args) {
        ObjectTools.requireNonNull(args, "command");
        if (args.length == 0) {
            throw new IllegalArgumentException("command must not be empty");
        }
        args_.addAll(List.of(args));
        return this;
    }

    /**
     * Returns the command and arguments to be executed.
     * <p>
     * The returned list is mutable and can be modified directly before calling {@link #execute()}.
     * This allows callers to append flags or manipulate arguments conditionally:
     * <pre>{@code
     * var op = new ExecOperation().command("git", "status");
     * if (verbose) {
     *     op.command().add("--verbose");
     * }
     * op.execute();
     * }</pre>
     *
     * @return the mutable command and arguments list, never null
     */
    public List<String> command() {
        return args_;
    }

    /**
     * Configures the command and arguments to be executed.
     *
     * @param args the list of arguments, must not be {@code null}, empty, or contain {@code null} elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty
     * @see #command(String...)
     */
    public final ExecOperation command(Collection<String> args) {
        ObjectTools.requireNonNull(args, "command");
        if (args.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        args_.addAll(args);
        return this;
    }

    /**
     * Adds an environment variable for the command.
     * <p>
     * These variables are merged with the current process environment. Existing variables
     * with the same name are overridden.
     *
     * @param name  the variable name, must not be {@code null}
     * @param value the variable value, must not be {@code null}
     * @return this operation instance
     * @throws NullPointerException     if name or value is {@code null}
     * @throws IllegalArgumentException if name is blank
     * @see #env(Map)
     */
    public ExecOperation env(String name, String value) {
        TextTools.requireNotBlank(name, "env name");
        ObjectTools.requireNonNull(value, "env value");
        env_.put(name, value);
        return this;
    }

    /**
     * Adds environment variables for the command.
     * <p>
     * These variables are merged with the current process environment. Existing variables
     * with the same name are overridden.
     *
     * @param vars the map of environment variables, must not be {@code null}
     * @return this operation instance
     * @throws NullPointerException if {@code vars} is {@code null}
     * @see #env(String, String)
     */
    public ExecOperation env(Map<String, String> vars) {
        ObjectTools.requireNonNull(vars, "env");
        env_.putAll(vars);
        return this;
    }

    /**
     * Returns the environment variables to be set for the command.
     * <p>
     * The returned map is mutable and can be modified directly before calling {@link #execute()}.
     *
     * @return the mutable environment variables map, never null
     */
    public Map<String, String> env() {
        return env_;
    }

    /**
     * Configures whether the operation should fail if the command exit value/status is not 0.
     * <p>
     * Default is {@code TRUE}
     *
     * @param failOnExit The fail on exit toggle
     * @return this operation instance.
     */
    public ExecOperation failOnExit(boolean failOnExit) {
        failOnExit_ = failOnExit;
        return this;
    }

    /**
     * Configures an Exec operation from a {@link BaseProject}.
     * <p>
     * The {@link #workDir() work directory} is automatically set to the project's working directory,
     * if not already set.
     *
     * @param project the project, must not be {@code null}
     * @return this operation instance
     * @throws NullPointerException if {@code project} is {@code null}
     */
    public ExecOperation fromProject(BaseProject project) {
        ObjectTools.requireNonNull(project, "project");
        if (workDir_ == null) {
            workDir_ = project.workDirectory();
        }
        return this;
    }

    /**
     * Configures whether the child process should inherit the I/O streams of the current JVM.
     * <p>
     * When {@code true}, the child process uses the same stdin, stdout, and stderr as the current
     * Java process. This enables interactive commands, preserves ANSI colors, and allows progress
     * bars to display correctly. Output is not captured by the logger and cannot be asserted in tests.
     * <p>
     * When {@code false}, stdout and stderr are merged and captured through the logger. This makes
     * output testable and keeps it in the build log, but breaks interactive prompts and ANSI formatting.
     * <p>
     * Default is {@code TRUE}
     *
     * @param inheritIO {@code true} to inherit I/O, {@code false} to capture output
     * @return this operation instance
     */
    public ExecOperation inheritIO(boolean inheritIO) {
        inheritIO_ = inheritIO;
        return this;
    }

    /**
     * Returns whether the operation should fail if the command exit value/status is not 0.
     *
     * @return {@code true} or {@code false}
     */
    public boolean isFailOnExit() {
        return failOnExit_;
    }

    /**
     * Returns whether the child process inherits the I/O streams of the current JVM.
     *
     * @return {@code true} if I/O is inherited (default), {@code false} if output is captured
     * @see #inheritIO(boolean)
     */
    public boolean isInheritIO() {
        return inheritIO_;
    }

    /**
     * If the current OS is Linux, configures the command and arguments to be executed.
     * <p>
     * If not Linux, this call is ignored.
     *
     * @param args the command to use on Linux, must not be {@code null}, empty, or contain {@code null} elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     * @see #onLinux(Collection)
     * @see #isLinux() static method for complex conditional logic
     */
    public ExecOperation onLinux(String... args) {
        TextTools.requireNotEmpty("onLinux", args);
        if (SystemTools.isLinux()) {
            args_.addAll(List.of(args));
        }
        return this;
    }

    /**
     * If the current OS is Linux, configures the command and arguments to be executed.
     * <p>
     * If not Linux, this call is ignored.
     *
     * @param args the command to use on Linux, must not be {@code null}, empty, or contain {@code null} elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     * @see #onLinux(String...)
     */
    public ExecOperation onLinux(Collection<String> args) {
        TextTools.requireNotEmpty(args, "onLinux");
        if (SystemTools.isLinux()) {
            args_.addAll(args);
        }
        return this;
    }

    /**
     * If the current OS is macOS, configures the command and arguments to be executed.
     * <p>
     * If not macOS, this call is ignored.
     *
     * @param args the command to use on macOS, must not be {@code null}, empty, or contain {@code null} elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     * @see #onMacOS(Collection)
     * @see #isMacOS() static method for complex conditional logic
     */
    public ExecOperation onMacOS(String... args) {
        TextTools.requireNotEmpty("onMacOS", args);
        if (SystemTools.isMacOS()) {
            args_.addAll(List.of(args));
        }
        return this;
    }

    /**
     * If the current OS is macOS, configures the command and arguments to be executed.
     * <p>
     * If not macOS, this call is ignored.
     *
     * @param args the command to use on macOS, must not be {@code null}, empty, or contain {@code null} elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     * @see #onMacOS(String...)
     */
    public ExecOperation onMacOS(Collection<String> args) {
        TextTools.requireNotEmpty(args, "onMacOS");
        if (SystemTools.isMacOS()) {
            args_.addAll(args);
        }
        return this;
    }

    /**
     * If the current OS is Unix-like (Linux, macOS, FreeBSD, Solaris, AIX), configures
     * the command and arguments to be executed.
     * <p>
     * If Windows, this call is ignored.
     * <p>
     * This is a convenience method for commands that work on all Unix-like systems.
     *
     * @param args the command to use on Unix-like systems, must not be {@code null}, empty, or contain null elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     * @see #onUnix(Collection)
     */
    public ExecOperation onUnix(String... args) {
        TextTools.requireNotEmpty("onUnix", args);
        if (!SystemTools.isWindows()) {
            args_.addAll(List.of(args));
        }
        return this;
    }

    /**
     * If the current OS is Unix-like, configures the command and arguments to be executed.
     * <p>
     * If Windows, this call is ignored.
     *
     * @param args the command to use on Unix-like systems, must not be {@code null}, empty, or contain null elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     * @see #onUnix(String...)
     */
    public ExecOperation onUnix(Collection<String> args) {
        TextTools.requireNotEmpty(args, "onUnix");
        if (!SystemTools.isWindows()) {
            args_.addAll(args);
        }
        return this;
    }

    /**
     * If the current OS is Windows, configures the command and arguments to be executed.
     * <p>
     * If not Windows, this call is ignored.
     * <p>
     * Allows platform-specific commands to be declared fluently:
     * <pre>{@code
     * new ExecOperation()
     *     .fromProject(this)
     *     .onWindows("cmd", "/c", "build.bat")
     *     .onUnix("./build.sh")
     *     .execute();
     * }</pre>
     * <p>
     * Note: If multiple matching calls are made, the last one wins.
     *
     * @param args the command to use on Windows, must not be {@code null}, empty, or contain null elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     * @see #onWindows(Collection)
     * @see #onUnix(String...)
     * @see #isWindows() static method for complex conditional logic
     */
    public ExecOperation onWindows(String... args) {
        TextTools.requireNotEmpty("onWindows", args);
        if (SystemTools.isWindows()) {
            args_.addAll(List.of(args));
        }
        return this;
    }

    /**
     * If the current OS is Windows, configures the command and arguments to be executed.
     * <p>
     * If not Windows, this call is ignored.
     *
     * @param args the command to use on Windows, must not be {@code null}, empty, or contain {@code null} elements
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty
     * @see #onWindows(String...)
     */
    public ExecOperation onWindows(Collection<String> args) {
        TextTools.requireNotEmpty(args, "onWindows");
        if (SystemTools.isWindows()) {
            args_.addAll(args);
        }
        return this;
    }

    /**
     * Sets a consumer to receive output lines when not inheriting I/O.
     * <p>
     * Only called when {@link #isInheritIO()} is {@code false}
     * <p>
     * Default logs with {@link Level#INFO}
     *
     * @param outputConsumer the output consumer, must not be {@code null}
     * @return this operation instance
     * @throws NullPointerException if {@code outputConsumer} is {@code null}
     */
    public ExecOperation outputConsumer(Consumer<String> outputConsumer) {
        outputConsumer_ = ObjectTools.requireNonNull(outputConsumer, "outputConsumer");
        return this;
    }

    /**
     * Configure the command timeout.
     *
     * @param timeout The timeout in seconds, use a negative number for no timeout
     * @return this operation instance
     * @throws IllegalArgumentException if timeout is 0
     */
    public ExecOperation timeout(long timeout) {
        if (timeout == 0) {
            throw new IllegalArgumentException("timeout should not be 0; use a negative number for no timeout");
        }
        timeout_ = timeout;
        return this;
    }

    /**
     * Returns the command timeout.
     *
     * @return the timeout in seconds
     */
    public long timeout() {
        return timeout_;
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory, must not be {@code null}
     * @return this operation instance
     * @throws NullPointerException if {@code dir} is {@code null}
     */
    public ExecOperation workDir(File dir) {
        workDir_ = ObjectTools.requireNonNull(dir, "workDir");
        return this;
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory, must not be {@code null}
     * @return this operation instance
     * @throws NullPointerException if {@code dir} is {@code null}
     */
    public ExecOperation workDir(Path dir) {
        ObjectTools.requireNonNull(dir, "workDir");
        return workDir(dir.toFile());
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory path, must not be {@code null} or empty
     * @return this operation instance
     * @throws IllegalArgumentException if {@code dir} is blank
     * @throws NullPointerException     is {@code dir} is {@code null}
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public ExecOperation workDir(String dir) {
        TextTools.requireNotBlank(dir, "workDir");
        return workDir(new File(dir));
    }

    /**
     * Returns the working directory.
     *
     * @return the directory, or {@code null} if not yet configured via {@link #fromProject(BaseProject)}
     * or {@link #workDir(File)}
     */
    @Nullable
    public File workDir() {
        return workDir_;
    }

    private void handleExitCode(int exitCode) throws ExitStatusException {
        if (exitCode != 0 && failOnExit_) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, "The command exit value/status is: " + exitCode);
            }
            ExitStatusException.throwOnFailure(exitCode);
        }
    }

    private void logExecutionStart(File workDir) {
        if (logger.isLoggable(Level.INFO) && !silent()) {
            logger.log(Level.INFO, "Working directory: " + workDir.getAbsolutePath());
            if (!env_.isEmpty()) {
                logger.log(Level.INFO, "Environment: " + env_);
            }
            logger.info(String.join(" ", args_));
        }
    }

    private void validatePreconditions() {
        if (!IOTools.isDirectory(workDir_)) {
            throw new IllegalArgumentException("A valid working directory must be specified");
        }
        if (ObjectTools.isEmpty(args_)) {
            throw new IllegalArgumentException("A command must be specified");
        }
        if (inheritIO_ && outputConsumer_ != DEFAULT_OUTPUT_CONSUMER) {
            throw new IllegalArgumentException("Cannot use custom outputConsumer with inheritIO(true)");
        }
    }
}
