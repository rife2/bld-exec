/*
 * Copyright 2023 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
public class ExecOperation extends AbstractOperation<ExecOperation> {
    private static final Logger LOGGER = Logger.getLogger(ExecOperation.class.getName());
    private final List<String> args_ = new ArrayList<>();
    private final Set<ExecFail> fail_ = new HashSet<>();
    private BaseProject project_;
    private String workDir_;

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
            LOGGER.severe("A project must be specified.");
        }

        var errorMessage = new StringBuilder(27);

        final File workDir;
        if (workDir_ == null || workDir_.isBlank()) {
            workDir = new File(project_.workDirectory().getAbsolutePath());
        } else {
            workDir = new File(workDir_);
        }

        if (workDir.isDirectory()) {
            var pb = new ProcessBuilder();
            pb.command(args_);
            pb.directory(workDir);

            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info(String.join(" ", args_));
            }

            var proc = pb.start();
            var err = proc.waitFor(30, TimeUnit.SECONDS);
            var stdout = readStream(proc.getInputStream());
            var stderr = readStream(proc.getErrorStream());

            if (!err) {
                errorMessage.append("TIMEOUT");
            } else if (!fail_.contains(ExecFail.NONE)) {
                var all = fail_.contains(ExecFail.ALL);
                var output = fail_.contains(ExecFail.OUTPUT);
                if ((all || fail_.contains(ExecFail.EXIT) || fail_.contains(ExecFail.NORMAL)) && proc.exitValue() > 0) {
                    errorMessage.append("EXIT ").append(proc.exitValue());
                    if (!stderr.isEmpty()) {
                        errorMessage.append(", STDERR -> ").append(stderr.get(0));
                    } else if (!stdout.isEmpty()) {
                        errorMessage.append(", STDOUT -> ").append(stdout.get(0));
                    }
                } else if ((all || output || fail_.contains(ExecFail.STDERR) || fail_.contains(ExecFail.NORMAL))
                        && !stderr.isEmpty()) {
                    errorMessage.append("STDERR -> ").append(stderr.get(0));
                } else if ((all || output || fail_.contains(ExecFail.STDOUT)) && !stdout.isEmpty()) {
                    errorMessage.append("STDOUT -> ").append(stdout.get(0));
                }
            }

            if (LOGGER.isLoggable(Level.INFO) && errorMessage.isEmpty() && !stdout.isEmpty()) {
                for (var l : stdout) {
                    LOGGER.info(l);
                }
            }
        } else {
            errorMessage.append("Invalid working directory: ").append(workDir.getCanonicalPath());
        }

        if (!errorMessage.isEmpty()) {
            throw new IOException(errorMessage.toString());
        }
    }

    /**
     * Configure the failure mode.
     * <p>
     * The failure modes are:
     * <ul>
     *     <li>{@link ExecFail#EXIT}<p>Exit value > 0</p></li>
     *     <li>{@link ExecFail#NORMAL}<p>Exit value > 0 or any data to the standard error stream (stderr)</p></li>
     *     <li>{@link ExecFail#OUTPUT}<p>Any data to the standard output stream (stdout) or stderr</p></li>
     *     <li>{@link ExecFail#STDERR}<p>Any data to stderr</p></li>
     *     <li>{@link ExecFail#STDOUT}<p>Any data to stdout</p></li>
     *     <li>{@link ExecFail#ALL}<p>Any of the conditions above</p></li>
     *     <li>{@link ExecFail#NONE}<p>Never fails</p></li>
     * </ul>
     *
     * @param fail one or more failure modes
     * @return this operation instance
     * @see ExecFail
     */
    public ExecOperation fail(ExecFail... fail) {
        fail_.addAll(Set.of(fail));
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

    private List<String> readStream(InputStream stream) {
        var lines = new ArrayList<String>();
        try (var scanner = new Scanner(stream)) {
            while (scanner.hasNextLine()) {
                lines.add(scanner.nextLine());
            }
        }
        return lines;
    }

    /**
     * Configures the working directory.
     *
     * @param dir the directory path
     * @return this operation instance
     */
    public ExecOperation workDir(String dir) {
        workDir_ = dir;
        return this;
    }
}