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
import rife.bld.BaseProject;
import rife.bld.extension.tools.CollectionTools;
import rife.bld.extension.tools.IOTools;
import rife.bld.extension.tools.ObjectTools;
import rife.bld.extension.tools.SystemTools;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a command on the command line.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
public class ExecOperation extends AbstractOperation<ExecOperation> {

    private static final Logger LOGGER = Logger.getLogger(ExecOperation.class.getName());
    private final List<String> args_ = new ArrayList<>();
    private boolean failOnExit_ = true;
    private int timeout_ = 30;
    private File workDir_;

    /**
     * Executes the command.
     */
    @Override
    @SuppressWarnings("PMD.PreserveStackTrace")
    @SuppressFBWarnings({"COMMAND_INJECTION", "LEST_LOST_EXCEPTION_STACK_TRACE"})
    public void execute() throws Exception {
        final var logInfo = LOGGER.isLoggable(Level.INFO) && !silent();
        final var logSevere = LOGGER.isLoggable(Level.SEVERE) && !silent();

        if (!IOTools.isDirectory(workDir_)) {
            if (logSevere) {
                LOGGER.severe("A valid working directory must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } else if (ObjectTools.isEmpty(args_)) {
            if (logSevere) {
                LOGGER.severe("A command must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } else {
            if (logInfo) {
                LOGGER.log(Level.INFO, "Working directory: {0}", workDir_.getAbsolutePath());
            }

            var pb = new ProcessBuilder();
            pb.inheritIO();
            pb.command(args_);
            pb.directory(workDir_);

            if (logInfo) {
                LOGGER.info(String.join(" ", args_));
            }

            @SuppressWarnings("PMD.CloseResource")
            Process proc;
            try {
                proc = pb.start();
            } catch (IOException e) {
                if (logSevere) {
                    LOGGER.log(Level.SEVERE, "Failed to start command.", e);
                }
                throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
            }

            boolean err;
            try {
                err = proc.waitFor(timeout_, TimeUnit.SECONDS);

                if (!err) {
                    if (logSevere) {
                        LOGGER.severe("The command timed out.");
                    }
                    throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
                } else if (proc.exitValue() != 0 && failOnExit_) {
                    if (logSevere) {
                        LOGGER.log(Level.SEVERE, "The command exit value/status is: {0}", proc.exitValue());
                    }
                    ExitStatusException.throwOnFailure(proc.exitValue());
                }
            } catch (InterruptedException e) {
                if (logSevere) {
                    LOGGER.log(Level.SEVERE, "The command was interrupted.", e);
                }
                throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
            } finally {
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                }
            }
        }
    }

    /**
     * Determines if the current operating system is AIX.
     *
     * @return {@code true} if the operating system is identified as AIX, {@code false} otherwise
     */
    public static boolean isAix() {
        return SystemTools.isAix();
    }

    /**
     * Determines if the current operating system is Cygwin.
     *
     * @return {@code true} if the operating system is identified as Cygwin, {@code false} otherwise
     */
    public static boolean isCygwin() {
        return SystemTools.isCygwin();
    }

    /**
     * Determines if the current operating system is FreeBSD.
     *
     * @return {@code true} if the operating system is FreeBSD, {@code false} otherwise
     */
    public static boolean isFreeBsd() {
        return SystemTools.isFreeBsd();
    }

    /**
     * Determines if the operating system is Linux.
     *
     * @return {@code true} if the operating system is Linux, {@code false} otherwise
     */
    public static boolean isLinux() {
        return SystemTools.isLinux();
    }

    /**
     * Determines if the current operating system is macOS.
     *
     * @return {@code true} if the OS is macOS, {@code false} otherwise
     */
    public static boolean isMacOS() {
        return SystemTools.isMacOS();
    }

    /**
     * Determines if the current operating system is MinGW.
     *
     * @return {@code true} if the operating system is identified as MinGW, {@code false} otherwise
     */
    public static boolean isMingw() {
        return SystemTools.isMingw();
    }

    /**
     * Determines if the current operating system is OpenVMS.
     *
     * @return {@code true} if the operating system is OpenVMS, {@code false} otherwise
     */
    public static boolean isOpenVms() {
        return SystemTools.isOpenVms();
    }

    /**
     * Determines if the current operating system is Solaris.
     *
     * @return {@code true} if the operating system is Solaris, {@code false} otherwise
     */
    public static boolean isSolaris() {
        return SystemTools.isSolaris();
    }

    /**
     * Determines if the current operating system is Windows.
     *
     * @return {@code true} if the operating system is Windows, {@code false} otherwise
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
     * @param arg one or more arguments
     * @return this operation instance
     * @see #command(Collection...)
     */
    public ExecOperation command(String... arg) {
        if (ObjectTools.isNotEmpty(arg)) {
            return command(List.of(arg));
        }
        return this;
    }

    /**
     * Returns the command and arguments to be executed.
     *
     * @return the command and arguments
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public List<String> command() {
        return args_;
    }

    /**
     * Configures the command and arguments to be executed.
     *
     * @param args the list of arguments
     * @return this operation instance
     * @see #command(String...)
     */
    @SafeVarargs
    public final ExecOperation command(Collection<String>... args) {
        args_.addAll(CollectionTools.combine(args));
        return this;
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
     * @param project the project
     * @return this operation instance
     */
    public ExecOperation fromProject(BaseProject project) {
        workDir_ = project.workDirectory();
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
     * Configure the command timeout.
     *
     * @param timeout The timeout in seconds
     * @return this operation instance
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
     * @return the timeout
     */
    public int timeout() {
        return timeout_;
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory
     * @return this operation instance
     */
    public ExecOperation workDir(File dir) {
        workDir_ = dir;
        return this;
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory
     * @return this operation instance
     */
    public ExecOperation workDir(Path dir) {
        return workDir(dir.toFile());
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory path
     * @return this operation instance
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public ExecOperation workDir(String dir) {
        return workDir(new File(dir));
    }

    /**
     * Returns the working directory.
     *
     * @return the directory
     */
    public File workDir() {
        return workDir_;
    }
}
