/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.workingsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IWorkingSetNewWizard;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

public final class ConfigureWorkingSetAssignementAction extends SelectionDispatchAction {
	
	private final class WorkingSetModelAwareSelectionDialog extends SimpleWorkingSetSelectionDialog {
		
		private CheckboxTableViewer fTableViewer;
		private boolean fShowVisibleOnly;
		private final HashSet fGrayedElements;
		private final HashSet fCheckedElements;
		private final IWorkingSet[] fWorkingSets;

		private WorkingSetModelAwareSelectionDialog(Shell shell, IWorkingSet[] allWorkingSet, IWorkingSet[] initialSelection, IWorkingSet[] initialGrayed) {
			super(shell, allWorkingSet, initialSelection);
			fWorkingSets= allWorkingSet;
			fShowVisibleOnly= true;
			fGrayedElements= new HashSet();
			fGrayedElements.addAll(Arrays.asList(initialGrayed));
			
			fCheckedElements= new HashSet();
			fCheckedElements.addAll(Arrays.asList(initialSelection));
		}
		
		public IWorkingSet[] getGrayed() {
			return (IWorkingSet[])fGrayedElements.toArray(new IWorkingSet[fGrayedElements.size()]);
		}
		
		public IWorkingSet[] getSelection() {
			return (IWorkingSet[])fCheckedElements.toArray(new IWorkingSet[fCheckedElements.size()]);
		}

		protected CheckboxTableViewer createTableViewer(Composite parent) {
			fTableViewer= super.createTableViewer(parent);
			fTableViewer.setGrayedElements(fGrayedElements.toArray());
			fTableViewer.addCheckStateListener(new ICheckStateListener() {
				public void checkStateChanged(CheckStateChangedEvent event) {
					Object element= event.getElement();
					fTableViewer.setGrayed(element, false);
					fGrayedElements.remove(element);
					if (event.getChecked()) {
						fCheckedElements.add(element);
					} else {
						fCheckedElements.remove(element);
					}
				}
			});
			return fTableViewer;
		}
		
		protected void selectAll() {
			super.selectAll();
			fGrayedElements.clear();
			fCheckedElements.addAll(Arrays.asList(fWorkingSets));
		}

		protected void deselectAll() {
			super.deselectAll();
			fGrayedElements.clear();
			fCheckedElements.clear();
		}
		
		protected ViewerFilter createTableFilter() {
			final ViewerFilter superFilter= super.createTableFilter();
			return new ViewerFilter() {
				public boolean select(Viewer viewer, Object parentElement, Object element) {
					if (!superFilter.select(viewer, parentElement, element))
						return false;
					
					IWorkingSet set= (IWorkingSet)element;
					if (!isValidWorkingSet(set))
						return false;
					
					if (fShowVisibleOnly) {
						if (!fWorkingSetModel.isActiveWorkingSet(set))
							return false;
						
						return true;						
					} else {
						return true;
					}
				}					
			};
		}
		
		protected ViewerSorter createTableSorter() {
			final ViewerSorter superSorter= super.createTableSorter();
			return new ViewerSorter() {
				public int compare(Viewer viewer, Object e1, Object e2) {
					IWorkingSet[] activeWorkingSets= fWorkingSetModel.getActiveWorkingSets();
					for (int i= 0; i < activeWorkingSets.length; i++) {
						IWorkingSet active= activeWorkingSets[i];
						if (active == e1) {
							return -1;
						} else if (active == e2) {
							return 1;
						}
					}
					
					return superSorter.compare(viewer, e1, e2);
				}
			};
		}
	
