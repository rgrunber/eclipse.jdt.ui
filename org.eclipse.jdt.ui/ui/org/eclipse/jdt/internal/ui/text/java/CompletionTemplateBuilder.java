/**
 * Copyright (c) 2010, 2012 Darmstadt University of Technology.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marcel Bruch - initial API and implementation.
 *    Olav Lenz - externalize Strings.
 */
package org.eclipse.jdt.internal.ui.text.java;

import static java.text.MessageFormat.format;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jdt.internal.corext.template.java.JavaContextType;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.DocumentTemplateContext;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.swt.graphics.Image;

/**
 * Creates the templates for a given call chain.
 */
@SuppressWarnings("restriction")
public final class CompletionTemplateBuilder {

    private CompletionTemplateBuilder() {
    }

    public static TemplateProposal create(final Chain chain, final JavaContentAssistInvocationContext context) {
        final String title = createChainCode(chain, true, 0);
        final String body = createChainCode(chain, false, chain.getExpectedDimensions());

        final Template template = new Template(title,
                format("{0,choice,1#1 element|1<{0,number,integer} elements}", chain.getElements().size()), "java", body, false); //$NON-NLS-1$
        return createTemplateProposal(template, context);
    }

    private static String createChainCode(final Chain chain, final boolean createAsTitle, final int expectedDimension) {
        final Map<String, Integer> varNames = new HashMap<> ();
        final StringBuilder sb = new StringBuilder(64);
        for (final ChainElement edge : chain.getElements()) {
            switch (edge.getElementType()) {
            case FIELD:
            case LOCAL_VARIABLE:
                appendVariableString(edge, sb);
                break;
            case METHOD:
                final MethodBinding method = edge.getElementBinding();
                if (createAsTitle) {
                    sb.append(method.readableName());
                } else {
                    sb.append(method.selector);
                    appendParameters(sb, method, varNames);
                }
                break;
            default:
//                log(WARNING_CANNOT_HANDLE_ELEMENT_TYPE, edge);
            }
            final boolean appendVariables = !createAsTitle;
            appendArrayDimensions(sb, edge.getReturnTypeDimension(), expectedDimension, appendVariables, varNames);
            sb.append("."); //$NON-NLS-1$
        }
        deleteLastChar(sb);
        return sb.toString();
    }

    private static void appendVariableString(final ChainElement edge, final StringBuilder sb) {
        if (edge.requiresThisForQualification() && sb.length() == 0) {
            sb.append("this."); //$NON-NLS-1$
        }
        sb.append(((VariableBinding) edge.getElementBinding()).name);
    }

    private static void appendParameters(final StringBuilder sb, final MethodBinding method,
            final Map<String, Integer> varNames) {
        sb.append("("); //$NON-NLS-1$
        for (final TypeBinding parameter : method.parameters) {
            String tmp = String.valueOf(parameter.shortReadableName());
            String parameterName = tmp.substring(0, 1).toLowerCase() + tmp.substring(1);
            int index = parameterName.indexOf("<"); //$NON-NLS-1$
            if (index != -1) {
                parameterName = parameterName.substring(0, index);
            }
            appendTemplateVariable(sb, parameterName, varNames);
            sb.append(", "); //$NON-NLS-1$
        }
        if (method.parameters.length > 0) {
            deleteLastChar(sb);
            deleteLastChar(sb);
        }
        sb.append(")"); //$NON-NLS-1$
    }

    private static void appendTemplateVariable(final StringBuilder sb, final String varname,
            final Map<String, Integer> varNames) {
        int val = varNames.containsKey(varname) ? varNames.get(varname).intValue() : 0;
        varNames.put(varname, val + 1);
        sb.append("${").append(varname); //$NON-NLS-1$
        final int count = varNames.get(varname);
        if (count > 1) {
            sb.append(count);
        }
        sb.append("}"); //$NON-NLS-1$
    }

    private static void appendArrayDimensions(final StringBuilder sb, final int dimension, final int expectedDimension,
            final boolean appendVariables, final Map<String, Integer> varNames) {
        for (int i = dimension; i-- > expectedDimension;) {
            sb.append("["); //$NON-NLS-1$
            if (appendVariables) {
                appendTemplateVariable(sb, "i", varNames); //$NON-NLS-1$
            }
            sb.append("]"); //$NON-NLS-1$
        }
    }

    private static StringBuilder deleteLastChar(final StringBuilder sb) {
        return sb.deleteCharAt(sb.length() - 1);
    }

    static TemplateProposal createTemplateProposal(final Template template,
            final JavaContentAssistInvocationContext contentAssistContext) {
        final DocumentTemplateContext templateContext = createJavaContext(contentAssistContext);
        final Region region = new Region(templateContext.getCompletionOffset(), templateContext.getCompletionLength());
        final TemplateProposal proposal = new TemplateProposal(template, templateContext, region,
                getChainCompletionIcon());
        return proposal;
    }

    static JavaContext createJavaContext(final JavaContentAssistInvocationContext contentAssistContext) {
        final ContextTypeRegistry templateContextRegistry = JavaPlugin.getDefault().getTemplateContextRegistry();
        final TemplateContextType templateContextType = templateContextRegistry.getContextType(JavaContextType.ID_ALL);
        final JavaContext javaTemplateContext = new JavaContext(templateContextType, contentAssistContext.getDocument(),
                contentAssistContext.getInvocationOffset(), contentAssistContext.getCoreContext().getToken().length,
                contentAssistContext.getCompilationUnit());
        javaTemplateContext.setForceEvaluation(true);
        return javaTemplateContext;
    }

    static Image getChainCompletionIcon() {
        return JavaPlugin.getImageDescriptorRegistry().get(JavaPluginImages.DESC_MISC_PUBLIC);
    }
}
