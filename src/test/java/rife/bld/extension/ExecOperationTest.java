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

import org.junit.jupiter.api.Test;
import rife.bld.BaseProject;
import rife.bld.Project;
import rife.bld.WebProject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;


class ExecOperationTest {
    private static final String FOO = "foo";
    private static final String HELLO = "Hello";

    @Test
    void testAll() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new Project())
                        .command("date")
                        .fail(ExecFail.ALL)
                        .execute()
        ).isInstanceOf(IOException.class);
    }

    @Test
    void testCat() throws Exception {
        var tmpFile = new File("hello.tmp");
        tmpFile.deleteOnExit();
        new ExecOperation()
                .fromProject(new Project())
                .timeout(10)
                .command("touch", tmpFile.getName())
                .fail(ExecFail.NORMAL)
                .execute();

        assertThat(tmpFile).exists();
    }

    @Test
    void testCommandList() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command(List.of("logger", "-s", HELLO))
                        .fail(ExecFail.STDERR)
                        .execute()).message().startsWith("STDERR -> ").endsWith(HELLO);
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
    void testExit() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command("tail", FOO)
                        .fail(ExecFail.EXIT)
                        .execute()).message().startsWith("EXIT ");
    }

    @Test
    void testNone() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new WebProject())
                        .command("cat", FOO)
                        .fail(ExecFail.NONE)
                        .execute()).doesNotThrowAnyException();
    }

    @Test
    void testOutput() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new WebProject())
                        .command("echo")
                        .fail(ExecFail.OUTPUT)
                        .execute()
        ).message().isEqualTo("STDOUT -> ");
    }

    @Test
    void testStdErr() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command("logger", "-s", HELLO)
                        .fail(ExecFail.STDERR)
                        .execute()).message().startsWith("STDERR -> ").endsWith(HELLO);
    }

    @Test
    void testStdOut() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command("echo", HELLO)
                        .fail(ExecFail.STDOUT)
                        .execute()).message().isEqualTo("STDOUT -> Hello");
    }

    @Test
    void testWorkDir() {
        assertThatCode(() ->
                new ExecOperation()
                        .fromProject(new BaseProject())
                        .command("echo")
                        .workDir(FOO)
                        .fail(ExecFail.NORMAL)
                        .execute()).message().startsWith("Invalid working directory: ").endsWith(FOO);
    }
}
