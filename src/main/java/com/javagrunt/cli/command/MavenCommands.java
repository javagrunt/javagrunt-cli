package com.javagrunt.cli.command;

import org.openrewrite.*;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.maven.ChangeProjectVersion;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.xml.RemoveEmptyXmlTags;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@ShellComponent
public class MavenCommands {

    @ShellMethod(value = "Remove empty XML tags from pom.xml or specified file")
    public String removeEmpty(@ShellOption(defaultValue = "pom.xml") String fileName) {
        try {
            Path projectDir = Paths.get(".").toAbsolutePath().normalize();
            Path xmlFile = projectDir.resolve(fileName);

            if (!Files.exists(xmlFile)) {
                return String.format("File not found: %s", fileName);
            }

            if (!fileName.endsWith(".xml")) {
                return "File must be an XML file (.xml extension required)";
            }

            ExecutionContext ctx = new InMemoryExecutionContext();

            XmlParser xmlParser = new XmlParser();
            List<SourceFile> sources = xmlParser.parse(List.of(xmlFile), projectDir, ctx).toList();

            if (sources.isEmpty()) {
                return "Failed to parse XML file: " + fileName;
            }

            Recipe recipe = new RemoveEmptyXmlTags();
            List<Result> results = recipe.run(new InMemoryLargeSourceSet(sources), ctx).getChangeset().getAllResults();

            if (results.isEmpty()) {
                return String.format("No empty XML tags found in %s", fileName);
            }

            for (Result result : results) {
                if (result.getAfter() != null) {
                    SourceFile after = result.getAfter();
                    Path targetPath = projectDir.resolve(after.getSourcePath());
                    Files.createDirectories(targetPath.getParent());
                    Files.writeString(targetPath, after.printAll());
                }
            }

            return String.format("Successfully removed empty XML tags from %s", fileName);

        } catch (Exception e) {
            return "Error during remove empty tags operation: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Change the project version in pom.xml")
    public String projectVersion(@ShellOption(defaultValue = "0") String version) {
        try {
            Path projectDir = Paths.get(".").toAbsolutePath().normalize();
            Path pomFile = projectDir.resolve("pom.xml");

            if (!Files.exists(pomFile)) {
                return "File not found: pom.xml";
            }

            ExecutionContext ctx = new InMemoryExecutionContext();

            MavenParser mavenParser = MavenParser.builder().build();
            List<SourceFile> sources = mavenParser.parse(List.of(pomFile), projectDir, ctx).toList();

            if (sources.isEmpty()) {
                return "Failed to parse pom.xml";
            }

            String groupId = null;
            String artifactId = null;

            for (SourceFile source : sources) {
                if (source instanceof Xml.Document) {
                    Xml.Document doc = (Xml.Document) source;
                    Xml.Tag root = doc.getRoot();

                    for (Xml.Tag child : root.getChildren()) {
                        if (child.getName().equals("groupId") && child.getValue().isPresent()) {
                            groupId = child.getValue().get();
                        } else if (child.getName().equals("artifactId") && child.getValue().isPresent()) {
                            artifactId = child.getValue().get();
                        }
                    }
                }
            }

            if (groupId == null || artifactId == null) {
                return "Could not find groupId or artifactId in pom.xml";
            }

            Recipe recipe = new ChangeProjectVersion(groupId, artifactId, version, null);
            List<Result> results = recipe.run(new InMemoryLargeSourceSet(sources), ctx).getChangeset().getAllResults();

            if (results.isEmpty()) {
                return "No changes made to pom.xml. Version may already be set to " + version;
            }

            for (Result result : results) {
                if (result.getAfter() != null) {
                    SourceFile after = result.getAfter();
                    Path targetPath = projectDir.resolve(after.getSourcePath());
                    Files.createDirectories(targetPath.getParent());
                    Files.writeString(targetPath, after.printAll());
                }
            }

            return String.format("Successfully changed project version to %s", version);

        } catch (Exception e) {
            return "Error during project version change: " + e.getMessage();
        }
    }
}
