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
import io.ballerina.projects.plugins.CodeGenerator;
import io.ballerina.projects.plugins.CodeGeneratorContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transformer module Code Analyzer and Generator.
 *
 */
public class TransformerCodeAnalyzerGenerator extends CodeGenerator {

    static final AtomicInteger VISITED_DEFAULT_MODULE_PARTS = new AtomicInteger(0);
    static final AtomicBoolean FOUND_EXPR_BODIED_FUNC = new AtomicBoolean(false);
    static final List<String> TRANSFORMER_FUNC_NAMES = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void init(CodeGeneratorContext codeGeneratorContext) {
        codeGeneratorContext.addSyntaxNodeAnalysisTask(new TransformerCodeValidator(), List.of(
                SyntaxKind.CLASS_DEFINITION,
                SyntaxKind.FUNCTION_DEFINITION,
                SyntaxKind.LISTENER_DECLARATION,
                SyntaxKind.MODULE_PART,
                SyntaxKind.SERVICE_DECLARATION));
        codeGeneratorContext.addSourceGeneratorTask(new TransformerServiceGenerator());
    }
}
