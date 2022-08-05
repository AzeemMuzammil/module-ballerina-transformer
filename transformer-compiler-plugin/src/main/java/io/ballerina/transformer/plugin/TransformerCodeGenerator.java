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

import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CodeGenerator;
import io.ballerina.projects.plugins.CodeGeneratorContext;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;
import io.ballerina.transformer.plugin.diagnostic.DiagnosticMessage;

//import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transformer module Code Generator.
 *
 */
public class TransformerCodeGenerator extends CodeGenerator {

    private static final AtomicInteger visitedDefaultModuleParts = new AtomicInteger(0);
    private static final AtomicBoolean foundExprBodiedFunc = new AtomicBoolean(false);
    private static final List<String> transformerFuncNames = Collections.synchronizedList(new ArrayList<>());


    @Override
    public void init(CodeGeneratorContext generatorContext) {
        generatorContext.addSyntaxNodeAnalysisTask(syntaxNodeAnalysisTask, List.of(SyntaxKind.CLASS_DEFINITION,
                SyntaxKind.FUNCTION_DEFINITION,
                SyntaxKind.LISTENER_DECLARATION,
                SyntaxKind.MODULE_PART,
                SyntaxKind.SERVICE_DECLARATION));

        generatorContext.addSourceGeneratorTask(sourceGeneratorContext -> {
            StringBuilder balServiceCode = new StringBuilder("import ballerina/http;\n" +
                    "\n" +
                    "# A service representing a network-accessible API\n" +
                    "# bound to port `9090`.\n" +
                    "service / on new http:Listener(9090) {\n");
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
        });
    }

    private final AnalysisTask<SyntaxNodeAnalysisContext> syntaxNodeAnalysisTask = syntaxNodeAnalysisContext -> {
        Node node = syntaxNodeAnalysisContext.node();
        SyntaxKind nodeKind = node.kind();

        switch (nodeKind) {
            case FUNCTION_DEFINITION:
                FunctionDefinitionNode functionDefNode = (FunctionDefinitionNode) node;
                if (functionDefNode.functionName().text().equals("main")) {
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_100);
                }
                if (functionDefNode.qualifierList().stream().anyMatch(qualifier ->
                        qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                        && functionDefNode.functionBody().kind() != SyntaxKind.EXPRESSION_FUNCTION_BODY) {
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_101);
                }
                if (functionDefNode.parent() instanceof ModulePartNode
                        && isTestModulePart((ModulePartNode) functionDefNode.parent())) {
                    break;
                }
                functionDefNode.metadata().ifPresent(metadata -> {
                    if (!metadata.annotations().isEmpty()) {
                        reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_106);
                    }
                });
                break;
            case LISTENER_DECLARATION:
                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_102);
                break;
            case CLASS_DEFINITION:
                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_103);
                break;
            case SERVICE_DECLARATION:
                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_104);
                break;
            case MODULE_PART:
                ModuleId moduleId = syntaxNodeAnalysisContext.moduleId();
                if (!isDefaultModule(syntaxNodeAnalysisContext.currentPackage().modules(), moduleId)) {
                    break;
                }

                // Inside default module's module part node
                ModulePartNode modulePartNode =
                        (ModulePartNode) syntaxNodeAnalysisContext.node();
                modulePartNode.members().forEach(member -> {
                    if (member.kind() == SyntaxKind.FUNCTION_DEFINITION) {
                        FunctionDefinitionNode funcDefNode = (FunctionDefinitionNode) member;
                        if (isExprBodyFuncWithPublicIsolatedQualifier(funcDefNode)) {
                            foundExprBodiedFunc.set(true);
                            transformerFuncNames.add(funcDefNode.functionName().text());
                        }
                    }
                });
                if (syntaxNodeAnalysisContext.currentPackage()
                        .module(moduleId).documentIds().size() == visitedDefaultModuleParts.incrementAndGet()
                        && !foundExprBodiedFunc.get()) {
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_105);
                }
                break;
            default:
                break;
        }
    };

    private void reportDiagnostics(SyntaxNodeAnalysisContext syntaxNodeAnalysisContext,
                                   DiagnosticMessage diagnosticMessage, Object... args) {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(diagnosticMessage.getCode(),
                diagnosticMessage.getMessageFormat(), diagnosticMessage.getSeverity());
        Diagnostic diagnostic =
                DiagnosticFactory.createDiagnostic(diagnosticInfo, syntaxNodeAnalysisContext.node().location(), args);
        syntaxNodeAnalysisContext.reportDiagnostic(diagnostic);
    }

    private boolean isDefaultModule(Iterable<Module> modules, ModuleId moduleId) {
        for (Module module : modules) {
            if (module.isDefaultModule() && module.moduleId() == moduleId) {
                return true;
            }
        }
        return false;
    }

    private boolean isTestModulePart(ModulePartNode node) {
        for (ImportDeclarationNode importDecNode : node.imports()) {
            if (importDecNode.moduleName().stream().anyMatch(name -> name.text().equals("test"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isExprBodyFuncWithPublicIsolatedQualifier(FunctionDefinitionNode funcDefNode) {
        return !funcDefNode.qualifierList().isEmpty() &&
                funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                        qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                && funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                && funcDefNode.functionBody().kind() == SyntaxKind.EXPRESSION_FUNCTION_BODY;
    }
}
