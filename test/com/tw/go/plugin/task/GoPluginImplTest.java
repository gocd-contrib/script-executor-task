package com.tw.go.plugin.task;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

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

    private abstract class GoPluginApiRequestMock extends GoPluginApiRequest {

        @Override
        public String extension() {
            return null;
        }

        @Override
        public String extensionVersion() {
            return null;
        }

        @Override
        public String requestName() {
            return GoPluginImpl.REQUEST_VALIDATION;
        }

        @Override
        public Map<String, String> requestParameters() {
            return null;
        }

        @Override
        public Map<String, String> requestHeaders() {
            return null;
        }
    }

    private Map<String,Object> createResponse(Map<String,Object> errors){
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("errors",errors);
        return expectedResponse;
    }

    @Test
    public void shouldGiveEmptyErrorsWhenScriptAndShellTypeProvided() throws UnhandledRequestTypeException {
        GoPluginApiRequest goPluginApiRequest = new GoPluginApiRequestMock() {
            @Override
            public String requestBody() {
                return "{\"shtype\":{\"secure\":false,\"value\":\"bash\",\"required\":false},\"script\":{\"secure\":false,\"value\":\"ls\",\"required\":false}}";
            }
        };

        GoPluginImpl plugin = new GoPluginImpl();
        Map<String, Object> errors = new HashMap<>();
        Map<String, Object> expectedResponse = createResponse(errors);
        GoPluginApiResponse actualResponse = plugin.handle(goPluginApiRequest);

        assertEquals(actualResponse.responseCode(),GoPluginImpl.SUCCESS_RESPONSE_CODE);
        assertEquals(actualResponse.responseBody(), new GsonBuilder().create().toJson(expectedResponse));
    }

    @Test
    public void shouldGiveShellTypeErrorWhenItisNotProvided() throws UnhandledRequestTypeException {
        GoPluginApiRequest goPluginApiRequest = new GoPluginApiRequestMock() {
            @Override
            public String requestBody() {
                return "{\"shtype\":{\"secure\":false,\"required\":false},\"script\":{\"secure\":false,\"value\":\"ls\",\"required\":false}}";
            }
        };

        GoPluginImpl plugin = new GoPluginImpl();
        Map<String, Object> errors = new HashMap<>();
        errors.put("shtype","Shell type can't be empty");
        Map<String, Object> expectedResponse = createResponse(errors);
        GoPluginApiResponse actualResponse = plugin.handle(goPluginApiRequest);

        assertEquals(actualResponse.responseCode(),GoPluginImpl.SUCCESS_RESPONSE_CODE);
        assertEquals(actualResponse.responseBody(), new GsonBuilder().create().toJson(expectedResponse));
    }

    @Test
    public void shouldGiveScriptErrorWhenItisNotProvided() throws UnhandledRequestTypeException {
        GoPluginApiRequest goPluginApiRequest = new GoPluginApiRequestMock() {
            @Override
            public String requestBody() {
                return "{\"shtype\":{\"secure\":false,\"value\":\"bash\",\"required\":false},\"script\":{\"secure\":false,\"required\":false}}";
            }
        };
        GoPluginImpl plugin = new GoPluginImpl();

        Map<String, Object> errors = new HashMap<>();
        errors.put("script","Script can't be empty");
        Map<String, Object> expectedResponse = createResponse(errors);
        GoPluginApiResponse actualResponse = plugin.handle(goPluginApiRequest);

        assertEquals(actualResponse.responseCode(),GoPluginImpl.SUCCESS_RESPONSE_CODE);
        assertEquals(actualResponse.responseBody(), new GsonBuilder().create().toJson(expectedResponse));
    }
}