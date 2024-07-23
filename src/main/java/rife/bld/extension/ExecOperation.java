/*
 * Copyright 2023-2024 the original author or authors.
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

import rife.bld.BaseProject;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
    private final Collection<String> args_ = new ArrayList<>();
    private boolean failOnExit_ = true;
    private BaseProject project_;
    private int timeout_ = 30;
    private File workDir_;

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
     * @see #command(Collection)
     */
    public ExecOperation command(String... arg) {
        args_.addAll(List.of(arg));
        return this;
    }

    /**
     * Returns the command and arguments to be executed.
     *
     * @return the command and arguments
     */
    public Collection<String> command() {
        return args_;
    }


    /**
     * Configures the command and arguments to be executed.
     *
     * @param args the list of arguments
     * @return this operation instance
     * @see #command(String...)
     */
    public ExecOperation command(Collection<String> args) {
        args_.addAll(args);
        return this;
    }

    /**
     * Executes the command.
     */
    @Override
    public void execute() throws Exception {
        if (project_ == null) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("A project must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } else {
            final File workDir = Objects.requireNonNullElseGet(workDir_,
                    () -> new File(project_.workDirectory().getAbsolutePath()));

            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Working directory: " + workDir.getAbsolutePath());
            }

            if (workDir.isDirectory()) {
                var pb = new ProcessBuilder();
                pb.inheritIO();
                pb.command(args_.stream().toList());
                pb.directory(workDir);

                if (LOGGER.isLoggable(Level.INFO) && !silent()) {
                    LOGGER.info(String.join(" ", args_));
                }

                var proc = pb.start();
                var err = proc.waitFor(timeout_, TimeUnit.SECONDS);

                if (!err) {
                    proc.destroy();
                    if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                        LOGGER.severe("The command timed out.");
                    }
                    throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
                } else if (proc.exitValue() != 0 && failOnExit_) {
                    if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                        LOGGER.severe("The command exit value/status is: " + proc.exitValue());
                    }
                    ExitStatusException.throwOnFailure(proc.exitValue());
                }
            } else {
                if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                    LOGGER.severe("Invalid working directory: " + workDir);
                }
                throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
            }
        }
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
     *
     * @param project the project
     * @return this operation instance
     */
    public ExecOperation fromProject(BaseProject project) {
        project_ = project;
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
     * @param dir the directory path
     * @return this operation instance
     */
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
