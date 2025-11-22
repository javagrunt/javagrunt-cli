package com.javagrunt.cli.command;

import org.openrewrite.*;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.xml.RemoveEmptyXmlTags;
import org.openrewrite.xml.XmlParser;
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
}
