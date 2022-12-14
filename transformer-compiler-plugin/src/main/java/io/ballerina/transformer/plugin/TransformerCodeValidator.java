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

import io.ballerina.compiler.syntax.tree.ArrayTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.DefaultableParameterNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.MapTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.RestParameterNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TableTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypeParameterNode;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.transformer.plugin.diagnostic.DiagnosticMessage;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transformer module Code Validator.
 *
 */
public class TransformerCodeValidator implements AnalysisTask<SyntaxNodeAnalysisContext> {

    private final AtomicInteger visitedDefaultModulePart;
    private final AtomicBoolean foundTransformerFunc;
    private final List<FunctionDefinitionNode> transformerFunctions;

    private final List<SyntaxKind> httpSupportedTypes = List.of(
            SyntaxKind.BOOLEAN_TYPE_DESC,
            SyntaxKind.INT_TYPE_DESC,
            SyntaxKind.FLOAT_TYPE_DESC,
            SyntaxKind.DECIMAL_TYPE_DESC,
            SyntaxKind.BYTE_TYPE_DESC,
            SyntaxKind.STRING_TYPE_DESC,
            SyntaxKind.JSON_TYPE_DESC,
            SyntaxKind.MAP_TYPE_DESC,
            SyntaxKind.RECORD_TYPE_DESC);

    TransformerCodeValidator(AtomicInteger visitedDefaultModulePart, AtomicBoolean foundTransformerFunc,
                             List<FunctionDefinitionNode> transformerFunctions) {
        this.visitedDefaultModulePart = visitedDefaultModulePart;
        this.foundTransformerFunc = foundTransformerFunc;
        this.transformerFunctions = transformerFunctions;
    }

    @Override
    public void perform(SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        ModulePartNode modulePartNode = (ModulePartNode) syntaxNodeAnalysisContext.node();
//        SyntaxKind nodeKind = node.kind();
        DocumentId documentId = syntaxNodeAnalysisContext.documentId();
        ModuleId moduleId = syntaxNodeAnalysisContext.moduleId();

        // Exclude Test related files from transformer validation
        for (DocumentId testDocId : syntaxNodeAnalysisContext.currentPackage().module(moduleId).testDocumentIds()) {
            if (documentId.equals(testDocId)) {
                return;
            }
        }

        // Analyze each node within each ModulePart nodes
        modulePartNode.members().forEach(member -> {
            SyntaxKind nodeKind = member.kind();

            switch (nodeKind) {
                case FUNCTION_DEFINITION:
                    FunctionDefinitionNode functionDefNode = (FunctionDefinitionNode) member;
                    if (functionDefNode.functionName().text().equals("main")) {
                        reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_100);
                    }
                    if (functionDefNode.qualifierList().stream().anyMatch(qualifier ->
                            qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                            && functionDefNode.functionBody().kind() != SyntaxKind.EXPRESSION_FUNCTION_BODY) {
                        reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_101);
                    }
                    functionDefNode.metadata().ifPresent(metadata -> {
                        if (!metadata.annotations().isEmpty()) {
                            reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_106);
                        }
                    });
                    if (isDefaultModule(syntaxNodeAnalysisContext.currentPackage().modules(), moduleId)) {
//                        if (isTransformerFunc(functionDefNode) && isServiceGenerableFunc(functionDefNode)) {
//                            foundTransformerFunc.set(true);
//                            transformerFunctions.add(functionDefNode);
//                        } else {
//                            reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_107);
//                        }
                        if (isTransformerFunc(functionDefNode)) {
                            foundTransformerFunc.set(true);
                            transformerFunctions.add(functionDefNode);
                            if (!isServiceGenerableFunc(functionDefNode, syntaxNodeAnalysisContext)) {
                                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_107);
                            }
                        }
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
                default:
                    break;
            }
        });

        // Check if all ModulePart nodes within default package is visited to report diagnostics
        if (isDefaultModule(syntaxNodeAnalysisContext.currentPackage().modules(), moduleId)) {
            if (syntaxNodeAnalysisContext.currentPackage().module(moduleId).documentIds().size()
                    == visitedDefaultModulePart.incrementAndGet()
                    && !foundTransformerFunc.get()) {
                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_105);
            }
        }
    }

    // Change location of the error based on the context.
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

    private boolean isTransformerFunc(FunctionDefinitionNode funcDefNode) {
        return !funcDefNode.qualifierList().isEmpty() &&
                funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                        qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                && funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                && funcDefNode.functionBody().kind() == SyntaxKind.EXPRESSION_FUNCTION_BODY;
    }

    private boolean isServiceGenerableFunc(FunctionDefinitionNode funcDefNode, SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        AtomicBoolean foundSupportedType = new AtomicBoolean(false);
        AtomicBoolean foundUnsupportedType = new AtomicBoolean(false);
        funcDefNode.functionSignature().parameters().forEach(param -> {
            if (param.kind().equals(SyntaxKind.REQUIRED_PARAM)) {
                RequiredParameterNode requiredParamNode = (RequiredParameterNode) param;
                if (requiredParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) requiredParamNode.typeName())
                            .memberTypeDesc().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (requiredParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) requiredParamNode.typeName())
                            .mapTypeParamsNode().typeNode().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (requiredParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
                    if (((TypeParameterNode) ((TableTypeDescriptorNode) requiredParamNode.typeName())
                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                                ((TableTypeDescriptorNode) requiredParamNode.typeName()).rowTypeParameterNode())
                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
                            foundSupportedType.set(true);
                        }
                    }
                } else if (httpSupportedTypes.contains(requiredParamNode.typeName().kind())) {
                    foundSupportedType.set(true);
                } else {

                    foundUnsupportedType.set(true);
                }
            } else if (param.kind().equals(SyntaxKind.DEFAULTABLE_PARAM)) {
                DefaultableParameterNode defaultableParamNode = (DefaultableParameterNode) param;
                if (defaultableParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) defaultableParamNode.typeName())
                            .memberTypeDesc().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (defaultableParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) defaultableParamNode.typeName())
                            .mapTypeParamsNode().typeNode().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (defaultableParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
                    if (((TypeParameterNode) ((TableTypeDescriptorNode) defaultableParamNode.typeName())
                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                                ((TableTypeDescriptorNode) defaultableParamNode.typeName()).rowTypeParameterNode())
                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
                            foundSupportedType.set(true);
                        }
                    }
                } else if (httpSupportedTypes.contains(defaultableParamNode.typeName().kind())) {
                    foundSupportedType.set(true);
                } else {
                    foundUnsupportedType.set(true);
                }
            } else if (param.kind().equals(SyntaxKind.REST_PARAM)) {
                RestParameterNode restParamNode = (RestParameterNode) param;
                if (restParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) restParamNode.typeName())
                            .memberTypeDesc().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (restParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) restParamNode.typeName())
                            .mapTypeParamsNode().typeNode().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (restParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
                    if (((TypeParameterNode) ((TableTypeDescriptorNode) restParamNode.typeName())
                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                                ((TableTypeDescriptorNode) restParamNode.typeName()).rowTypeParameterNode())
                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
                            foundSupportedType.set(true);
                        }
                    }
                } else if (httpSupportedTypes.contains(restParamNode.typeName().kind())) {
                    foundSupportedType.set(true);
                } else {
                    foundUnsupportedType.set(true);
                }
            }
        });
        return foundSupportedType.get() && !foundUnsupportedType.get();
    }

