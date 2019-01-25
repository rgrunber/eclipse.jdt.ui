/**
 * Copyright (c) 2010, 2011 Darmstadt University of Technology.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marcel Bruch - initial API and implementation.
 */
package org.eclipse.jdt.internal.ui.text.java;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.osgi.util.NLS;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.JavaManipulation;

import org.eclipse.jdt.internal.codeassist.InternalCompletionContext;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnMemberAccess;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnMessageSend;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnQualifiedAllocationExpression;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnQualifiedNameReference;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnSingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.InvocationSite;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
import org.eclipse.jdt.internal.compiler.util.ObjectVector;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

import org.eclipse.ui.progress.UIJob;

public class ChainCompletionProposalComputer implements IJavaCompletionProposalComputer {

    public static final String CATEGORY_ID = "org.eclipse.recommenders.chain.rcp.proposalCategory.chain"; //$NON-NLS-1$

    private JavaContentAssistInvocationContext ctx;
    private List<ChainElement> entrypoints;
    private String error;
    private Scope scope;
    private InvocationSite invocationSite;

    @Override
    public List<ICompletionProposal> computeCompletionProposals(final ContentAssistInvocationContext context,
            final IProgressMonitor monitor) {
        if (!shouldMakeProposals()) {
            return Collections.emptyList();
        }
        if (!initializeRequiredContext(context)) {
            return Collections.emptyList();
        }
        if (!shouldPerformCompletionOnExpectedType()) {
            return Collections.emptyList();
        }
        if (!findEntrypoints()) {
            return Collections.emptyList();
        }
        return executeCallChainSearch();
    }

