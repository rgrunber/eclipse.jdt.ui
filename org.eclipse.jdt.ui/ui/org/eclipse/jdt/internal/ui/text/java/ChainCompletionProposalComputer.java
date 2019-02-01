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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

import org.eclipse.ui.progress.UIJob;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.manipulation.JavaManipulation;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;

public class ChainCompletionProposalComputer implements IJavaCompletionProposalComputer {

    public static final String CATEGORY_ID = "org.eclipse.recommenders.chain.rcp.proposalCategory.chain"; //$NON-NLS-1$

    private JavaContentAssistInvocationContext ctx;
    private CompletionProposalCollector collector;
    private List<ChainElement> entrypoints;
    private String error;
    private IJavaElement invocationSite;
    private String [] excludedTypes;

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
        collector = new CompletionProposalCollector(ctx.getCompilationUnit());
        collector.setInvocationContext(ctx);
        ICompilationUnit cu = ctx.getCompilationUnit();
        int offset = ctx.getInvocationOffset();
        try {
            cu.codeComplete(offset, collector, new NullProgressMonitor());
        } catch (JavaModelException e) {
            e.printStackTrace();
        }

        invocationSite = ctx.getCoreContext().getEnclosingElement();
        return true;
    }

    private boolean shouldPerformCompletionOnExpectedType() {
        final IType expected = ctx.getExpectedType();
        return expected != null;
//        return expected != null || !ctx.getExpectedTypeNames().isEmpty();
    }

    private boolean findEntrypoints() {
        excludedTypes = JavaManipulation.getPreference(PreferenceConstants.PREF_IGNORED_TYPES, ctx.getProject()).split("\\|"); //$NON-NLS-1$
        for (int i = 0; i < excludedTypes.length; ++i) {
            excludedTypes[i] = "L" + excludedTypes[i].replace('.', '/'); //$NON-NLS-1$
        }

        entrypoints = new LinkedList<>();
        List<IJavaElement> elements = new LinkedList<>();
        for (IJavaCompletionProposal prop : collector.getJavaCompletionProposals()) {
            if (prop instanceof AbstractJavaCompletionProposal) {
                AbstractJavaCompletionProposal aprop = (AbstractJavaCompletionProposal) prop;
                IJavaElement element = aprop.getJavaElement();
                if (element != null) {
                    elements.add(element);
                } else {
                    IJavaElement [] visibleElements = ctx.getCoreContext().getVisibleElements(null);
                        for (IJavaElement ve : visibleElements) {
                        if (ve.getElementName().equals(aprop.getReplacementString())) {
                            elements.add(ve);
                        }
                    }
                }
            }
        }

        IBinding [] bindings = TypeBindingAnalyzer.resolveBindingsForTypes(ctx.getCompilationUnit(), elements.toArray(new IJavaElement [0]));
        for (IBinding b : bindings) {
            if (b != null && matchesExpectedPrefix(b) && !ChainFinder.isFromExcludedType(Arrays.asList(excludedTypes), b)) {
                entrypoints.add(new ChainElement(b, false));
            }
        }

        return !entrypoints.isEmpty();
    }

    private boolean matchesExpectedPrefix(final IBinding binding) {
        String prefix = String.valueOf(ctx.getCoreContext().getToken());
        return String.valueOf(binding.getName()).startsWith(prefix);
    }

    private List<ICompletionProposal> executeCallChainSearch() {
        final int maxChains = Integer.parseInt(JavaManipulation.getPreference(PreferenceConstants.PREF_MAX_CHAINS, ctx.getProject()));
        final int minDepth = Integer.parseInt(JavaManipulation.getPreference(PreferenceConstants.PREF_MIN_CHAIN_LENGTH, ctx.getProject()));
        final int maxDepth = Integer.parseInt(JavaManipulation.getPreference(PreferenceConstants.PREF_MAX_CHAIN_LENGTH, ctx.getProject()));

        final List<ITypeBinding> expectedTypes = TypeBindingAnalyzer.resolveBindingsForExpectedTypes(ctx);
        final ChainFinder finder = new ChainFinder(expectedTypes, Arrays.asList(excludedTypes), invocationSite);
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
