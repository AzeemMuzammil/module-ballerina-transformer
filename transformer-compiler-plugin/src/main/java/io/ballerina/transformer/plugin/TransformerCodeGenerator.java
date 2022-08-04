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

import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.projects.plugins.CodeGenerator;
import io.ballerina.projects.plugins.CodeGeneratorContext;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;

//import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Transformer module Code Generator.
 *
 */
public class TransformerCodeGenerator extends CodeGenerator {

    @Override
    public void init(CodeGeneratorContext generatorContext) {
        TypeInfoHolder holder = new TypeInfoHolder();
        generatorContext.addSyntaxNodeAnalysisTask(syntaxNodeAnalysisContext -> {
            TypeDefinitionNode typeDefNode = (TypeDefinitionNode) syntaxNodeAnalysisContext.node();
            String typeName = typeDefNode.typeName().text();
            holder.typeInfoList.add(new TypeInfo(typeName));
        }, SyntaxKind.TYPE_DEFINITION);

//        generatorContext.addSourceGeneratorTask(sourceGeneratorContext -> {
//            sourceGeneratorContext.addResourceFile("".getBytes(Charset.defaultCharset()), "./test.json");
//        });

        generatorContext.addSourceGeneratorTask(sourceGeneratorContext -> {
            for (TypeInfo typeInfo : holder.typeInfoList) {
                TextDocument textDocument = TextDocuments.from("type generated_" +
                        typeInfo.typeName() + " record {};");
                sourceGeneratorContext.addSourceFile(textDocument, "type");
            }

            TextDocument textDocument = TextDocuments
                    .from("public isolated function hi(string firstName) returns string => firstName;");
            sourceGeneratorContext.addSourceFile(textDocument, "type");
        });

//        generatorContext.addSourceGeneratorTask(sourceGeneratorContext -> {
//            for (TypeInfo typeInfo : holder.typeInfoList) {
//                TextDocument textDocument = TextDocuments.from("type generated_test_" +
//                        typeInfo.typeName() + " record {};");
//                sourceGeneratorContext.addTestSourceFile(textDocument, "type");
//            }
//
//            TextDocument textDocument = TextDocuments.from("import ballerina/transformer as _;");
//            sourceGeneratorContext.addTestSourceFile(textDocument, "type");
//        });
    }

    private static class TypeInfoHolder {
        List<TypeInfo> typeInfoList = new ArrayList<>();
    }

    private static class TypeInfo {
        private final String typeName;

        public TypeInfo(String typeName) {
            this.typeName = typeName;
        }

        String typeName() {
            return typeName;
        }
    }
}
