/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;

import org.eclipse.jdt.internal.ui.text.correction.QuickFixProcessor;

/**
 * Analyze the CompilationUnit for any issues and report them.
 *
 * While many issues are found and reported in the internal compiler AST
 * code, (eg. org.eclipse.jdt.internal.compiler.ast.Statement#analyseCode(..))
 * this approach uses the public AST and is meant for non-compiler issues
 * (eg. style, bad practice, correctness, performance).
 */
public class ProblemAnalyzer {

	public static void analyze(CompilationUnit ast) {
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				checkForCollectionStreamWithJoin(ast, node);
				return false;
			}
		});
	}

	// X.stream().collect(Collectors.joining(Y)) -> String.join(Y, X)
	private static void checkForCollectionStreamWithJoin(CompilationUnit ast, MethodInvocation node) {

		// collect(..) receiver is a method invocation
		// collect(..) has exactly 1 argument and is a method invocation
		if (node.getExpression() instanceof MethodInvocation
				&& node.arguments().size() == 1 && node.arguments().get(0) instanceof MethodInvocation
				&& "collect".equals(node.getName().getIdentifier())) { //$NON-NLS-1$
			boolean isCollectionStreamWithJoin= false;
			MethodInvocation arg= (MethodInvocation) node.arguments().get(0);
			MethodInvocation recv= (MethodInvocation) node.getExpression();

			// collect(..) receiver is 'stream()' and has no arguments
			if ("stream".equals(recv.getName().getIdentifier()) //$NON-NLS-1$
					&& (recv.arguments().isEmpty())) {
				// collect(..) argument is 'joining(..)' and has 0 or 1 argument
				if ("joining".equals(String.valueOf(arg.getName().getIdentifier())) //$NON-NLS-1$
						&& arg.arguments().size() <= 1) {
					// collect(..) argument receiver is 'Collectors'
					if (arg.getExpression() instanceof SimpleName) {
						String arg_recv= ((SimpleName)arg.getExpression()).getIdentifier();
						if ("Collectors".equals(arg_recv)) { //$NON-NLS-1$
							isCollectionStreamWithJoin= true;
						}
					}
				}
			}

			if (isCollectionStreamWithJoin) {
				createJavaProblemMarker(ast.getJavaElement().getResource(),
						QuickFixProcessor.CollectAndJoiningStringStream, IMarker.SEVERITY_INFO,
						"Stream<String> being reduced to a String through join operation can be re-written using String.join(..)",
						node.getStartPosition(), node.getStartPosition() + node.getLength(), ast.getLineNumber(node.getStartPosition()));
			}
		}
	}

	private static IMarker createJavaProblemMarker (IResource res, int problemId, int severity, String message, int start, int end, int line) {
		IMarker marker= null;
		try {
			marker= res.createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
			marker.setAttribute(IJavaModelMarker.ID, problemId);
			marker.setAttribute(IMarker.SEVERITY, severity);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.CHAR_START, start);
			marker.setAttribute(IMarker.CHAR_END, end);
			marker.setAttribute(IMarker.LINE_NUMBER, line);
		} catch (CoreException e) {
			// marker is either null, or partially completed
		}
		return marker;
	}

}
