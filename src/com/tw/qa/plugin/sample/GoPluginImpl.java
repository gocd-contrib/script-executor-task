/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.tw.qa.plugin.sample;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;

@Extension
public class GoPluginImpl implements GoPlugin {
    public final static String EXTENSION_NAME = "task-extension";
    public final static List<String> SUPPORTED_API_VERSIONS = asList("1.0");

    public final static String REQUEST_CONFIGURATION = "configuration";
    public final static String REQUEST_VALIDATION = "validate";
    public final static String REQUEST_TASK_VIEW = "view";
    public final static String REQUEST_EXECUTION = "execute";

    public static final int SUCCESS_RESPONSE_CODE = 200;

    private static Logger LOGGER = Logger.getLoggerFor(GoPluginImpl.class);

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        // ignore
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) throws UnhandledRequestTypeException {
        if (goPluginApiRequest.requestName().equals(REQUEST_CONFIGURATION)) {
            return handleConfiguration();
        } else if (goPluginApiRequest.requestName().equals(REQUEST_VALIDATION)) {
            return handleValidation(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_TASK_VIEW)) {
            try {
                return handleView();
            } catch (IOException e) {
                String message = "Failed to find template: " + e.getMessage();
                return renderJSON(500, message);
            }
        } else if (goPluginApiRequest.requestName().equals(REQUEST_EXECUTION)) {
            return handleExecute(goPluginApiRequest);
        }
        return null;
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, SUPPORTED_API_VERSIONS);
    }

    private GoPluginApiResponse handleConfiguration() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("script", createField("Script", null, true, false, "0"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleValidation(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> response = new HashMap<String, Object>();
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleView() throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("displayValue", "Script Executor");
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/views/task.template.html"), "UTF-8"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleExecute(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> response = new HashMap<String, Object>();
        String workingDirectory = null;
        String scriptFileName = null;
        try {
            Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);

            Map<String, Object> configKeyValuePairs = (Map<String, Object>) map.get("config");
            Map<String, Object> context = (Map<String, Object>) map.get("context");
            workingDirectory = (String) context.get("workingDirectory");
            Map<String, String> environmentVariables = (Map<String, String>) context.get("environmentVariables");

            Map<String, String> scriptConfig = (Map<String, String>) configKeyValuePairs.get("script");
            String scriptValue = scriptConfig.get("value");

            scriptFileName = UUID.randomUUID() + ".sh";

            createShellScript(workingDirectory, scriptFileName, scriptValue);

            int exitCode = executeShellScript(workingDirectory, environmentVariables, scriptFileName);

            if (exitCode == 0) {
                response.put("success", true);
                response.put("message", "Script completed successfully.");
            } else {
                response.put("success", false);
                response.put("message", "Script completed with exit code: " + exitCode + ".");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        deleteShellScript(workingDirectory, scriptFileName);
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private void createShellScript(String workingDirectory, String scriptFileName, String scriptValue) throws IOException, InterruptedException {
        File file = new File(workingDirectory + "/" + scriptFileName);
        FileUtils.writeStringToFile(file, scriptValue);

        executeCommand(workingDirectory, new HashMap<String, String>(), "chmod", "u+x", scriptFileName);

        JobConsoleLogger.getConsoleLogger().printLine("Script written into '" + file.getAbsolutePath() + "'.");
    }

    private int executeShellScript(String workingDirectory, Map<String, String> environmentVariables, String scriptFileName) throws IOException, InterruptedException {
        return executeCommand(workingDirectory, environmentVariables, "/bin/sh", "-c", "./" + scriptFileName);
    }

    private int executeCommand(String workingDirectory, Map<String, String> environmentVariables, String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(workingDirectory));
        processBuilder.environment().putAll(environmentVariables);
        Process process = processBuilder.start();

        JobConsoleLogger.getConsoleLogger().readOutputOf(process.getInputStream());
        JobConsoleLogger.getConsoleLogger().readErrorOf(process.getErrorStream());

        return process.waitFor();
    }

    private void deleteShellScript(String workingDirectory, String scriptFileName) {
        if (scriptFileName != null && !scriptFileName.trim().isEmpty()) {
            FileUtils.deleteQuietly(new File(workingDirectory + "/" + scriptFileName));
        }
    }

    private Map<String, Object> createField(String displayName, String defaultValue, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<String, Object>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    private Map<String, String> keyValuePairs(String configMapStr) {
        Map<String, String> keyValuePairs = new HashMap<String, String>();
        Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(configMapStr, Object.class);
        for (String field : map.keySet()) {
            Map<String, Object> fieldProperties = (Map<String, Object>) map.get(field);
            String value = (String) fieldProperties.get("value");
            keyValuePairs.put(field, value);
        }
        return keyValuePairs;
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }
}
