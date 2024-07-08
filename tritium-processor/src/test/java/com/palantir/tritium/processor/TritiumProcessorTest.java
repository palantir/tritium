/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.tritium.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.ByteSource;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.tritium.examples.AnnotatedAbstractClass;
import com.palantir.tritium.examples.AnnotatedClass;
import com.palantir.tritium.examples.AnnotatedMethod;
import com.palantir.tritium.examples.BindsParameter;
import com.palantir.tritium.examples.DelegateToCallable;
import com.palantir.tritium.examples.DelegateToCallableMethod;
import com.palantir.tritium.examples.DelegateToRunnable;
import com.palantir.tritium.examples.DelegateToRunnableMethod;
import com.palantir.tritium.examples.DeprecatedMethod;
import com.palantir.tritium.examples.DeprecatedType;
import com.palantir.tritium.examples.Empty;
import com.palantir.tritium.examples.HasDefaultMethod;
import com.palantir.tritium.examples.HasToString;
import com.palantir.tritium.examples.OverlappingNames;
import com.palantir.tritium.examples.Overloaded;
import com.palantir.tritium.examples.Parameterized;
import com.palantir.tritium.examples.Simple;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

public final class TritiumProcessorTest {

    private static final boolean DEV_MODE = Boolean.getBoolean("recreate");
    private static final Path TEST_CLASSES_BASE_DIR = Paths.get("src", "test", "java");
    private static final Path RESOURCES_BASE_DIR = Paths.get("src", "test", "resources");

    @Test
    public void testExampleFileCompiles() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, Simple.class);
    }

    @Test
    public void testExampleWithToString() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, HasToString.class);
    }

    @Test
    public void testOverloaded() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, Overloaded.class);
    }

    @Test
    public void testParameterized() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, Parameterized.class);
    }

    @Test
    public void testDefault() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, HasDefaultMethod.class);
    }

    @Test
    public void testBindsParameter() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, BindsParameter.class);
    }

    @Test
    public void testDelegate() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, DelegateToRunnable.class);
    }

    @Test
    public void testDelegateTypeParameter() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, DelegateToCallable.class);
    }

    @Test
    public void testOverlappingNames() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, OverlappingNames.class);
    }

    @Test
    public void testDeprecatedMethod() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, DeprecatedMethod.class);
    }

    @Test
    @SuppressWarnings({"deprecation", "UnnecessarilyFullyQualified"}) // testing deprecation linting
    public void testDeprecatedInterface() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, com.palantir.tritium.examples.DeprecatedInterface.class);
    }

    @Test
    public void testDeprecatedType() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, DeprecatedType.class);
    }

    @Test
    public void testEmptyInterface() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, Empty.class);
    }

    @Test
    public void testAnnotatedMethod() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, AnnotatedMethod.class);
    }

    @Test
    public void testAnnotatedMethodDelegate() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, DelegateToRunnableMethod.class);
    }

    @Test
    public void testAnnotatedMethodDelegateTypeParameter() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, DelegateToCallableMethod.class);
    }

    @Test
    public void testAnnotatedAbstractClass() {
        Compilation compilation = compileTestClass(TEST_CLASSES_BASE_DIR, AnnotatedAbstractClass.class);
        assertThat(compilation).hadErrorContaining("Only interfaces may be instrumented using @Instrument");
    }

    @Test
    public void testAnnotatedClass() {
        Compilation compilation = compileTestClass(TEST_CLASSES_BASE_DIR, AnnotatedClass.class);
        assertThat(compilation).hadErrorContaining("Only interfaces may be instrumented using @Instrument");
    }

    private static void assertTestFileCompileAndMatches(Path basePath, Class<?> clazz) {
        Compilation compilation = compileTestClass(basePath, clazz);
        assertThat(compilation).succeededWithoutWarnings();
        String generatedClassName = "Instrumented" + clazz.getSimpleName();
        String generatedFqnClassName = clazz.getPackage().getName() + "." + generatedClassName;
        String generatedClassFileRelativePath = generatedFqnClassName.replaceAll("\\.", "/") + ".java";
        assertThat(compilation.generatedFile(StandardLocation.SOURCE_OUTPUT, generatedClassFileRelativePath))
                .hasValueSatisfying(
                        javaFileObject -> assertContentsMatch(javaFileObject, generatedClassFileRelativePath));
    }

    private static Compilation compileTestClass(Path basePath, Class<?> clazz) {
        Path clazzPath = basePath.resolve(Paths.get(
                Joiner.on("/").join(Splitter.on(".").split(clazz.getPackage().getName())),
                clazz.getSimpleName() + ".java"));
        try {
            return Compiler.javac()
                    .withOptions("-source", "11", "-Werror", "-Xlint:deprecation", "-Xlint:unchecked")
                    .withProcessors(new TritiumAnnotationProcessor())
                    .compile(JavaFileObjects.forResource(clazzPath.toUri().toURL()));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertContentsMatch(JavaFileObject javaFileObject, String generatedClassFile) {
        try {
            Path output = RESOURCES_BASE_DIR.resolve(generatedClassFile + ".generated");
            String generatedContents = readJavaFileObject(javaFileObject);
            if (DEV_MODE) {
                Files.deleteIfExists(output);
                Files.write(output, generatedContents.getBytes(StandardCharsets.UTF_8));
            }
            assertThat(generatedContents).isEqualTo(readFromFile(output));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readJavaFileObject(JavaFileObject javaFileObject) throws IOException {
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return javaFileObject.openInputStream();
            }
        }.asCharSource(StandardCharsets.UTF_8).read();
    }

    private static String readFromFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
