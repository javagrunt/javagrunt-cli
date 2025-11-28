package com.javagrunt.cli.command;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.shell.interactive.enabled=false"})
class CustomPromptProviderTests {

    private CustomPromptProvider promptProvider;

    @BeforeEach
    void setUp() {
        promptProvider = new CustomPromptProvider();
    }

    @Test
    void testGetPromptReturnsCorrectText() {
        AttributedString prompt = promptProvider.getPrompt();

        assertNotNull(prompt);
        assertEquals("javagrunt-cli:>", prompt.toString());
    }

    @Test
    void testGetPromptReturnsCorrectStyle() {
        AttributedString prompt = promptProvider.getPrompt();
        assertNotNull(prompt);
        AttributedStyle style = prompt.styleAt(0);
        assertNotNull(style);
        AttributedStyle expectedStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
        assertEquals(expectedStyle, style);
    }

    @Test
    void testGetPromptIsConsistent() {
        AttributedString prompt1 = promptProvider.getPrompt();
        AttributedString prompt2 = promptProvider.getPrompt();

        assertNotNull(prompt1);
        assertNotNull(prompt2);
        assertEquals(prompt1.toString(), prompt2.toString());
        assertEquals(prompt1.styleAt(0), prompt2.styleAt(0));
    }
}
