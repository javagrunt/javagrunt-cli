package com.javagrunt.cli.command;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

@Component
class CustomPromptProvider implements PromptProvider {

    @Override
    public AttributedString getPrompt() {
        return new AttributedString("javagrunt-cli:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }
}
