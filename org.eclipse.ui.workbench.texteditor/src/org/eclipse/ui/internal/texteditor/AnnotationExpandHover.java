/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.texteditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.viewers.IDoubleClickListener;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlCreatorExtension;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewerExtension3;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationAccess;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationHoverExtension;
import org.eclipse.jface.text.source.IAnnotationHoverExtension2;
import org.eclipse.jface.text.source.IAnnotationListener;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRulerExtension;
import org.eclipse.jface.text.source.IVerticalRulerInfo;

import org.eclipse.ui.internal.texteditor.AnnotationExpansionControl.AnnotationHoverInput;

/**
 * 
 * 
 * @since 3.0
 */
public class AnnotationExpandHover implements IAnnotationHover, IAnnotationHoverExtension, IAnnotationHoverExtension2 {
	private class InformationControlCreator implements IInformationControlCreator, IInformationControlCreatorExtension {

		/*
		 * @see org.eclipse.jface.text.IInformationControlCreator#createInformationControl(org.eclipse.swt.widgets.Shell)
		 */
		public IInformationControl createInformationControl(Shell parent) {
			return new AnnotationExpansionControl(parent, SWT.NONE, fAnnotationAccess);
		}

		/*
		 * @see org.eclipse.jface.text.IInformationControlCreatorExtension#canReuse(org.eclipse.jface.text.IInformationControl)
		 */
		public boolean canReuse(IInformationControl control) {
			return control instanceof AnnotationExpansionControl;
		}

		/*
		 * @see org.eclipse.jface.text.IInformationControlCreatorExtension#canReplace(org.eclipse.jface.text.IInformationControlCreator)
		 */
		public boolean canReplace(IInformationControlCreator creator) {
			return creator == this;
		}
	}
	
	private final IInformationControlCreator fgCreator= new InformationControlCreator();
	protected IVerticalRulerInfo fVerticalRulerInfo;
	protected IAnnotationListener fAnnotationListener; 

	protected IDoubleClickListener fDblClickListener;
	protected IAnnotationAccess fAnnotationAccess;
	
	/**
	 * Creates a new hover instance.
	 * 
	 * @param ruler
	 * @param listener
	 * @param doubleClickListener
	 * @param access
	 */
	public AnnotationExpandHover(IVerticalRulerInfo ruler, IAnnotationListener listener, IDoubleClickListener doubleClickListener, IAnnotationAccess access) {
		fAnnotationListener= listener;
		fVerticalRulerInfo= ruler;
		fDblClickListener= doubleClickListener;
		fAnnotationAccess= access;
	}

	/**
	 * @param ruler
	 * @param access
	 */
	public AnnotationExpandHover(IVerticalRulerInfo ruler, IAnnotationAccess access) {
		this(ruler, null, null, access);
	}

	/*
	 * @see org.eclipse.jface.text.source.IAnnotationHover#getHoverInfo(org.eclipse.jface.text.source.ISourceViewer, int)
	 */
	public String getHoverInfo(ISourceViewer sourceViewer, int line) {
		// we don't have any sensible return value as text
		return null;
	}
	
	/*
	 * @see org.eclipse.jface.text.source.IAnnotationHoverExtension2#getHoverInfo2(org.eclipse.jface.text.source.ISourceViewer, int)
	 */
	public Object getHoverInfo2(ISourceViewer viewer, int line) {
		IAnnotationModel model= viewer.getAnnotationModel();
		IDocument document= viewer.getDocument();
		
		if (model == null)
			return null;
		
		List exact= new ArrayList();
		HashMap messagesAtPosition= new HashMap();
		
		Iterator e= model.getAnnotationIterator();
		while (e.hasNext()) {
			Annotation annotation= (Annotation) e.next();
			Position position= model.getPosition(annotation);
			if (position == null)
				continue;
			
			if (compareRulerLine(position, document, line) == 1) {
				if (isDuplicateMessage(messagesAtPosition, position, annotation.getText()))
					continue;
			
				exact.add(annotation);
			}
		}
		
		if (exact.size() < 1)
			return null;
		
		sort(exact, model);
		
		if (exact.size() > 0)
			setLastRulerMouseLocation(viewer, line);
		
		AnnotationHoverInput input= new AnnotationHoverInput();
		input.fAnnotations= (Annotation[]) exact.toArray(new Annotation[0]);
		input.fViewer= viewer;
		input.fRulerInfo= fVerticalRulerInfo;
		input.fAnnotationListener= fAnnotationListener;
		input.fDoubleClickListener= fDblClickListener;
		input.model= model;
		
		return input;
	}

