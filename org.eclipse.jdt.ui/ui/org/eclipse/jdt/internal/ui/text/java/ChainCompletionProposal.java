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
package org.eclipse.recommenders.internal.chain.rcp;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.google.common.annotations.VisibleForTesting;

/**
 * This class basically delegates all events to a {@link TemplateProposal} but provides some auxiliary methods for
 * testing such as {@link #getChainElementNames()}. It may be extended to track user click feedback to continuously
 * improve chain completion.
 */
@SuppressWarnings("restriction")
public class ChainCompletionProposal implements IJavaCompletionProposal, ICompletionProposalExtension2,
        ICompletionProposalExtension3, ICompletionProposalExtension4, ICompletionProposalExtension6 {

    private static final int CHAIN_PROPOSAL_BOOST = 100;

    private final Chain chain;
    private final TemplateProposal completion;

    public ChainCompletionProposal(final TemplateProposal completion, final Chain chain) {
        this.completion = completion;
        this.chain = chain;
    }

    @VisibleForTesting
    public List<String> getChainElementNames() {
        final List<String> b = new LinkedList<String>();
        for (final ChainElement edge : chain.getElements()) {
            final Binding bind = edge.getElementBinding();
            final char[] name = bind instanceof MethodBinding ? ((MethodBinding) bind).selector : bind.readableName();
            b.add(String.valueOf(name));
        }
        return b;
    }

    @Override
    public void apply(final IDocument document) {
        throw new IllegalStateException("Applying proposals to documents is deprecated"); //$NON-NLS-1$
    }

    @Override
    public void apply(final ITextViewer viewer, final char trigger, final int stateMask, final int offset) {
        completion.apply(viewer, trigger, stateMask, offset);
    }

    @Override
    public String getAdditionalProposalInfo() {
        return completion.getAdditionalProposalInfo();
    }

    @Override
    public String getDisplayString() {
        return completion.getDisplayString();
    }

    @Override
    public IContextInformation getContextInformation() {
        return completion.getContextInformation();
    }

    @Override
    public int getRelevance() {
        return -chain.getElements().size() - CHAIN_PROPOSAL_BOOST;
    }

    @Override
    public Point getSelection(final IDocument document) {
        return completion.getSelection(document);
    }

    @Override
    public Image getImage() {
        return completion.getImage();
    }

    @Override
    public void selected(final ITextViewer viewer, final boolean smartToggle) {
        completion.selected(viewer, smartToggle);

    }

    @Override
    public void unselected(final ITextViewer viewer) {
        completion.unselected(viewer);
    }

    @Override
    public boolean validate(final IDocument document, final int offset, final DocumentEvent event) {
        return completion.validate(document, offset, event);
    }

    @Override
    public String toString() {
        return completion.getDisplayString();
    }

    @Override
    public StyledString getStyledDisplayString() {
        return completion.getStyledDisplayString();
    }

    @Override
    public boolean isAutoInsertable() {
        return completion.isAutoInsertable();
    }

    @Override
    public IInformationControlCreator getInformationControlCreator() {
        return completion.getInformationControlCreator();
    }

    @Override
    public CharSequence getPrefixCompletionText(final IDocument document, final int completionOffset) {
        return completion.getPrefixCompletionText(document, completionOffset);
    }

    @Override
    public int getPrefixCompletionStart(final IDocument document, final int completionOffset) {
        return completion.getPrefixCompletionStart(document, completionOffset);
    }
}