		protected void createBottomButtonBar(Composite parent) {
			Composite bar= new Composite(parent, SWT.NONE);
			bar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			bar.setLayout(new GridLayout(2, false));
			
			final Button showVisibleOnly= new Button(bar, SWT.CHECK);
			showVisibleOnly.setSelection(fShowVisibleOnly);
			showVisibleOnly.setLayoutData(new GridData(SWT.LEAD, SWT.FILL, false, true));
			showVisibleOnly.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					fShowVisibleOnly= showVisibleOnly.getSelection();
					
					fTableViewer.refresh();
					
					fTableViewer.setCheckedElements(fCheckedElements.toArray());
					fTableViewer.setGrayedElements(fGrayedElements.toArray());
				}
			});
			
			Link ppwsLink= new Link(bar, SWT.NONE);
			ppwsLink.setText(WorkingSetMessages.ConfigureWorkingSetAssignementAction_OnlyShowVisible_link);
			ppwsLink.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			ppwsLink.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					
					List workingSets= new ArrayList(Arrays.asList(fWorkingSetModel.getAllWorkingSets()));
					IWorkingSet[] activeWorkingSets= fWorkingSetModel.getActiveWorkingSets();
					WorkingSetConfigurationDialog dialog= new WorkingSetConfigurationDialog(
						getShell(), 
						(IWorkingSet[])workingSets.toArray(new IWorkingSet[workingSets.size()]),
						activeWorkingSets); 
					dialog.setSelection(activeWorkingSets);
					if (dialog.open() == IDialogConstants.OK_ID) {
						IWorkingSet[] selection= dialog.getSelection();
						fWorkingSetModel.setActiveWorkingSets(selection);
					}
					
					recalculateCheckedState();
				}
			});
		}
		
		protected void createButtonsForRightButtonBar(Composite bar) {
			super.createButtonsForRightButtonBar(bar);
			
			new Label(bar, SWT.NONE);
			
			Button newButton= new Button(bar, SWT.NONE);
			newButton.setText(WorkingSetMessages.ConfigureWorkingSetAssignementAction_New_button); 
			newButton.setFont(bar.getFont());
			setButtonLayoutData(newButton);
			newButton.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
					IWorkingSetNewWizard wizard= manager.createWorkingSetNewWizard(new String[] {JavaWorkingSetUpdater.ID});
					WizardDialog dialog= new WizardDialog(getShell(), wizard);
					dialog.create();
					if (dialog.open() == Window.OK) {
						IWorkingSet workingSet= wizard.getSelection();
						manager.addWorkingSet(workingSet);
						
						IWorkingSet[] activeWorkingSets= fWorkingSetModel.getActiveWorkingSets();
						IWorkingSet[] newActive= new IWorkingSet[activeWorkingSets.length + 1];
						System.arraycopy(activeWorkingSets, 0, newActive, 0, activeWorkingSets.length);
						newActive[activeWorkingSets.length]= workingSet;
						fWorkingSetModel.setActiveWorkingSets(newActive);
						
						recalculateCheckedState();
						
						fTableViewer.setSelection(new StructuredSelection(workingSet), true);
					}
				}
			});
		}
		
		private void recalculateCheckedState() {
			fTableViewer.setInput(fWorkingSetModel.getAllWorkingSets());
			
			fTableViewer.refresh();
			
			IWorkingSet[] checked= getChecked();
			IWorkingSet[] grayed= ConfigureWorkingSetAssignementAction.this.getGrayed(checked);
			
			fCheckedElements.clear();
			fCheckedElements.addAll(Arrays.asList(checked));
			fGrayedElements.addAll(Arrays.asList(grayed));
			
			fTableViewer.setCheckedElements(checked);
			fTableViewer.setGrayedElements(grayed);
		}
	}

	private static final String[] VALID_WORKING_SET_IDS= new String[] {
			JavaWorkingSetUpdater.ID,
			"org.eclipse.ui.resourceWorkingSetPage" //$NON-NLS-1$
	};
	
	private IAdaptable[] fElements;
	private WorkingSetModel fWorkingSetModel;
	private final IWorkbenchSite fSite;

	public ConfigureWorkingSetAssignementAction(IWorkbenchSite site) {
		super(site);
		fSite= site;
		setText(WorkingSetMessages.ConfigureWorkingSetAssignementAction_WorkingSets_actionLabel);
	}
	
	public void setWorkingSetModel(WorkingSetModel workingSetModel) {
		fWorkingSetModel= workingSetModel;
	}
	
	public void selectionChanged(IStructuredSelection selection) {
		fElements= getSelectedElements(selection);
		setEnabled(fElements.length > 0);
	}

	private IAdaptable[] getSelectedElements(IStructuredSelection selection) {
		ArrayList result= new ArrayList();
		
		List list= selection.toList();
		for (Iterator iterator= list.iterator(); iterator.hasNext();) {
			Object object= iterator.next();
			if (object instanceof IResource || object instanceof IJavaElement) {
				result.add(object);
			}
		}
		
		return (IAdaptable[])result.toArray(new IAdaptable[result.size()]);
	}

	public void run() {		
		
		IWorkingSet[] checked= getChecked();
		IWorkingSet[] grayed= getGrayed(checked);
		WorkingSetModelAwareSelectionDialog dialog= new WorkingSetModelAwareSelectionDialog(fSite.getShell(), fWorkingSetModel.getAllWorkingSets(), checked, grayed);
		
		if (fElements.length == 1) {
			IAdaptable element= fElements[0];
			if (element instanceof IProject || element instanceof IJavaProject) {
				String elementName;
				if (element instanceof IProject) {
					elementName= ((IProject)element).getName();
				} else {
					elementName= ((IJavaProject)element).getProject().getName();
				}
				dialog.setMessage(Messages.format(WorkingSetMessages.ConfigureWorkingSetAssignementAction_DialogMessage_specific, elementName));
			} else {
				dialog.setMessage(WorkingSetMessages.ConfigureWorkingSetAssignementAction_DialogMessage_single);
			}
		} else {
			dialog.setMessage(WorkingSetMessages.ConfigureWorkingSetAssignementAction_DialogMessage_multi);
		}
		if (dialog.open() == Window.OK) {
			updateWorkingSets(dialog.getSelection(), dialog.getGrayed(), fElements);
		}
	}
	
	private IWorkingSet[] getChecked() {
		HashSet result= new HashSet();
		
		IWorkingSet[] workingSets= fWorkingSetModel.getAllWorkingSets();
		for (int i= 0; i < fElements.length; i++) {			
			HashSet containingWorkingSets= getContainingWorkingSets(workingSets, fElements[i]);
			result.addAll(containingWorkingSets);
		}
		
		return (IWorkingSet[])result.toArray(new IWorkingSet[result.size()]);
	}

	private IWorkingSet[] getGrayed(IWorkingSet[] checked) {
		HashSet result= new HashSet();
		
		for (int i= 0; i < checked.length; i++) {
			IWorkingSet checkedSet= checked[i];
			for (int j= 0; j < fElements.length && !result.contains(checkedSet); j++) {
				IAdaptable adapted= adapt(checkedSet, fElements[j]);
				if (adapted == null || !contains(checkedSet, adapted)) {
					result.add(checkedSet);
				}
			}
		}
		
		return (IWorkingSet[])result.toArray(new IWorkingSet[result.size()]);
	}

	private void updateWorkingSets(IWorkingSet[] newWorkingSets, IWorkingSet[] grayedWorkingSets, IAdaptable[] elements) {
		HashSet selectedSets= new HashSet(Arrays.asList(newWorkingSets));
		HashSet grayedSets= new HashSet(Arrays.asList(grayedWorkingSets));
		IWorkingSet[] workingSets= fWorkingSetModel.getAllWorkingSets();
		
		for (int i= 0; i < workingSets.length; i++) {
			IWorkingSet workingSet= workingSets[i];
			if (isValidWorkingSet(workingSet) && !selectedSets.contains(workingSet) && !grayedSets.contains(workingSet)) {
				for (int j= 0; j < elements.length; j++) {							
					IAdaptable adapted= adapt(workingSet, elements[j]);
					if (adapted != null && contains(workingSet, adapted)) {
						remove(workingSet, adapted);
					}
				}
			}
		}

		for (int i= 0; i < newWorkingSets.length; i++) {
			IWorkingSet set= newWorkingSets[i];
			if (!grayedSets.contains(set)) {
				for (int j= 0; j < elements.length; j++) {						
					IAdaptable adapted= adapt(set, elements[j]);
					if (adapted != null && !contains(set, adapted)) {
						add(set, adapted);
					}
				}
			}
		}
	}

	private HashSet getContainingWorkingSets(IWorkingSet[] workingSets, IAdaptable element) {
		HashSet result= new HashSet();

		for (int i= 0; i < workingSets.length; i++) {
			IWorkingSet set= workingSets[i];
			IAdaptable adapted= adapt(set, element);
			if (adapted != null && contains(set, adapted)) {
				result.add(set);
			}
		}

		return result;
	}
	
	private static boolean isValidWorkingSet(IWorkingSet set) {
		for (int i= 0; i < VALID_WORKING_SET_IDS.length; i++) {
			if (VALID_WORKING_SET_IDS[i].equals(set.getId()))
				return true;
		}

		return false;
	}

	private static IAdaptable adapt(IWorkingSet set, IAdaptable element) {
		IAdaptable[] adaptedElements= set.adaptElements(new IAdaptable[] {
			element
		});
		if (adaptedElements.length != 1)
			return null;

		return adaptedElements[0];
	}

	private static boolean contains(IWorkingSet set, IAdaptable adaptedElement) {
		IAdaptable[] elements= set.getElements();
		for (int i= 0; i < elements.length; i++) {
			if (elements[i].equals(adaptedElement))
				return true;
		}

		return false;
	}

	private static void remove(IWorkingSet workingSet, IAdaptable adaptedElement) {
		HashSet set= new HashSet(Arrays.asList(workingSet.getElements()));
		set.remove(adaptedElement);
		workingSet.setElements((IAdaptable[])set.toArray(new IAdaptable[set.size()]));
	}

	private static void add(IWorkingSet workingSet, IAdaptable adaptedElement) {
		IAdaptable[] elements= workingSet.getElements();
		IAdaptable[] newElements= new IAdaptable[elements.length + 1];
		System.arraycopy(elements, 0, newElements, 0, elements.length);
		newElements[elements.length]= adaptedElement;
		workingSet.setElements(newElements);
	}

}