	protected void sort(List exact, final IAnnotationModel model) {
		class AnnotationComparator implements Comparator {

			/*
			 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
			 */
			public int compare(Object o1, Object o2) {
				Annotation a1= (Annotation) o1;
				Annotation a2= (Annotation) o2;
				
				Position p1= model.getPosition(a1);
				Position p2= model.getPosition(a2);
				
				// annotation order:
				// primary order: by position in line
				// secondary: annotation importance 
				if (p1.offset == p2.offset)
					return getOrder(a2) - getOrder(a1);
				else
					return p1.offset - p2.offset;
			}
		}
		
		Collections.sort(exact, new AnnotationComparator());
		
	}
	
	protected int getOrder(Annotation annotation) {
		// standard implementation: check for marker annotation level
		return annotation.getLayer();
	}

	protected boolean isDuplicateMessage(Map messagesAtPosition, Position position, String message) {
		if (messagesAtPosition.containsKey(position)) {
			Object value= messagesAtPosition.get(position);
			if (message == null || message.equals(value))
				return true;

			if (value instanceof List) {
				List messages= (List)value;
				if  (messages.contains(message))
					return true;
				else
					messages.add(message);
			} else {
				ArrayList messages= new ArrayList();
				messages.add(value);
				messages.add(message);
				messagesAtPosition.put(position, messages);
			}
		} else
			messagesAtPosition.put(position, message);
		return false;
	}
	
	protected void setLastRulerMouseLocation(ISourceViewer viewer, int line) {
		// set last mouse activity in order to get the correct context menu
		if (fVerticalRulerInfo instanceof IVerticalRulerExtension) {
			StyledText st= viewer.getTextWidget();
			if (st != null && !st.isDisposed()) {
				if (viewer instanceof ITextViewerExtension3) {
					int widgetLine= ((ITextViewerExtension3)viewer).modelLine2WidgetLine(line);
					Point loc= st.getLocationAtOffset(st.getOffsetAtLine(widgetLine));
					((IVerticalRulerExtension)fVerticalRulerInfo).setLocationOfLastMouseButtonActivity(0, loc.y);
				} else if (viewer instanceof TextViewer) {
					// TODO remove once TextViewer implements the extension
					int widgetLine= ((TextViewer)viewer).modelLine2WidgetLine(line);
					Point loc= st.getLocationAtOffset(st.getOffsetAtLine(widgetLine));
					((IVerticalRulerExtension)fVerticalRulerInfo).setLocationOfLastMouseButtonActivity(0, loc.y);
				}
			}
		}
	}

	/**
	 * Returns the distance to the ruler line. 
	 */
	protected int compareRulerLine(Position position, IDocument document, int line) {
		
		if (position.getOffset() > -1 && position.getLength() > -1) {
			try {
				int firstLine= document.getLineOfOffset(position.getOffset());
				if (line == firstLine)
					return 1;
				if (firstLine <= line && line <= document.getLineOfOffset(position.getOffset() + position.getLength()))
					return 2;
			} catch (BadLocationException x) {
			}
		}
		
		return 0;
	}
	
	/*
	 * @see org.eclipse.jface.text.source.IAnnotationHoverExtension#getInformationControlCreator()
	 */
	public IInformationControlCreator getInformationControlCreator() {
		return fgCreator;
	}

	/*
	 * @see org.eclipse.jface.text.source.IAnnotationHoverExtension#getHoverInfo(org.eclipse.jface.text.source.ISourceViewer, int, int, int)
	 */
	public String getHoverInfo(ISourceViewer sourceViewer, int lineNumber, int first, int number) {
		// use default implementation
		return getHoverInfo(sourceViewer, lineNumber);
	}

	/*
	 * @see org.eclipse.jface.text.source.IAnnotationHoverExtension#getLineRange(org.eclipse.jface.text.source.ISourceViewer, int, int, int)
	 */
	public ITextSelection getLineRange(ISourceViewer viewer, int line, int first, int number) {
		// use default implementation
		return null;
	}

}
