/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.transformer.plugin;

import io.ballerina.projects.plugins.GeneratorTask;
import io.ballerina.projects.plugins.SourceGeneratorContext;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;

import java.util.List;

/**
 * Transformer module Service Generator.
 *
 */
public class TransformerServiceGenerator implements GeneratorTask<SourceGeneratorContext> {
    private final List<String> transformerFuncNames;

    TransformerServiceGenerator(List<String> transformerFuncNames) {
        this.transformerFuncNames = transformerFuncNames;
    }

    @Override
    public void generate(SourceGeneratorContext sourceGeneratorContext) {
        // TODO: Change the Listener Port to be configurable in Ballerina.toml
        StringBuilder balServiceCode = new StringBuilder("import ballerina/http;\n" +
                "\n" +
                "# A service representing a network-accessible API\n" +
                "# bound to default port `8080`.\n\n" +
                "configurable int port = 8080;\n\n" +
                "service / on new http:Listener(port) {\n");
        for (String transformerFuncName : transformerFuncNames) {
            String service = String.format(
                    "    resource function post %s(@http:Payload json payload) returns json|error {\n" +
                            "        return %s(check payload.cloneWithType()).toJson();\n" +
                            "    }\n\n", transformerFuncName, transformerFuncName);
            balServiceCode.append(service);
        }
        balServiceCode.append("}\n");

        TextDocument textDocument = TextDocuments.from(balServiceCode.toString());
        sourceGeneratorContext.addSourceFile(textDocument, "service");
    }
}
