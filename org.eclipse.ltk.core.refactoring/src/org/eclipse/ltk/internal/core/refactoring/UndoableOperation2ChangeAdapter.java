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
package org.eclipse.ltk.internal.core.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.commands.operations.IHistoryNotificationAwareOperation;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryEvent;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.IValidationCheckResultQuery;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

public class UndoableOperation2ChangeAdapter implements IUndoableOperation, IHistoryNotificationAwareOperation {
	
	private String fLabel;
	private Change fExecuteChange;
	private Change fUndoChange;
	private Change fRedoChange;
	private Change fActiveChange;
	
	private List fContexts= new ArrayList();
	
	private static class ContextAdapter implements IAdaptable {
		private IAdaptable fInfoAdapter;
		private String fTitle;
		public ContextAdapter(IAdaptable infoAdapter, String title) {
			fInfoAdapter= infoAdapter;
			fTitle= title;
		}
		public Object getAdapter(Class adapter) {
			if (String.class.equals(adapter))
				return fTitle;
			return fInfoAdapter.getAdapter(adapter);
		}
	}
	
	private static class ExecuteResult {
		boolean changeExecuted;
		boolean changeExecutionFailed;
		Change reverseChange;
		RefactoringStatus validationStatus;
		public ExecuteResult() {
			validationStatus= new RefactoringStatus();
		}
	}
	
	public UndoableOperation2ChangeAdapter(Change change) {
		fExecuteChange= change;
		fActiveChange= change;
	}
	
	public void setUndoChange(Change undoChange) {
		fUndoChange= undoChange;
		fActiveChange= fUndoChange;
		fExecuteChange= null;
		fRedoChange= null;
	}
	
	public Change getChange() {
		return fActiveChange;
	}

	public void setLabel(String label) {
		fLabel= label;
	}
	
	public String getLabel() {
		if (fLabel != null)
			return fLabel;
		return fActiveChange.getName();
	}

	public String getDescription() {
		return fActiveChange.getName();
	}

	public boolean isComposite() {
		return false;
	}

	public void addContext(IUndoContext context) {
		fContexts.add(context);
	}

	public boolean hasContext(IUndoContext context) {
		if (context == null)
			return false;
		for (int i = 0; i< fContexts.size(); i++) {
			IUndoContext otherContext = (IUndoContext)fContexts.get(i);
			// have to check both ways because one context may be more general in
			// its matching rules than another.
			if (context.matches(otherContext) || otherContext.matches(context)) 
				return true;
		}
		return false;
	}

	public void removeContext(IUndoContext context) {
		fContexts.remove(context);
	}

	public IUndoContext[] getContexts() {
		return (IUndoContext[])fContexts.toArray(new IUndoContext[fContexts.size()]);
	}

	public boolean canExecute() {
		return fExecuteChange != null;
	}

	public IStatus execute(IProgressMonitor monitor, IAdaptable info) {
		try {
			ExecuteResult result= executeChange(
				getQuery(
					info,
					RefactoringCoreMessages.getString("Refactoring.execute.label")), //$NON-NLS-1$
				monitor);
			if (!result.changeExecuted) {
				return createStatus(result);
			}
			fUndoChange= result.reverseChange;
			fActiveChange= fUndoChange;
			fExecuteChange= null;
			return new Status(IStatus.OK, RefactoringCorePlugin.getPluginId(), 0, "", null); //$NON-NLS-1$
		} catch (CoreException e) {
			return e.getStatus();
		}
	}

	public boolean canUndo() {
		return fUndoChange != null;
	}

	public IStatus undo(IProgressMonitor monitor, IAdaptable info) {
		try {
			ExecuteResult result= executeChange(
				getQuery(
					info,
					RefactoringCoreMessages.getString("Refactoring.undo.label")), //$NON-NLS-1$ 
				monitor);
			if (!result.changeExecuted) {
				return createStatus(result);
			}
			fRedoChange= result.reverseChange;
			fActiveChange= fRedoChange;
			fUndoChange= null;
			return new Status(IStatus.OK, RefactoringCorePlugin.getPluginId(), 0, "", null); //$NON-NLS-1$
		} catch (CoreException e) {
			return e.getStatus();
		}
	}
	
	public boolean canRedo() {
		return fRedoChange != null;
	}

