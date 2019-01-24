/**
 * Copyright (c) 2011 Stefan Henss.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stefan Henß - initial API and implementation.
 */
package org.eclipse.jdt.internal.ui.text.java;

import java.lang.reflect.Field;

import org.eclipse.jdt.internal.codeassist.InternalCompletionContext;
import org.eclipse.jdt.internal.codeassist.InternalExtendedCompletionContext;
import org.eclipse.jdt.internal.compiler.lookup.Scope;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

/**
 * A scope is required to determine for methods and fields if they are visible from the invocation site.
 */
public final class ScopeAccessWorkaround {

    private static Field EXTENDED_CONTEXT;
    private static Field ASSIST_SCOPE;

    private ScopeAccessWorkaround() {
        // Not meant to be instantiated
    }

    public static Scope resolveScope(final JavaContentAssistInvocationContext ctx) {
        try {
			EXTENDED_CONTEXT = InternalCompletionContext.class.getDeclaredField("extendedContext"); //$NON-NLS-1$
			EXTENDED_CONTEXT.setAccessible(true);
			ASSIST_SCOPE = InternalExtendedCompletionContext.class.getDeclaredField("assistScope"); //$NON-NLS-1$
			ASSIST_SCOPE.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e1) {
			return null;
		}

        if (ctx.getCoreContext() == null || !(ctx.getCoreContext() instanceof InternalCompletionContext)) {
            return null;
        }
        InternalCompletionContext context = (InternalCompletionContext) ctx.getCoreContext();
        if (EXTENDED_CONTEXT == null || ASSIST_SCOPE == null) {
            return null;
        }
        try {
            final InternalExtendedCompletionContext extendedContext = (InternalExtendedCompletionContext) EXTENDED_CONTEXT
                    .get(context);
            if (extendedContext == null) {
                return null;
            }
            return (Scope) ASSIST_SCOPE.get(extendedContext);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
