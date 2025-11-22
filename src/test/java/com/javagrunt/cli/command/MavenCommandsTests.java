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

        Files.deleteIfExists(cleanXml);
        Files.deleteIfExists(testXml);
        Files.deleteIfExists(testTxt);
        Files.deleteIfExists(pomTest);
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
}
