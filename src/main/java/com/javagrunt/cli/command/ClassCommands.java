package com.javagrunt.cli.command;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ShellComponent
class ClassCommands {
    @ShellMethod(value = "Rename @SpringBootApplication class to Application using OpenRewrite")
    public String renameMain() {
        try {
            Path projectDir = Paths.get(".").toAbsolutePath().normalize();

            List<Path> sourceFiles;
            try (Stream<Path> paths = Files.walk(projectDir.resolve("src"))) {
                sourceFiles = paths
                        .filter(path -> path.toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }

            if (sourceFiles.isEmpty()) {
                return "No Java source files found in src directory.";
            }

            InMemoryExecutionContext ctx = new InMemoryExecutionContext();

            JavaParser javaParser = JavaParser.fromJavaVersion()
                    .build();

            List<SourceFile> sources = javaParser.parse(sourceFiles, projectDir, ctx).toList();

            if (sources.isEmpty()) {
                return "Failed to parse Java source files. Found " + sourceFiles.size() + " .java files but parsing returned no results.";
            }

            AtomicReference<String> oldClassName = new AtomicReference<>();
            AtomicReference<String> packageName = new AtomicReference<>();

            JavaIsoVisitor<@NotNull ExecutionContext> findVisitor = new JavaIsoVisitor<>() {
                @Override
                public J.@NotNull ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

                    boolean hasSpringBootApp = c.getLeadingAnnotations().stream()
                            .anyMatch(ann -> ann.getSimpleName().contains("SpringBootApplication"));

                    if (hasSpringBootApp) {
                        oldClassName.set(c.getSimpleName());

                        J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                        if (cu != null && cu.getPackageDeclaration() != null) {
                            packageName.set(cu.getPackageDeclaration().getExpression().toString());
                        }
                    }

                    return c;
                }
            };

            for (SourceFile source : sources) {
                if (source instanceof J.CompilationUnit) {
                    findVisitor.visit(source, ctx);
                }
            }

            if (oldClassName.get() == null) {
                return "No class with @SpringBootApplication annotation found.";
            }

            if (oldClassName.get().equals("Application")) {
                return "Main class is already named Application.";
            }

            String oldFqn = packageName.get() + "." + oldClassName.get();
            String newFqn = packageName.get() + ".Application";
            String oldClassSimpleName = oldClassName.get();
            String oldTestClassSimpleName = oldClassName.get() + "Tests";

            Recipe renameClassDeclaration = new Recipe() {
                @Override
                public @NotNull String getDisplayName() {
                    return "Rename main class declaration";
                }

                @Override
                public @NotNull String getDescription() {
                    return "Renames the class declaration from " + oldClassSimpleName + " to Application";
                }

                @Override
                public @NotNull TreeVisitor<?, @NotNull ExecutionContext> getVisitor() {
                    return new JavaIsoVisitor<>() {
                        @Override
                        public J.@NotNull ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl, ExecutionContext ctx) {
                            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                            if (c.getSimpleName().equals(oldClassSimpleName)) {
                                return c.withName(c.getName().withSimpleName("Application"));
                            }
                            return c;
                        }
                    };
                }
            };

            Recipe renameTestClassDeclaration = new Recipe() {
                @Override
                public @NotNull String getDisplayName() {
                    return "Rename test class declaration";
                }

                @Override
                public @NotNull String getDescription() {
                    return "Renames the test class declaration from " + oldTestClassSimpleName + " to ApplicationTests";
                }

                @Override
                public @NotNull TreeVisitor<?, @NotNull ExecutionContext> getVisitor() {
                    return new JavaIsoVisitor<>() {
                        @Override
                        public J.@NotNull ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl, ExecutionContext ctx) {
                            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                            if (c.getSimpleName().equals(oldTestClassSimpleName)) {
                                return c.withName(c.getName().withSimpleName("ApplicationTests"));
                            }
                            return c;
                        }
                    };
                }
            };

            Recipe renameRecipe = new CompositeRecipe(Arrays.asList(
                    renameClassDeclaration,  // Rename the main class declaration
                    renameTestClassDeclaration,  // Rename the test class declaration
                    new ChangeType(oldFqn, newFqn, true),  // Update all type references for main class
                    new ChangeType(oldFqn + "Tests", packageName.get() + ".ApplicationTests", true)  // Update all type references for test class
            ));

            // Apply the recipe
            RecipeRun run = renameRecipe.run(new InMemoryLargeSourceSet(sources), ctx);

            // Write the changes back and handle file renames
            for (Result result : run.getChangeset().getAllResults()) {

                if (result.getBefore() != null && result.getAfter() != null) {
                    Path oldPath = projectDir.resolve(result.getBefore().getSourcePath());
                    SourceFile after = result.getAfter();

                    // Update source path to match the new class name
                    String oldFilePath = after.getSourcePath().toString();
                    String newFilePath = getString(oldFilePath, oldClassSimpleName, oldTestClassSimpleName);

                    // If file name changed, update the source path
                    Path newPath;
                    if (!oldFilePath.equals(newFilePath)) {
                        after = after.withSourcePath(Paths.get(newFilePath));
                        newPath = projectDir.resolve(newFilePath);

                        // Delete the old file
                        if (Files.exists(oldPath)) {
                            Files.delete(oldPath);
                        }
                    } else {
                        newPath = oldPath;
                    }

                    // Write the new/updated file
                    Files.createDirectories(newPath.getParent());
                    Files.writeString(newPath, after.printAll());
                }
            }

            return String.format("Successfully renamed %s to Application and %sTests to ApplicationTests.",
                    oldClassName.get(), oldClassName.get());

        } catch (Exception e) {
            return "Error during rename operation: " + e.getMessage();
        }
    }

    private static @NotNull String getString(String oldFilePath, String oldClassSimpleName, String oldTestClassSimpleName) {
        String newFilePath = oldFilePath;

        // Handle main class file rename
        if (oldFilePath.contains(oldClassSimpleName + ".java")) {
            newFilePath = oldFilePath.replace(oldClassSimpleName + ".java", "Application.java");
        }
        // Handle test class file rename
        else if (oldFilePath.contains(oldTestClassSimpleName + ".java")) {
            newFilePath = oldFilePath.replace(oldTestClassSimpleName + ".java", "ApplicationTests.java");
        }
        return newFilePath;
    }
}
