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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
    private final static File GIT_IGNORE_FILE = new File("./.gitignore");


    @ShellMethod("add mvn extensions and config")
    public String extensions() {
        try {
            mavenConfigDir();
            extensionsConfig();
            mavenGitVersioningExtension();
            gitIgnoreVersionedPom();
        } catch (IOException ioException) {
            return "There was a problem adding extensions and config";
        }
        return "Successfully added extensions and config in ./.mvn";
    }

    private void gitIgnoreVersionedPom() throws IOException {
        final String VERSIONED_POM_PATTERN = ".git-versioned-pom.xml";
        final String GIT_IGNORE_ENTRY = """
                
                ### maven-git-versioning-extension
                .git-versioned-pom.xml
                """;

        if (!GIT_IGNORE_FILE.exists()) {
            writeStringToFile(gitignoreFile(), GIT_IGNORE_FILE);
            return;
        }

        if (!containsPattern(GIT_IGNORE_FILE, VERSIONED_POM_PATTERN)) {
            appendToFile(GIT_IGNORE_FILE, GIT_IGNORE_ENTRY);
        }

    }

    private boolean containsPattern(File file, String pattern) throws IOException {
        try (var lines = Files.lines(file.toPath())) {
            return lines.anyMatch(line -> line.contains(pattern));
        }
    }

    private void appendToFile(File file, String content) throws IOException {
        Files.writeString(
                file.toPath(),
                content,
                StandardOpenOption.APPEND
        );
    }

    private void extensionsConfig() throws IOException {
        File file = new File("./.mvn/extensions.xml");
        if (!file.exists()) {
            writeStringToFile(extensionsFile(), file);
        }
    }

    private void mavenGitVersioningExtension() throws IOException {
        File file = new File("./.mvn/maven-git-versioning-extension.xml");
        if (!file.exists()) {
            writeStringToFile(mavenGitVersioningExtensionConfig(), file);
        }
    }

    private String gitignoreFile() {
        return """
                HELP.md
                target/
                .mvn/wrapper/maven-wrapper.jar
                !**/src/main/**/target/
                !**/src/test/**/target/
                
                ### STS ###
                .apt_generated
                .classpath
                .factorypath
                .project
                .settings
                .springBeans
                .sts4-cache
                
                ### IntelliJ IDEA ###
                .idea
                *.iws
                *.iml
                *.ipr
                
                ### NetBeans ###
                /nbproject/private/
                /nbbuild/
                /dist/
                /nbdist/
                /.nb-gradle/
                build/
                !**/src/main/**/build/
                !**/src/test/**/build/
                
                ### VS Code ###
                .vscode/
                
                ###maven-git-versioning-extension
                .git-versioned-pom.xml
                """;
    }

    private String extensionsFile() {
        return """
                 <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
                     <extension>
                         <groupId>me.qoomon</groupId>
                         <artifactId>maven-git-versioning-extension</artifactId>
                         <version>9.11.0</version>
                     </extension>
                     <extension>
                         <groupId>kr.motd.maven</groupId>
                         <artifactId>os-maven-plugin</artifactId>
                         <version>1.7.1</version>
                     </extension>
                 </extensions>
                """;
    }

    private String mavenGitVersioningExtensionConfig() {
        return """
                <configuration xmlns="https://github.com/qoomon/maven-git-versioning-extension"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xsi:schemaLocation="https://github.com/qoomon/maven-git-versioning-extension https://qoomon.github.io/maven-git-versioning-extension/configuration-9.4.0.xsd">
                
                    <refs considerTagsOnBranches="true">
                        <ref type="tag">
                            <pattern><![CDATA[v(?<version>.*)]]></pattern>
                            <version>${ref.version}</version>
                        </ref>
                        <ref type="branch">
                            <pattern>(main|release.*)</pattern>
                            <version>${describe.tag.version.major}.${describe.tag.version.minor}.${describe.tag.version.patch.next}-SNAPSHOT</version>
                        </ref>
                        <ref type="branch">
                            <pattern><![CDATA[feature/(?<feature>.+)]]></pattern>
                            <version>${describe.tag.version}-${ref.feature}-SNAPSHOT</version>
                        </ref>
                    </refs>
                    <rev>
                        <version>${commit}</version>
                    </rev>
                </configuration>
                """;
    }

    private void mavenConfigDir() throws IOException {
        File file = new File("./.mvn");
        if (!file.exists()) {
            if (!file.mkdir()) {
                throw new IOException("Couldn't create directory");
            }
        }
    }

    void writeStringToFile(String data, File file) throws IOException {
        FileWriter fileWriter = new FileWriter(file, false);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        bufferedWriter.write(data);
        bufferedWriter.flush();
        bufferedWriter.close();
        fileWriter.close();
    }
}
