package com.tw.go.plugin.task;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class GoPluginImplTest {
    @Test
    public void shouldCleanupScript() {
        GoPluginImpl plugin = new GoPluginImpl();
        String lineSeparator = System.getProperty("line.separator");

        assertThat(plugin.cleanupScript(""), is(""));
        assertThat(plugin.cleanupScript("a"), is("a"));
        assertThat(plugin.cleanupScript("a\n"), is("a" + lineSeparator));
        assertThat(plugin.cleanupScript("a\nb"), is("a" + lineSeparator + "b"));
        assertThat(plugin.cleanupScript("a\rb"), is("a" + lineSeparator + "b"));
        assertThat(plugin.cleanupScript("a\r\nb"), is("a" + lineSeparator + "b"));
    }
}