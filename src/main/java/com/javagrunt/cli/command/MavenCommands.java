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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

@ShellComponent
public class MavenCommands {

    private static final Path MVN_DIR = Paths.get(".mvn");
    private static final Path GIT_IGNORE_FILE = Paths.get(".gitignore");
    private static final Path EXTENSIONS_XML = MVN_DIR.resolve("extensions.xml");
    private static final Path VERSIONING_CONFIG_XML = MVN_DIR.resolve("maven-git-versioning-extension.xml");
    private static final String POM_XML = "pom.xml";

    private static final String VERSIONED_POM_PATTERN = ".git-versioned-pom.xml";

    private static final String GITIGNORE_TEMPLATE = """
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

    private static final String EXTENSIONS_XML_TEMPLATE = """
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

    private static final String VERSIONING_CONFIG_TEMPLATE = """
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

    private static final String GIT_IGNORE_VERSIONING_ENTRY = """

            ### maven-git-versioning-extension
            .git-versioned-pom.xml
            """;

    @ShellMethod(value = "Remove empty XML tags from pom.xml or specified file")
    public String removeEmpty(@ShellOption(defaultValue = POM_XML) String fileName) {
        try {
            Path projectDir = getCurrentProjectDir();
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
            List<Result> results = executeRecipe(recipe, sources, ctx);

            if (results.isEmpty()) {
                return String.format("No empty XML tags found in %s", fileName);
            }

            writeResults(results, projectDir);
            return String.format("Successfully removed empty XML tags from %s", fileName);

        } catch (Exception e) {
            return "Error during remove empty tags operation: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Change the project version in pom.xml")
    public String projectVersion(@ShellOption(defaultValue = "0") String version) {
        try {
            Path projectDir = getCurrentProjectDir();
            Path pomFile = projectDir.resolve(POM_XML);

            if (!Files.exists(pomFile)) {
                return "File not found: " + POM_XML;
            }

            ExecutionContext ctx = new InMemoryExecutionContext();
            MavenParser mavenParser = MavenParser.builder().build();
            List<SourceFile> sources = mavenParser.parse(List.of(pomFile), projectDir, ctx).toList();

            if (sources.isEmpty()) {
                return "Failed to parse " + POM_XML;
            }

            MavenCoordinates coordinates = extractMavenCoordinates(sources);
            if (coordinates.groupId() == null || coordinates.artifactId() == null) {
                return "Could not find groupId or artifactId in " + POM_XML;
            }

            Recipe recipe = new ChangeProjectVersion(coordinates.groupId(), coordinates.artifactId(), version, null);
            List<Result> results = executeRecipe(recipe, sources, ctx);

            if (results.isEmpty()) {
                return "No changes made to " + POM_XML + ". Version may already be set to " + version;
            }

            writeResults(results, projectDir);
            return String.format("Successfully changed project version to %s", version);

        } catch (Exception e) {
            return "Error during project version change: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Add Maven extensions and configuration")
    public String extensions() {
        try {
            createMavenConfigDir();
            createExtensionsConfig();
            createVersioningExtensionConfig();
            updateGitIgnore();
            return "Successfully added extensions and config in ./.mvn";
        } catch (IOException e) {
            return "Error adding extensions and config: " + e.getMessage();
        }
    }

    private List<Result> executeRecipe(Recipe recipe, List<SourceFile> sources, ExecutionContext ctx) {
        return recipe.run(new InMemoryLargeSourceSet(sources), ctx)
                .getChangeset()
                .getAllResults();
    }

    private void writeResults(List<Result> results, Path projectDir) throws IOException {
        for (Result result : results) {
            if (result.getAfter() != null) {
                SourceFile after = result.getAfter();
                Path targetPath = projectDir.resolve(after.getSourcePath());
                Files.createDirectories(targetPath.getParent());
                Files.writeString(targetPath, after.printAll());
            }
        }
    }

    private MavenCoordinates extractMavenCoordinates(List<SourceFile> sources) {
        String groupId = null;
        String artifactId = null;

        for (SourceFile source : sources) {
            if (source instanceof Xml.Document doc) {
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

        return new MavenCoordinates(groupId, artifactId);
    }

    private Path getCurrentProjectDir() {
        return Paths.get(".").toAbsolutePath().normalize();
    }

    private void createMavenConfigDir() throws IOException {
        if (!Files.exists(MVN_DIR)) {
            Files.createDirectories(MVN_DIR);
        }
    }

    private void createExtensionsConfig() throws IOException {
        if (!Files.exists(EXTENSIONS_XML)) {
            Files.writeString(EXTENSIONS_XML, EXTENSIONS_XML_TEMPLATE);
        }
    }

    private void createVersioningExtensionConfig() throws IOException {
        if (!Files.exists(VERSIONING_CONFIG_XML)) {
            Files.writeString(VERSIONING_CONFIG_XML, VERSIONING_CONFIG_TEMPLATE);
        }
    }

    private void updateGitIgnore() throws IOException {
        if (!Files.exists(GIT_IGNORE_FILE)) {
            Files.writeString(GIT_IGNORE_FILE, GITIGNORE_TEMPLATE);
            return;
        }

        if (!containsPattern(GIT_IGNORE_FILE, VERSIONED_POM_PATTERN)) {
            Files.writeString(GIT_IGNORE_FILE, GIT_IGNORE_VERSIONING_ENTRY, StandardOpenOption.APPEND);
        }
    }

    private boolean containsPattern(Path file, String pattern) throws IOException {
        try (var lines = Files.lines(file)) {
            return lines.anyMatch(line -> line.contains(pattern));
        }
    }

    private record MavenCoordinates(String groupId, String artifactId) {}
}
