package com.tw.go.plugin.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GoPluginImplTest {
    @Test
    public void shouldCleanupScript() {
        GoPluginImpl plugin = new GoPluginImpl();
        String lineSeparator = System.getProperty("line.separator");

        assertThat(plugin.cleanupScript("")).isEqualTo("");
        assertThat(plugin.cleanupScript("a")).isEqualTo("a");
        assertThat(plugin.cleanupScript("a\n")).isEqualTo("a" + lineSeparator);
        assertThat(plugin.cleanupScript("a\nb")).isEqualTo("a" + lineSeparator + "b");
        assertThat(plugin.cleanupScript("a\rb")).isEqualTo("a" + lineSeparator + "b");
        assertThat(plugin.cleanupScript("a\r\nb")).isEqualTo("a" + lineSeparator + "b");
    }
}