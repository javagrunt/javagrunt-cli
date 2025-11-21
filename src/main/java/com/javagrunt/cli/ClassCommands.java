package com.javagrunt.cli;

import org.openrewrite.*;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.MavenParser;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ShellComponent
public class ClassCommands {
    @ShellMethod(value = "Rename @SpringBootApplication class to Application using OpenRewrite")
    public String renameMainApplicationClassWithOpenRewrite() {
        try {
            // Parse the project
            Path projectDir = Paths.get(".");
            MavenParser mavenParser = MavenParser.builder()
                    .build();

            List<Path> sourceFiles = Files.walk(projectDir.resolve("src"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            ExecutionContext ctx = new InMemoryExecutionContext();
            List<SourceFile> sources = mavenParser.parse(sourceFiles, projectDir, ctx).toList();

            // Find the class with @SpringBootApplication
            AtomicReference<String> oldClassName = new AtomicReference<>();
            AtomicReference<String> packageName = new AtomicReference<>();

            // First pass: find the main class by manually visiting sources
            JavaIsoVisitor<ExecutionContext> findVisitor = new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

                    if (c.getLeadingAnnotations().stream()
                            .anyMatch(ann -> ann.getSimpleName().equals("SpringBootApplication"))) {
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
                findVisitor.visit(source, ctx);
            }

            if (oldClassName.get() == null) {
                return "No class with @SpringBootApplication annotation found.";
            }

            if (oldClassName.get().equals("Application")) {
                return "Main class is already named Application.";
            }

            // Create the rename recipe
            String oldFqn = packageName.get() + "." + oldClassName.get();
            String newFqn = packageName.get() + ".Application";

            Recipe renameRecipe = new CompositeRecipe(Arrays.asList(
                    new ChangeType(oldFqn, newFqn, true),
                    new ChangeType(oldFqn + "Tests", packageName.get() + ".ApplicationTests", true)
            ));

            // Apply the recipe
            RecipeRun run = renameRecipe.run(new InMemoryLargeSourceSet(sources), ctx);

            // Write the changes back
            for (Result result : run.getChangeset().getAllResults()) {
                if (result.getAfter() != null) {
                    SourceFile after = result.getAfter();
                    Path sourcePath = projectDir.resolve(after.getSourcePath());
                    Files.createDirectories(sourcePath.getParent());
                    Files.writeString(sourcePath, after.printAll());
                }
            }

            return String.format("Successfully renamed %s to Application using OpenRewrite.", oldClassName.get());

        } catch (Exception e) {
            return "Error during rename operation: " + e.getMessage();
        }
    }
}
