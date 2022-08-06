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
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.transformer.plugin.diagnostic.DiagnosticMessage;

/**
 * Transformer module Code Validator.
 *
 */
public class TransformerCodeValidator implements AnalysisTask<SyntaxNodeAnalysisContext> {

    @Override
    public void perform(SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
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
                            TransformerCodeAnalyzerGenerator.FOUND_EXPR_BODIED_FUNC.set(true);
                            TransformerCodeAnalyzerGenerator.TRANSFORMER_FUNC_NAMES
                                    .add(funcDefNode.functionName().text());
                        }
                    }
                });
                if (syntaxNodeAnalysisContext.currentPackage().module(moduleId).documentIds().size()
                        == TransformerCodeAnalyzerGenerator.VISITED_DEFAULT_MODULE_PARTS.incrementAndGet()
                        && !TransformerCodeAnalyzerGenerator.FOUND_EXPR_BODIED_FUNC.get()) {
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_105);
                }
                break;
            default:
                break;
        }
    }

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