//    private boolean isReturnTypeSupported(FunctionDefinitionNode funcDefNode) {
//        AtomicBoolean isSupportedType = new AtomicBoolean(false);
//        funcDefNode.functionSignature().returnTypeDesc().orElse(null).
//        funcDefNode.functionSignature().parameters().forEach(param -> {
//            if (param.kind().equals(SyntaxKind.REQUIRED_PARAM)) {
//                RequiredParameterNode requiredParamNode = (RequiredParameterNode) param;
//                if (requiredParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
//                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) requiredParamNode.typeName())
//                            .memberTypeDesc().kind())) {
//                        isSupportedType.set(true);
//                    }
//                } else if (requiredParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
//                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) requiredParamNode.typeName())
//                            .mapTypeParamsNode().typeNode().kind())) {
//                        isSupportedType.set(true);
//                    }
//                } else if (requiredParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
//                    if (((TypeParameterNode) ((TableTypeDescriptorNode) requiredParamNode.typeName())
//                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
//                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
//                                ((TableTypeDescriptorNode) requiredParamNode.typeName()).rowTypeParameterNode())
//                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
//                            isSupportedType.set(true);
//                        }
//                    }
//                } else if (httpSupportedTypes.contains(requiredParamNode.typeName().kind())) {
//                    isSupportedType.set(true);
//                }
//            } else if (param.kind().equals(SyntaxKind.DEFAULTABLE_PARAM)) {
//                DefaultableParameterNode defaultableParamNode = (DefaultableParameterNode) param;
//                if (defaultableParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
//                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) defaultableParamNode.typeName())
//                            .memberTypeDesc().kind())) {
//                        isSupportedType.set(true);
//                    }
//                } else if (defaultableParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
//                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) defaultableParamNode.typeName())
//                            .mapTypeParamsNode().typeNode().kind())) {
//                        isSupportedType.set(true);
//                    }
//                } else if (defaultableParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
//                    if (((TypeParameterNode) ((TableTypeDescriptorNode) defaultableParamNode.typeName())
//                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
//                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
//                                ((TableTypeDescriptorNode) defaultableParamNode.typeName()).rowTypeParameterNode())
//                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
//                            isSupportedType.set(true);
//                        }
//                    }
//                } else if (httpSupportedTypes.contains(defaultableParamNode.typeName().kind())) {
//                    isSupportedType.set(true);
//                }
//            } else if (param.kind().equals(SyntaxKind.REST_PARAM)) {
//                RestParameterNode restParamNode = (RestParameterNode) param;
//                if (restParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
//                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) restParamNode.typeName())
//                            .memberTypeDesc().kind())) {
//                        isSupportedType.set(true);
//                    }
//                } else if (restParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
//                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) restParamNode.typeName())
//                            .mapTypeParamsNode().typeNode().kind())) {
//                        isSupportedType.set(true);
//                    }
//                } else if (restParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
//                    if (((TypeParameterNode) ((TableTypeDescriptorNode) restParamNode.typeName())
//                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
//                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
//                                ((TableTypeDescriptorNode) restParamNode.typeName()).rowTypeParameterNode())
//                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
//                            isSupportedType.set(true);
//                        }
//                    }
//                } else if (httpSupportedTypes.contains(restParamNode.typeName().kind())) {
//                    isSupportedType.set(true);
//                }
//            }
//        });
//        return isSupportedType.get();
//    }
}
