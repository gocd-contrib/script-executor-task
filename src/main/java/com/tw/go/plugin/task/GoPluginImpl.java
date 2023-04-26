/*************************GO-LICENSE-START*********************************
 * Copyright 2022 Thoughtworks, Inc.
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

package com.tw.go.plugin.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


@Extension
public class GoPluginImpl implements GoPlugin {
    public final static String EXTENSION_NAME = "task";
    public final static List<String> SUPPORTED_API_VERSIONS = List.of("1.0");

    public final static String REQUEST_CONFIGURATION = "configuration";
    public final static String REQUEST_CONFIGURATION_2 = "go.plugin-settings.get-configuration";
    public final static String REQUEST_VALIDATION = "validate";
    public final static String REQUEST_VALIDATION_2 = "go.plugin-settings.validate-configuration";
    public final static String REQUEST_TASK_VIEW = "view";
    public final static String REQUEST_TASK_VIEW_2 = "go.plugin-settings.get-view";
    public final static String REQUEST_EXECUTION = "execute";

    public static final int SUCCESS_RESPONSE_CODE = 200;
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        // ignore
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        if (goPluginApiRequest.requestName().equals(REQUEST_CONFIGURATION) || goPluginApiRequest.requestName().equals(REQUEST_CONFIGURATION_2)) {
            return handleConfiguration();
        } else if (goPluginApiRequest.requestName().equals(REQUEST_VALIDATION) || goPluginApiRequest.requestName().equals(REQUEST_VALIDATION_2)) {
            return handleValidation();
        } else if (goPluginApiRequest.requestName().equals(REQUEST_TASK_VIEW) || goPluginApiRequest.requestName().equals(REQUEST_TASK_VIEW_2)) {
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
        Map<String, Object> response = new HashMap<>();
        response.put("script", createField("Script", null, "0"));
        response.put("shtype", createField("Shell", "bash", "1"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleValidation() {
        Map<String, Object> response = new HashMap<>();
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleView() throws IOException {
        try (InputStream resource = getClass().getResourceAsStream("/views/task.template.html")) {
            Map<String, Object> response = new HashMap<>();
            response.put("displayValue", "Script Executor");
            response.put("template", new String(Objects.requireNonNull(resource).readAllBytes(), StandardCharsets.UTF_8));
            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        }
    }

    @SuppressWarnings("unchecked")
    private GoPluginApiResponse handleExecute(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> response = new HashMap<>();
        String workingDirectory = null;
        String scriptFileName = null;
        try {
            Map<String, Object> map = (Map<String, Object>) GSON.fromJson(goPluginApiRequest.requestBody(), Object.class);

            Map<String, Object> configKeyValuePairs = (Map<String, Object>) map.get("config");
            Map<String, Object> context = (Map<String, Object>) map.get("context");
            workingDirectory = (String) context.get("workingDirectory");
            Map<String, String> environmentVariables = (Map<String, String>) context.get("environmentVariables");

            Map<String, String> scriptConfig = (Map<String, String>) configKeyValuePairs.get("script");
            String scriptValue = scriptConfig.get("value");
            JobConsoleLogger.getConsoleLogger().printLine(scriptValue);
            Map<String, String> shTypeConfig = (Map<String, String>) configKeyValuePairs.get("shtype");
            String shType = shTypeConfig.get("value");
            if (shType == null || shType.trim().equals("")) {
                shType = "bash";
            }

            scriptFileName = UUID.randomUUID() + ".sh";

            createScript(workingDirectory, scriptFileName, scriptValue);

            int exitCode = executeScript(workingDirectory, shType, scriptFileName, environmentVariables);

            if (exitCode == 0) {
                response.put("success", true);
                response.put("message", "[script-executor] Script completed successfully.");
            } else {
                response.put("success", false);
                response.put("message", "[script-executor] Script completed with exit code: " + exitCode + ".");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "[script-executor] Script execution interrupted. Reason: " + e.getMessage());
        }
        deleteScript(workingDirectory, scriptFileName);
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private Path getScriptPath(String workingDirectory, String scriptFileName) {
        return Paths.get(workingDirectory, scriptFileName);
    }

    private void createScript(String workingDirectory, String scriptFileName, String scriptValue) throws IOException, InterruptedException {
        Path scriptPath = getScriptPath(workingDirectory, scriptFileName);
        Files.writeString(scriptPath, cleanupScript(scriptValue), StandardCharsets.UTF_8);

        executeCommand(workingDirectory, null, "chmod", "u+x", scriptFileName);
    }

    String cleanupScript(String scriptValue) {
        return scriptValue.replaceAll("(\\r\\n|\\n|\\r)", System.getProperty("line.separator"));
    }

    private int executeScript(String workingDirectory, String shType, String scriptFileName, Map<String, String> environmentVariables) throws IOException, InterruptedException {
        return executeCommand(workingDirectory, environmentVariables, "/bin/bash", "-euo", "pipefail", "-c", "./" + scriptFileName);
    }

    private int executeCommand(String workingDirectory, Map<String, String> environmentVariables, String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(workingDirectory));
        if (environmentVariables != null && !environmentVariables.isEmpty()) {
            processBuilder.environment().putAll(environmentVariables);
        }
        Process process = processBuilder.start();

        JobConsoleLogger.getConsoleLogger().readOutputOf(process.getInputStream());
        JobConsoleLogger.getConsoleLogger().readErrorOf(process.getErrorStream());

        return process.waitFor();
    }

    private void deleteScript(String workingDirectory, String scriptFileName) {
        if (scriptFileName != null && !scriptFileName.isEmpty()) {
            try {
                Files.deleteIfExists(getScriptPath(workingDirectory, scriptFileName));
            } catch (IOException ignore) {
            }
        }
    }

    private Map<String, Object> createField(String displayName, String defaultValue, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("required", true);
        fieldProperties.put("secure", false);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : GSON.toJson(response);
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
