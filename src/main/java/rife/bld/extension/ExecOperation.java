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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import rife.bld.BaseProject;
import rife.bld.extension.tools.IOTools;
import rife.bld.extension.tools.ObjectTools;
import rife.bld.extension.tools.SystemTools;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a command on the command line.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
@SuppressWarnings("PMD.DoNotUseThreads")
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "intentional and documented")
public class ExecOperation extends AbstractOperation<ExecOperation> {

    public static final String COMMAND_NOT_VALID = "command values must not be null or empty";
    private static final Logger logger = Logger.getLogger(ExecOperation.class.getName());
    private final List<String> args_ = new ArrayList<>();
    private final Map<String, String> env_ = new HashMap<>();
    private boolean failOnExit_ = true;
    private boolean inheritIO_;
    private int timeout_ = 30;
    private File workDir_;

    @Override
    @SuppressWarnings({"PMD.CloseResource", "PMD.PreserveStackTrace"})
    @SuppressFBWarnings("LEST_LOST_EXCEPTION_STACK_TRACE")
    public void execute() throws Exception {
        validatePreconditions();
        logExecutionStart();

        var pb = createProcessBuilder();
        Process proc = null;
        Thread outputThread = null;

        try {
            proc = pb.start();
            outputThread = startOutputReader(proc);
            waitForCompletion(proc);
            handleExitCode(proc);
        } catch (IOException e) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, "Failed to execute command.", e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } finally {
            cleanup(proc, outputThread);
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
     * @param args one or more arguments, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #command(Collection)
     */
    public ExecOperation command(@NonNull String... args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
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
     * @param args the list of arguments, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #command(String...)
     */
    public final ExecOperation command(@NonNull Collection<String> args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        args_.addAll(args);
        return this;
    }

    /**
     * Adds an environment variable for the command.
     * <p>
     * These variables are merged with the current process environment. Existing variables
     * with the same name are overridden.
     *
     * @param name  the variable name, must not be null
     * @param value the variable value, must not be null
     * @return this operation instance
     * @throws NullPointerException if name or value is null
     * @see #env(Map)
     */
    public ExecOperation env(@NonNull String name, @NonNull String value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        env_.put(name, value);
        return this;
    }

    /**
     * Adds environment variables for the command.
     * <p>
     * These variables are merged with the current process environment. Existing variables
     * with the same name are overridden.
     *
     * @param vars the map of environment variables, must not be null
     * @return this operation instance
     * @throws NullPointerException if vars is null
     * @see #env(String, String)
     */
    public ExecOperation env(@NonNull Map<String, String> vars) {
        Objects.requireNonNull(vars, "vars must not be null");
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
     * The {@link #workDir() work directory} is automatically set to the project's working directory.
     *
     * @param project the project, must not be null
     * @return this operation instance
     * @throws NullPointerException if project is null
     */
    public ExecOperation fromProject(@NonNull BaseProject project) {
        Objects.requireNonNull(project, "project must not be null");
        workDir_ = project.workDirectory();
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
     * Default is {@code FALSE}
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
     * @return {@code true} if I/O is inherited, {@code false} if output is captured
     * @see #inheritIO(boolean)
     */
    public boolean isInheritIO() {
        return inheritIO_;
    }

    /**
     * If the current OS is Linux, configures the command and arguments to be executed.
     * If not Linux, this call is ignored.
     *
     * @param args the command to use on Linux, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onLinux(Collection)
     * @see #isLinux() static method for complex conditional logic
     */
    public ExecOperation onLinux(@NonNull String... args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (SystemTools.isLinux()) {
            return command(args);
        }
        return this;
    }

    /**
     * If the current OS is Linux, configures the command and arguments to be executed.
     * If not Linux, this call is ignored.
     *
     * @param args the command to use on Linux, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onLinux(String...)
     */
    public ExecOperation onLinux(@NonNull Collection<String> args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (SystemTools.isLinux()) {
            return command(args);
        }
        return this;
    }

    /**
     * If the current OS is macOS, configures the command and arguments to be executed.
     * If not macOS, this call is ignored.
     *
     * @param args the command to use on macOS, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onMacOS(Collection)
     * @see #isMacOS() static method for complex conditional logic
     */
    public ExecOperation onMacOS(@NonNull String... args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (SystemTools.isMacOS()) {
            return command(args);
        }
        return this;
    }

    /**
     * If the current OS is macOS, configures the command and arguments to be executed.
     * If not macOS, this call is ignored.
     *
     * @param args the command to use on macOS, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onMacOS(String...)
     */
    public ExecOperation onMacOS(@NonNull Collection<String> args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (SystemTools.isMacOS()) {
            return command(args);
        }
        return this;
    }

    /**
     * If the current OS is Unix-like (Linux, macOS, FreeBSD, Solaris, AIX), configures
     * the command and arguments to be executed. If Windows, this call is ignored.
     * <p>
     * This is a convenience method for commands that work on all Unix-like systems.
     *
     * @param args the command to use on Unix-like systems, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onUnix(Collection)
     */
    public ExecOperation onUnix(@NonNull String... args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (!SystemTools.isWindows()) {
            return command(args);
        }
        return this;
    }

    /**
     * If the current OS is Unix-like, configures the command and arguments to be executed.
     * If Windows, this call is ignored.
     *
     * @param args the command to use on Unix-like systems, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onUnix(String...)
     */
    public ExecOperation onUnix(@NonNull Collection<String> args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (!SystemTools.isWindows()) {
            return command(args);
        }
        return this;
    }

    /**
     * If the current OS is Windows, configures the command and arguments to be executed.
     * If not Windows, this call is ignored.
     * <p>
     * Allows platform-specific commands to be declared fluently:
     * <pre>{@code
     * new ExecOperation()
     *     .fromProject(this)
     *     .isWindows("cmd", "/c", "build.bat")
     *     .isUnix("./build.sh")
     *     .execute();
     * }</pre>
     * <p>
     * Note: If multiple matching calls are made, the last one wins.
     *
     * @param args the command to use on Windows, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onWindows(Collection)
     * @see #onUnix(String...)
     * @see #isWindows() static method for complex conditional logic
     */
    public ExecOperation onWindows
    (@NonNull String... args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (SystemTools.isWindows()) {
            return command(args);
        }
        return this;
    }

    /**
     * If the current OS is Windows, configures the command and arguments to be executed.
     * If not Windows, this call is ignored.
     *
     * @param args the command to use on Windows, must not be null or contain null/empty elements
     * @return this operation instance
     * @throws NullPointerException     if args is null
     * @throws IllegalArgumentException if args contains null or empty elements
     * @see #onWindows(String...)
     */
    public ExecOperation onWindows(@NonNull Collection<String> args) {
        ObjectTools.requireAllNotEmpty(args, COMMAND_NOT_VALID);
        if (SystemTools.isWindows()) {
            return command(args);
        }
        return this;
    }

    /**
     * Configure the command timeout.
     *
     * @param timeout The timeout in seconds, must be greater than 0
     * @return this operation instance
     * @throws IllegalArgumentException if timeout is less than or equal to 0
     */
    public ExecOperation timeout(int timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        timeout_ = timeout;
        return this;
    }

    /**
     * Returns the command timeout.
     *
     * @return the timeout in seconds
     */
    public int timeout() {
        return timeout_;
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory, must not be null
     * @return this operation instance
     * @throws NullPointerException if dir is null
     */
    public ExecOperation workDir(@NonNull File dir) {
        Objects.requireNonNull(dir, "directory must not be null");
        workDir_ = dir;
        return this;
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory, must not be null
     * @return this operation instance
     * @throws NullPointerException if dir is null
     */
    public ExecOperation workDir(@NonNull Path dir) {
        Objects.requireNonNull(dir, "directory must not be null");
        return workDir(dir.toFile());
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory path, must not be null or empty
     * @return this operation instance
     * @throws IllegalArgumentException if dir is null or empty
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public ExecOperation workDir(@NonNull String dir) {
        ObjectTools.requireNotEmpty(dir, "directory must not be null or empty");
        return workDir(new File(dir));
    }

    /**
     * Returns the working directory.
     *
     * @return the directory, or {@code null} if not yet configured via {@link #fromProject(BaseProject)}
     * or {@link #workDir(File)}
     */
    public File workDir() {
        return workDir_;
    }

    private void cleanup(Process proc, Thread outputThread) {
        if (proc != null) {
            if (proc.isAlive()) {
                proc.destroyForcibly();
            }
            closeQuietly(proc.getInputStream());
            closeQuietly(proc.getErrorStream());
            closeQuietly(proc.getOutputStream());
        }
        if (outputThread != null) {
            outputThread.interrupt();
            try {
                outputThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    @SuppressFBWarnings("COMMAND_INJECTION")
    private ProcessBuilder createProcessBuilder() {
        var pb = new ProcessBuilder();
        pb.command(args_);
        pb.directory(workDir_);

        if (!env_.isEmpty()) {
            pb.environment().putAll(env_);
        }

        if (inheritIO_) {
            pb.inheritIO();
        } else {
            pb.redirectErrorStream(true);
        }
        return pb;
    }

    private void handleExitCode(Process proc) throws ExitStatusException {
        int exitCode = proc.exitValue();
        if (exitCode != 0 && failOnExit_) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, "The command exit value/status is: " + exitCode);
            }
            ExitStatusException.throwOnFailure(exitCode);
        }
    }

    private void logExecutionStart() {
        if (logger.isLoggable(Level.INFO) && !silent()) {
            logger.log(Level.INFO, "Working directory: " +  workDir_.getAbsolutePath());
            if (!env_.isEmpty()) {
                logger.log(Level.INFO, "Environment: " + env_);
            }
            logger.info(String.join(" ", args_));
        }
    }

    @SuppressFBWarnings("CRLF_INJECTION_LOGS")
    private void readProcessOutput(Process proc) {
        final var logInfo = logger.isLoggable(Level.INFO) && !silent();
        final var logSevere = logger.isLoggable(Level.SEVERE) && !silent();

        try (var reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logInfo) {
                    logger.info(line);
                }
            }
        } catch (IOException e) {
            if (logSevere && proc.isAlive()) {
                logger.log(Level.SEVERE, "Failed to read command output.", e);
            }
        }
    }

    private Thread startOutputReader(Process proc) {
        if (inheritIO_) {
            return null;
        }

        var thread = new Thread(() -> readProcessOutput(proc));
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void validatePreconditions() throws ExitStatusException {
        final var logSevere = logger.isLoggable(Level.SEVERE) && !silent();

        if (!IOTools.isDirectory(workDir_)) {
            if (logSevere) {
                logger.severe("A valid working directory must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        if (ObjectTools.isEmpty(args_)) {
            if (logSevere) {
                logger.severe("A command must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }

    @SuppressWarnings("PMD.PreserveStackTrace")
    @SuppressFBWarnings("LEST_LOST_EXCEPTION_STACK_TRACE")
    private void waitForCompletion(Process proc) throws ExitStatusException {
        final var logSevere = logger.isLoggable(Level.SEVERE) && !silent();

        try {
            boolean finished = proc.waitFor(timeout_, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                proc.waitFor(5, TimeUnit.SECONDS);
                if (logSevere) {
                    logger.severe("The command timed out after " + timeout_ + " seconds.");
                }
                throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
            }
        } catch (InterruptedException e) {
            if (logSevere) {
                logger.log(Level.SEVERE, "The command was interrupted.", e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }
}