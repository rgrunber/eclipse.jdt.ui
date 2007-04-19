/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.workingsets;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.jdt.ui.IContextMenuConstants;

public class WorkingSetActionGroup extends ActionGroup {

	private IViewSite fSite;
	private ISelectionChangedListener fLazyInitializer= new  ISelectionChangedListener() {
		public void selectionChanged(SelectionChangedEvent event) {
			ISelectionProvider selectionProvider= fSite.getSelectionProvider();
			selectionProvider.removeSelectionChangedListener(fLazyInitializer);
			ISelection selection= event.getSelection();
			
			fEditAction= new OpenPropertiesWorkingSetAction(fSite);
			fEditAction.update(selection);
			selectionProvider.addSelectionChangedListener(fEditAction);
			
			fCloseAction= OpenCloseWorkingSetAction.createCloseAction(fSite);
			fCloseAction.update(selection);
			selectionProvider.addSelectionChangedListener(fCloseAction);
			
			fOpenAction= OpenCloseWorkingSetAction.createOpenAction(fSite);
			fOpenAction.update(selection);
			selectionProvider.addSelectionChangedListener(fOpenAction);
		}
	};
	
	private OpenPropertiesWorkingSetAction fEditAction;
	private OpenCloseWorkingSetAction fCloseAction;
	private OpenCloseWorkingSetAction fOpenAction;
	
	public WorkingSetActionGroup(IViewPart part) {
		fSite= part.getViewSite();
		fSite.getSelectionProvider().addSelectionChangedListener(fLazyInitializer);
	}
	
	public void dispose() {
		ISelectionProvider selectionProvider= fSite.getSelectionProvider();
		
		if (fEditAction != null)
			selectionProvider.removeSelectionChangedListener(fEditAction);
		if (fCloseAction != null) {
			selectionProvider.removeSelectionChangedListener(fCloseAction);
			fCloseAction.dispose();
		}
		if (fOpenAction != null) {
			selectionProvider.removeSelectionChangedListener(fOpenAction);
			fOpenAction.dispose();
		}
	}
	
	public void fillContextMenu(IMenuManager menu) {
		super.fillContextMenu(menu);
		if (fCloseAction != null && fCloseAction.isEnabled())
			menu.appendToGroup(IContextMenuConstants.GROUP_BUILD, fCloseAction);
		if (fOpenAction != null && fOpenAction.isEnabled())
			menu.appendToGroup(IContextMenuConstants.GROUP_BUILD, fOpenAction);
		if (fEditAction != null && fEditAction.isEnabled()) {
			menu.prependToGroup(IContextMenuConstants.GROUP_PROPERTIES, new Separator());
			menu.prependToGroup(IContextMenuConstants.GROUP_PROPERTIES, fEditAction);
		}
			
	}
}
