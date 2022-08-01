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
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CodeAnalysisContext;
import io.ballerina.projects.plugins.CodeAnalyzer;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.transformer.plugin.diagnostic.DiagnosticMessage;

import java.util.List;

/**
 * Transformer module Code Analyzer.
 *
 */
public class TransformerCodeAnalyzer extends CodeAnalyzer {

    @Override
    public void init(CodeAnalysisContext codeAnalysisContext) {
        codeAnalysisContext.addSyntaxNodeAnalysisTask(modulePartAnalysisTask, List.of(SyntaxKind.CLASS_DEFINITION,
                SyntaxKind.FUNCTION_DEFINITION,
                SyntaxKind.LISTENER_DECLARATION,
                SyntaxKind.MODULE_PART,
                SyntaxKind.SERVICE_DECLARATION));
    }

    private final AnalysisTask<SyntaxNodeAnalysisContext> modulePartAnalysisTask = syntaxNodeAnalysisContext -> {
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
                ModulePartNode modulePartNode =
                        (ModulePartNode) syntaxNodeAnalysisContext.node();
                boolean isPublic = modulePartNode.members().stream().anyMatch(member -> {
                    if (member.kind() == SyntaxKind.FUNCTION_DEFINITION) {
                        FunctionDefinitionNode funcDefNode = (FunctionDefinitionNode) member;
                        return !funcDefNode.qualifierList().isEmpty() &&
                                funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                                        qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                                && funcDefNode.functionBody().kind() == SyntaxKind.EXPRESSION_FUNCTION_BODY;
                    }
                    return false;
                });
                boolean isIsolated = modulePartNode.members().stream().anyMatch(member -> {
                    if (member.kind() == SyntaxKind.FUNCTION_DEFINITION) {
                        FunctionDefinitionNode funcDefNode = (FunctionDefinitionNode) member;
                        return !funcDefNode.qualifierList().isEmpty() &&
                                funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                                        qualifier.kind() == SyntaxKind.ISOLATED_KEYWORD)
                                && funcDefNode.functionBody().kind() == SyntaxKind.EXPRESSION_FUNCTION_BODY;
                    }
                    return false;
                });
                if (!isPublic || !isIsolated) {
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_105);
                }
                break;
            default:
                break;
        }
    };

//    private boolean isAnyQualifierTokenMatch(NodeList<Token> qualifierList, SyntaxKind kind) {
//        return qualifierList.stream().anyMatch(qualifier -> qualifier.kind() == kind);
//    }

    private void reportDiagnostics(SyntaxNodeAnalysisContext syntaxNodeAnalysisContext,
                                   DiagnosticMessage diagnosticMessage, Object... args) {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(diagnosticMessage.getCode(),
                diagnosticMessage.getMessageFormat(), diagnosticMessage.getSeverity());
        Diagnostic diagnostic =
                DiagnosticFactory.createDiagnostic(diagnosticInfo, syntaxNodeAnalysisContext.node().location(), args);
        syntaxNodeAnalysisContext.reportDiagnostic(diagnostic);
    }
}
