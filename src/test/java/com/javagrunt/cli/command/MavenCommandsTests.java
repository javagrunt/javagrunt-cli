package com.javagrunt.cli.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.shell.interactive.enabled=false"})
class MavenCommandsTests {

    private MavenCommands mavenCommands;
    private Path testFilesDir;

    @BeforeEach
    void setUp() throws IOException {
        mavenCommands = new MavenCommands();
        // Create test files in current directory
        testFilesDir = Paths.get(".").toAbsolutePath().normalize();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test files
        Path cleanXml = testFilesDir.resolve("clean.xml");
        Path testXml = testFilesDir.resolve("test-with-empty.xml");
        Path testTxt = testFilesDir.resolve("test-file.txt");
        Path pomTest = testFilesDir.resolve("pom-test.xml");
        Path pomVersion = testFilesDir.resolve("pom-version-test.xml");

        Files.deleteIfExists(cleanXml);
        Files.deleteIfExists(testXml);
        Files.deleteIfExists(testTxt);
        Files.deleteIfExists(pomTest);
        Files.deleteIfExists(pomVersion);
    }

    @Test
    void testRemoveEmptyWithCleanXml() throws IOException {
        // Create clean.xml with no empty tags
        String cleanXmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <element1>Content 1</element1>
                    <element2 attr="value">Content 2</element2>
                </root>
                """;

        Path cleanXmlPath = testFilesDir.resolve("clean.xml");
        Files.writeString(cleanXmlPath, cleanXmlContent);

        // Execute the command
        String result = mavenCommands.removeEmpty("clean.xml");

        // Verify the result
        assertEquals("No empty XML tags found in clean.xml", result);

        // Verify file content is unchanged
        String fileContent = Files.readString(cleanXmlPath);
        assertTrue(fileContent.contains("<element1>Content 1</element1>"));
        assertTrue(fileContent.contains("<element2 attr=\"value\">Content 2</element2>"));
    }

    @Test
    void testRemoveEmptyWithEmptyTags() throws IOException {
        // Create XML with empty tags
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <element1>Some content</element1>
                    <element2/>
                    <element3></element3>
                    <element4 attribute="value"/>
                    <parent>
                        <child1/>
                        <child2>Content</child2>
                    </parent>
                </root>
                """;

        Path xmlPath = testFilesDir.resolve("test-with-empty.xml");
        Files.writeString(xmlPath, xmlContent);

        // Execute the command
        String result = mavenCommands.removeEmpty("test-with-empty.xml");

        // Verify the result
        assertEquals("Successfully removed empty XML tags from test-with-empty.xml", result);

        // Verify empty tags were removed
        String fileContent = Files.readString(xmlPath);
        assertFalse(fileContent.contains("<element2/>"));
        assertFalse(fileContent.contains("<element3>"));
        assertFalse(fileContent.contains("<child1/>"));
        assertTrue(fileContent.contains("<element1>Some content</element1>"));
        assertTrue(fileContent.contains("<element4 attribute=\"value\"/>"));
        assertTrue(fileContent.contains("<child2>Content</child2>"));
    }

    @Test
    void testRemoveEmptyWithNonExistentFile() {
        // Execute the command with non-existent file
        String result = mavenCommands.removeEmpty("nonexistent-file.xml");

        // Verify the result
        assertEquals("File not found: nonexistent-file.xml", result);
    }

    @Test
    void testRemoveEmptyWithNonXmlFile() throws IOException {
        // Create a non-XML file
        Path txtPath = testFilesDir.resolve("test-file.txt");
        Files.writeString(txtPath, "test content");

        // Execute the command
        String result = mavenCommands.removeEmpty("test-file.txt");

        // Verify the result
        assertEquals("File must be an XML file (.xml extension required)", result);
    }

    @Test
    void testRemoveEmptyWithPomXml() throws IOException {
        // Create a pom-test.xml with empty tags
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test</artifactId>
                    <version>1.0.0</version>
                    <url/>
                    <licenses>
                        <license/>
                    </licenses>
                </project>
                """;

        Path pomPath = testFilesDir.resolve("pom-test.xml");
        Files.writeString(pomPath, pomContent);

        // Execute the command
        String result = mavenCommands.removeEmpty("pom-test.xml");

        // Verify the result
        assertEquals("Successfully removed empty XML tags from pom-test.xml", result);

        // Verify empty tags were removed
        String fileContent = Files.readString(pomPath);
        assertFalse(fileContent.contains("<url/>"));
        assertFalse(fileContent.contains("<license/>"));
        assertTrue(fileContent.contains("<modelVersion>4.0.0</modelVersion>"));
    }

    @Test
    void testProjectVersionWithValidPom() throws IOException {
        // Create a valid pom.xml
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                    <name>Test Project</name>
                </project>
                """;

        Path pomPath = testFilesDir.resolve("pom-version-test.xml");
        Files.writeString(pomPath, pomContent);

        // Temporarily rename actual pom.xml if it exists
        Path actualPom = testFilesDir.resolve("pom.xml");
        Path backupPom = testFilesDir.resolve("pom.xml.backup");
        boolean hadActualPom = Files.exists(actualPom);
        if (hadActualPom) {
            Files.move(actualPom, backupPom);
        }

        try {
            // Copy test pom to pom.xml location
            Files.copy(pomPath, actualPom);

            // Execute the command
            String result = mavenCommands.projectVersion("2.0.0");

            // Verify the result
            assertEquals("Successfully changed project version to 2.0.0", result);

            // Verify version was changed
            String fileContent = Files.readString(actualPom);
            assertTrue(fileContent.contains("<version>2.0.0</version>"));
            assertFalse(fileContent.contains("<version>1.0.0</version>"));
        } finally {
            // Clean up and restore original pom.xml
            Files.deleteIfExists(actualPom);
            if (hadActualPom) {
                Files.move(backupPom, actualPom);
            }
        }
    }

    @Test
    void testProjectVersionWithDefaultValue() throws IOException {
        // Create a valid pom.xml
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        Path actualPom = testFilesDir.resolve("pom.xml");
        Path backupPom = testFilesDir.resolve("pom.xml.backup");
        boolean hadActualPom = Files.exists(actualPom);
        if (hadActualPom) {
            Files.move(actualPom, backupPom);
        }

        try {
            Files.writeString(actualPom, pomContent);

            // Execute the command with default value
            String result = mavenCommands.projectVersion("0");

            // Verify the result
            assertEquals("Successfully changed project version to 0", result);

            // Verify version was changed to default value
            String fileContent = Files.readString(actualPom);
            assertTrue(fileContent.contains("<version>0</version>"));
        } finally {
            Files.deleteIfExists(actualPom);
            if (hadActualPom) {
                Files.move(backupPom, actualPom);
            }
        }
    }

    @Test
    void testProjectVersionWithNonExistentPom() {
        // Temporarily rename pom.xml if it exists to simulate non-existent
        Path actualPom = testFilesDir.resolve("pom.xml");
        Path backupPom = testFilesDir.resolve("pom.xml.backup");
        boolean hadActualPom = false;

        try {
            if (Files.exists(actualPom)) {
                hadActualPom = true;
                Files.move(actualPom, backupPom);
            }

            // Execute the command
            String result = mavenCommands.projectVersion("1.0.0");

            // Verify the result
            assertEquals("File not found: pom.xml", result);
        } catch (IOException e) {
            fail("Test setup failed: " + e.getMessage());
        } finally {
            try {
                if (hadActualPom) {
                    Files.move(backupPom, actualPom);
                }
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }
}
