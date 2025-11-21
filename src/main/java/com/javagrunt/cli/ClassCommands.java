package com.javagrunt.cli;

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

@ShellComponent
public class ClassCommands {
    @ShellMethod(value = "Rename @SpringBootApplication class to Application using OpenRewrite")
    public String renameMain() {
        try {
            // Parse the project
            Path projectDir = Paths.get(".").toAbsolutePath().normalize();

            List<Path> sourceFiles = Files.walk(projectDir.resolve("src"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            if (sourceFiles.isEmpty()) {
                return "No Java source files found in src directory.";
            }

            ExecutionContext ctx = new InMemoryExecutionContext();

            // Use JavaParser instead of MavenParser for simpler parsing without Maven context
            JavaParser javaParser = JavaParser.fromJavaVersion()
                    .build();

            List<SourceFile> sources = javaParser.parse(sourceFiles, projectDir, ctx).toList();

            if (sources.isEmpty()) {
                return "Failed to parse Java source files. Found " + sourceFiles.size() + " .java files but parsing returned no results.";
            }

            // Find the class with @SpringBootApplication
            AtomicReference<String> oldClassName = new AtomicReference<>();
            AtomicReference<String> packageName = new AtomicReference<>();

            // Create visitor to find the main application class
            JavaIsoVisitor<ExecutionContext> findVisitor = new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

                    // Check if class has @SpringBootApplication annotation
                    boolean hasSpringBootApp = c.getLeadingAnnotations().stream()
                            .anyMatch(ann -> ann.getSimpleName().contains("SpringBootApplication"));

                    if (hasSpringBootApp) {
                        oldClassName.set(c.getSimpleName());

                        // Get package name
                        J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                        if (cu != null && cu.getPackageDeclaration() != null) {
                            packageName.set(cu.getPackageDeclaration().getExpression().toString());
                        }
                    }

                    return c;
                }
            };

            // Visit all sources to find the main class
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

            // Create custom recipe to rename the class declaration
            String oldFqn = packageName.get() + "." + oldClassName.get();
            String newFqn = packageName.get() + ".Application";
            String oldClassSimpleName = oldClassName.get();

            Recipe renameClassDeclaration = new Recipe() {
                @Override
                public String getDisplayName() {
                    return "Rename class declaration";
                }

                @Override
                public String getDescription() {
                    return "Renames the class declaration from " + oldClassSimpleName + " to Application";
                }

                @Override
                public TreeVisitor<?, ExecutionContext> getVisitor() {
                    return new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                            if (c.getSimpleName().equals(oldClassSimpleName)) {
                                return c.withName(c.getName().withSimpleName("Application"));
                            }
                            return c;
                        }
                    };
                }
            };

            Recipe renameRecipe = new CompositeRecipe(Arrays.asList(
                    renameClassDeclaration,  // First rename the class declaration
                    new ChangeType(oldFqn, newFqn, true),  // Then update all type references
                    new ChangeType(oldFqn + "Tests", packageName.get() + ".ApplicationTests", true)  // Also rename test class if it exists
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
                    String newFilePath = oldFilePath.replace(oldClassSimpleName + ".java", "Application.java");

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

            return String.format("Successfully renamed %s to Application.", oldClassName.get());

        } catch (Exception e) {
            return "Error during rename operation: " + e.getMessage();
        }
    }
}