    /**
     * Ensures that we only make recommendations if we are not on the default tab. Disables this engine if the user has
     * activated chain completion on default content assist list
     */
    protected boolean shouldMakeProposals() {
        final List<String> excluded = new LinkedList<>(Arrays.asList(PreferenceConstants.getExcludedCompletionProposalCategories()));
        if (excluded.contains(CATEGORY_ID)) {
            // we are excluded on default tab? Then we are not on default tab NOW. We are on a subsequent tab and should
            // make completions:
            return true;
        }
        // disable and stop computing.
        UIJob job = new UIJob(CATEGORY_ID) {
            @Override
            public IStatus runInUIThread(IProgressMonitor monitor) {
                excluded.add(getName());
                String[] newExcluded = excluded.toArray(new String [0]);
                PreferenceConstants.setExcludedCompletionProposalCategories(newExcluded);
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule(300);
        return false;
    }

    private boolean initializeRequiredContext(final ContentAssistInvocationContext context) {
        if (!(context instanceof JavaContentAssistInvocationContext)) {
            return false;
        }

        ctx = (JavaContentAssistInvocationContext) context;
        CompletionProposalCollector collector = new CompletionProposalCollector(ctx.getCompilationUnit());
        collector.setInvocationContext(ctx);
        ICompilationUnit cu = ctx.getCompilationUnit();
        int offset = ctx.getInvocationOffset();
        try {
            cu.codeComplete(offset, collector, new NullProgressMonitor());
        } catch (JavaModelException e) {
            e.printStackTrace();
        }

        final Scope optionalScope = ScopeAccessWorkaround.resolveScope(ctx);
        if (optionalScope == null) {
            return false;
        }
        scope = optionalScope;
        return true;
    }

    private boolean shouldPerformCompletionOnExpectedType() {
        final IType expected = ctx.getExpectedType();
        return expected != null;
//        return expected != null || !ctx.getExpectedTypeNames().isEmpty();
    }

    private boolean findEntrypoints() {
        entrypoints = new LinkedList<ChainElement>();
        ASTNode node;
		if (ctx.getCoreContext() instanceof InternalCompletionContext) {
			node = ((InternalCompletionContext) ctx.getCoreContext()).getCompletionNode();
		} else {
			node = null;
		}
        if (node instanceof CompletionOnQualifiedNameReference) {
            invocationSite = (CompletionOnQualifiedNameReference) node;
            findEntrypointsForCompletionOnQualifiedName((CompletionOnQualifiedNameReference) node);
        } else if (node instanceof CompletionOnMemberAccess) {
            invocationSite = (CompletionOnMemberAccess) node;
            findEntrypointsForCompletionOnMemberAccess((CompletionOnMemberAccess) node);
        } else if (node instanceof CompletionOnSingleNameReference
                || node instanceof CompletionOnQualifiedAllocationExpression
                || node instanceof CompletionOnMessageSend) {
            invocationSite = (InvocationSite) node;
            findEntrypointsForCompletionOnSingleName();
        }
        return !entrypoints.isEmpty();
    }

    private void findEntrypointsForCompletionOnQualifiedName(final CompletionOnQualifiedNameReference node) {
        final Binding b = node.binding;
        if (b == null) {
            return;
        }
        switch (b.kind()) {
        case Binding.TYPE:
            addPublicStaticMembersToEntrypoints((TypeBinding) b);
            break;
        case Binding.FIELD:
            addPublicInstanceMembersToEntrypoints(((FieldBinding) b).type);
            break;
        case Binding.LOCAL:
            addPublicInstanceMembersToEntrypoints(((VariableBinding) b).type);
            break;
        default:
            JavaPlugin.logErrorMessage(NLS.bind("Cannot handle '{0}' as source for finding entry points.", b));
        }
    }

    private void addPublicStaticMembersToEntrypoints(final TypeBinding type) {
        for (final Binding m : TypeBindingAnalyzer.findAllPublicStaticFieldsAndNonVoidNonPrimitiveStaticMethods(type, invocationSite,
                scope)) {
            if (matchesExpectedPrefix(m)) {
                entrypoints.add(new ChainElement(m, false));
            }
        }
    }

    private void addPublicInstanceMembersToEntrypoints(final TypeBinding type) {
        for (final Binding m : TypeBindingAnalyzer.findVisibleInstanceFieldsAndRelevantInstanceMethods(type, invocationSite, scope)) {
            if (matchesExpectedPrefix(m)) {
                entrypoints.add(new ChainElement(m, false));
            }
        }
    }

    private boolean matchesExpectedPrefix(final Binding binding) {
        String prefix = String.valueOf(ctx.getCoreContext().getToken());
        return String.valueOf(binding.readableName()).startsWith(prefix);
    }

    private void findEntrypointsForCompletionOnMemberAccess(final CompletionOnMemberAccess node) {
        final TypeBinding b = node.actualReceiverType;
        if (b == null) {
            return;
        }
        addPublicInstanceMembersToEntrypoints(b);
    }

    private void findEntrypointsForCompletionOnSingleName() {
        if (ctx.getCoreContext() instanceof InternalCompletionContext) {
            InternalCompletionContext context= (InternalCompletionContext) ctx.getCoreContext();
            ObjectVector visibleLocalVariables= context.getVisibleLocalVariables();
            Set<String> localVariableNames= getLocalVariableNames(visibleLocalVariables);
            resolveEntrypoints(visibleLocalVariables, localVariableNames);
            resolveEntrypoints(context.getVisibleFields(), localVariableNames);
            resolveEntrypoints(context.getVisibleMethods(), localVariableNames);
        }
    }

    private static Set<String> getLocalVariableNames(final ObjectVector visibleLocalVariables) {
        final Set<String> names = new HashSet<>();
        for (int i = visibleLocalVariables.size(); i-- > 0;) {
            final LocalVariableBinding decl = (LocalVariableBinding) visibleLocalVariables.elementAt(i);
            names.add(Arrays.toString(decl.name));
        }
        return names;
    }

    private void resolveEntrypoints(final ObjectVector elements, final Set<String> localVariableNames) {
        for (int i = elements.size(); i-- > 0;) {
            final Binding decl = (Binding) elements.elementAt(i);
            if (!matchesPrefixToken(decl)) {
                continue;
            }
            final String key = String.valueOf(decl.computeUniqueKey());
            if (key.startsWith("Ljava/lang/Object;")) { //$NON-NLS-1$
                continue;
            }
            boolean requiresThis = false;
            if (decl instanceof FieldBinding) {
                requiresThis = localVariableNames.contains(Arrays.toString(((FieldBinding) decl).name));
            }
            final ChainElement e = new ChainElement(decl, requiresThis);
            if (e.getReturnType() != null) {
                entrypoints.add(e);
            }
        }
    }

    private boolean matchesPrefixToken(final Binding decl) {
        String prefix = String.valueOf(ctx.getCoreContext().getToken());
        return String.valueOf(decl.readableName()).startsWith(prefix);
    }

    private List<ICompletionProposal> executeCallChainSearch() {
        final int maxChains = Integer.parseInt(JavaManipulation.getPreference(PreferenceConstants.PREF_MAX_CHAINS, ctx.getProject()));
        final int minDepth = Integer.parseInt(JavaManipulation.getPreference(PreferenceConstants.PREF_MIN_CHAIN_LENGTH, ctx.getProject()));
        final int maxDepth = Integer.parseInt(JavaManipulation.getPreference(PreferenceConstants.PREF_MAX_CHAIN_LENGTH, ctx.getProject()));
        final String[] excludedTypes = JavaManipulation.getPreference(PreferenceConstants.PREF_IGNORED_TYPES, ctx.getProject()).split("\\|"); //$NON-NLS-1$
        for (int i = 0; i < excludedTypes.length; ++i) {
            excludedTypes[i] = "L" + excludedTypes[i].replace('.', '/'); //$NON-NLS-1$
        }

        final List<TypeBinding> expectedTypes = TypeBindingAnalyzer.resolveBindingsForExpectedTypes(ctx,
                scope);
        final ChainFinder finder = new ChainFinder(expectedTypes, Arrays.asList(excludedTypes), invocationSite,
                scope);
        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> finder.startChainSearch(entrypoints, maxChains, minDepth, maxDepth));
            long timeout = Long.parseLong(JavaManipulation.getPreference(PreferenceConstants.PREF_TIMEOUT, ctx.getProject()));
            future.get(timeout, TimeUnit.SECONDS);
        } catch (final Exception e) {
            setError("Timeout during call chain computation."); //$NON-NLS-1$
        }
        return buildCompletionProposals(finder.getChains());
    }

    private List<ICompletionProposal> buildCompletionProposals(final List<Chain> chains) {
        final List<ICompletionProposal> proposals = new LinkedList<>();
        for (final Chain chain : chains) {
            final TemplateProposal proposal = CompletionTemplateBuilder.create(chain, ctx);
            final ChainCompletionProposal completionProposal = new ChainCompletionProposal(proposal, chain);
            proposals.add(completionProposal);
        }
        return proposals;
    }

    private void setError(final String errorMessage) {
        error = errorMessage;
    }

    @Override
    public List<IContextInformation> computeContextInformation(final ContentAssistInvocationContext context,
            final IProgressMonitor monitor) {
        return Collections.emptyList();
    }

    @Override
    public void sessionStarted() {
        setError(null);
    }

    @Override
    public String getErrorMessage() {
        return error;
    }

    @Override
    public void sessionEnded() {
    }
}