	public IStatus redo(IProgressMonitor monitor, IAdaptable info) {
		try {
			ExecuteResult result= executeChange(
				getQuery(
					info,
					RefactoringCoreMessages.getString("Refactoring.redo.label")), //$NON-NLS-1$ 
				monitor);
			if (!result.changeExecuted) {
				return createStatus(result);
			}
			fUndoChange= result.reverseChange;
			fActiveChange= fUndoChange;
			fRedoChange= null;
			return new Status(IStatus.OK, RefactoringCorePlugin.getPluginId(), 0, "", null); //$NON-NLS-1$
		} catch (CoreException e) {
			return e.getStatus();
		}
	}
	
	public void aboutToNotify(OperationHistoryEvent event) {
		switch(event.getEventType()) {
			case OperationHistoryEvent.ABOUT_TO_EXECUTE:
			case OperationHistoryEvent.ABOUT_TO_UNDO:
			case OperationHistoryEvent.ABOUT_TO_REDO:
			case OperationHistoryEvent.DONE:
			case OperationHistoryEvent.UNDONE:
			case OperationHistoryEvent.REDONE:
			case OperationHistoryEvent.OPERATION_NOT_OK:
				ResourcesPlugin.getWorkspace().checkpoint(false);
				break;
		}
	}

	public void dispose() {
		fActiveChange.dispose();
	}
	
	private ExecuteResult executeChange(final IValidationCheckResultQuery query, IProgressMonitor pm) throws CoreException {
		final ExecuteResult result= new ExecuteResult();
		if (fActiveChange == null || !fActiveChange.isEnabled())
			return result;
		IWorkspaceRunnable runnable= new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				boolean reverseIsInitialized= false;
				try {
					monitor.beginTask("", 11); //$NON-NLS-1$
					result.validationStatus= fActiveChange.isValid(new SubProgressMonitor(monitor, 2));
					if (result.validationStatus.hasFatalError()) {
						query.stopped(result.validationStatus);
						fActiveChange.dispose();
						return;
					}
					if (!result.validationStatus.isOK() && !query.proceed(result.validationStatus)) {
						return;
					}
					try {
						result.changeExecutionFailed= true;
						result.reverseChange= fActiveChange.perform(new SubProgressMonitor(monitor, 9));
						result.changeExecutionFailed= false;
						result.changeExecuted= true;
					} finally {
						ResourcesPlugin.getWorkspace().checkpoint(false);
					}
					fActiveChange.dispose();
					if (result.reverseChange != null) {
						result.reverseChange.initializeValidationData(new NotCancelableProgressMonitor(
							new SubProgressMonitor(monitor, 1)));
						reverseIsInitialized= true;
					}
				} catch (CoreException e) {
					Change ch= result.reverseChange;
					result.reverseChange= null;
					if (ch != null && reverseIsInitialized) {
						ch.dispose();
					}
					throw e;
				} catch (RuntimeException e) {
					Change ch= result.reverseChange;
					result.reverseChange= null;
					if (ch != null && reverseIsInitialized) {
						ch.dispose();
					}
					throw e;
				} finally {
					monitor.done();
				}
			}
		};
		ResourcesPlugin.getWorkspace().run(runnable, pm);
		return result;
	}
	
	private IStatus createStatus(ExecuteResult result) {
		if (!result.validationStatus.isOK()) {
			return asStatus(result.validationStatus.getEntryWithHighestSeverity());
		} else {
			return new Status(IStatus.ERROR, RefactoringCorePlugin.getPluginId(), IStatus.ERROR, 
				RefactoringCoreMessages.getString("UndoableOperation2ChangeAdapter.error.message"),  //$NON-NLS-1$
				null);
		}
	}

	private static IStatus asStatus(RefactoringStatusEntry entry) {
		int statusSeverity= IStatus.ERROR;
		switch (entry.getSeverity()) {
			case RefactoringStatus.OK :
				statusSeverity= IStatus.OK;
				break;
			case RefactoringStatus.INFO :
				statusSeverity= IStatus.INFO;
				break;
			case RefactoringStatus.WARNING :
			case RefactoringStatus.ERROR :
				statusSeverity= IStatus.WARNING;
				break;
		}
		String pluginId= entry.getPluginId();
		int code= entry.getCode();
		if (pluginId == null) {
			pluginId= RefactoringCorePlugin.getPluginId();
			code= IStatus.ERROR;
		}
		return new Status(statusSeverity, pluginId, code, entry.getMessage(), null);
	}
	
	private IValidationCheckResultQuery getQuery(IAdaptable info, String title) {
		IValidationCheckResultQuery result= (IValidationCheckResultQuery)info.getAdapter(IValidationCheckResultQuery.class);
		if (result != null)
			return result;
		ContextAdapter context= new ContextAdapter(info, title);
		return RefactoringCore.getQueryFactory().create(context);
	}
}
