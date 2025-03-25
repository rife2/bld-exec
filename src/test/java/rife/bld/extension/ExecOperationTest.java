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
    private static final String FOO = "foo";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
    private static final String CAT = IS_WINDOWS ? "type" : "cat";

    @Test
    void testCommand() {
        var op = new ExecOperation().fromProject(new WebProject())
                .command(FOO, "bar");
        assertThat(op.command()).containsExactly(FOO, "bar");
    }

    @Test
    void testException() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command(FOO)
                        .execute()).message().startsWith("Cannot run program \"" + FOO + '"');
    }

    @Test
    void testExitValue() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command(List.of(CAT, FOO))
                        .execute()).isInstanceOf(ExitStatusException.class);
    }

    @Test
    void testFailOnExit() {
        var op = new ExecOperation()
                .fromProject(new BaseProject())
                .command(List.of(CAT, FOO))
                .failOnExit(false);
        assertThat(op.isFailOnExit()).isFalse();
        assertThatCode(op::execute).doesNotThrowAnyException();

        op.failOnExit(true);
        assertThat(op.isFailOnExit()).isTrue();
    }

    @Test
    void testTimeout() {
        List<String> sleep;
        if (IS_WINDOWS) {
            sleep = List.of("timeout", "/t", "10");
        } else {
            sleep = List.of("sleep", "10");
        }
        var op = new ExecOperation()
                .fromProject(new BaseProject())
                .timeout(5)
                .command(sleep);
        assertThat(op.timeout()).isEqualTo(5);
        assertThatCode(op::execute).isInstanceOf(ExitStatusException.class);
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testTouch() throws Exception {
        var tmpFile = new File("hello.tmp");
        tmpFile.deleteOnExit();
        new ExecOperation()
                .fromProject(new Project())
                .timeout(10)
                .command("touch", tmpFile.getName())
                .execute();

        assertThat(tmpFile).exists();
    }

    @Test
    void testWorkDir() {
        var workDir = new File(System.getProperty("java.io.tmpdir"));
        var op = new ExecOperation()
                .fromProject(new BaseProject())
                .command("echo", FOO)
                .workDir(workDir);
        assertThat(op.workDir()).as("as file").isEqualTo(workDir);
        assertThatCode(op::execute).doesNotThrowAnyException();

        var build = "build";
        op = op.workDir(build);
        assertThat(op.workDir()).as("as string").isEqualTo(new File(build));
        assertThatCode(op::execute).doesNotThrowAnyException();

        op = op.workDir(workDir.toPath());
        assertThat(op.workDir()).as("as path").isEqualTo(workDir);
        assertThatCode(op::execute).doesNotThrowAnyException();
    }

    @Test
    void testWorkDirInvalid() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command("echo")
                        .workDir(FOO)
                        .execute()).isInstanceOf(ExitStatusException.class);
    }
}
