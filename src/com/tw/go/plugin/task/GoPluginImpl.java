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

package com.tw.go.plugin.task;

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
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;

@Extension
public class GoPluginImpl implements GoPlugin {
    public final static String EXTENSION_NAME = "task";
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
        Boolean isWindows = isWindows();
        try {
            Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);

            Map<String, Object> configKeyValuePairs = (Map<String, Object>) map.get("config");
            Map<String, Object> context = (Map<String, Object>) map.get("context");
            workingDirectory = (String) context.get("workingDirectory");
            Map<String, String> environmentVariables = (Map<String, String>) context.get("environmentVariables");

            Map<String, String> scriptConfig = (Map<String, String>) configKeyValuePairs.get("script");
            String scriptValue = scriptConfig.get("value");

            scriptFileName = generateScriptFileName(isWindows);

            createScript(workingDirectory, scriptFileName, isWindows, scriptValue);

            int exitCode = executeScript(workingDirectory, scriptFileName, isWindows, environmentVariables);

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

    private boolean isWindows() {
        String osName = System.getProperty("os.name");
        boolean isWindows = StringUtils.containsIgnoreCase(osName, "windows");
        JobConsoleLogger.getConsoleLogger().printLine("[script-executor] OS detected: '" + osName + "'. Is Windows? " + isWindows);
        return isWindows;
    }

    private String generateScriptFileName(Boolean isWindows) {
        return UUID.randomUUID() + (isWindows ? ".bat" : ".sh");
    }

    private String getScriptPath(String workingDirectory, String scriptFileName) {
        return workingDirectory + "/" + scriptFileName;
    }

    private void createScript(String workingDirectory, String scriptFileName, Boolean isWindows, String scriptValue) throws IOException, InterruptedException {
        File file = new File(getScriptPath(workingDirectory, scriptFileName));
        FileUtils.writeStringToFile(file, scriptValue);

        if (!isWindows) {
            executeCommand(workingDirectory, null, "chmod", "u+x", scriptFileName);
        }

        JobConsoleLogger.getConsoleLogger().printLine("[script-executor] Script written into '" + file.getAbsolutePath() + "'.");
    }

    private int executeScript(String workingDirectory, String scriptFileName, Boolean isWindows, Map<String, String> environmentVariables) throws IOException, InterruptedException {
        if (isWindows) {
            return executeCommand(workingDirectory, environmentVariables, "cmd", "/c", scriptFileName);
        }
        return executeCommand(workingDirectory, environmentVariables, "/bin/sh", "-c", "./" + scriptFileName);
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
        if (!StringUtils.isEmpty(scriptFileName)) {
            FileUtils.deleteQuietly(new File(getScriptPath(workingDirectory, scriptFileName)));
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
