/********************************************************************************/
/*                                                                              */
/*              DicontrolSymptomFinder.java                                     */
/*                                                                              */
/*      Find symptom for given thread                                           */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2025 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 * This program and the accompanying materials are made available under the      *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at                                                           *
 *      http://www.eclipse.org/legal/epl-v10.html                                *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.diad.dicontrol;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;

import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.disource.DisourceFactory;

class DicontrolSymptomFinder implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private DiadThread      for_thread;
private DiadStack       for_stack;
private DiadStackFrame  for_frame;

private static final Set<String> ASSERTION_EXCEPTIONS;

static {
   ASSERTION_EXCEPTIONS = new HashSet<>();
   ASSERTION_EXCEPTIONS.add("java.lang.AssertionError");
   ASSERTION_EXCEPTIONS.add("org.junit.ComparisonFailure");
   ASSERTION_EXCEPTIONS.add("junit.framework.AssertionFailedError");
   ASSERTION_EXCEPTIONS.add("junit.framework.ComparisonFailure");
   ASSERTION_EXCEPTIONS.add("org.junit.AssumpptionViolatedException");
}


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolSymptomFinder(DicontrolMain ctrl,DiadThread th,DiadStack stack,DiadStackFrame frm)
{
   diad_control = ctrl;
   for_thread = th;
   for_stack = stack;
   for_frame = frm;
}



/********************************************************************************/
/*                                                                              */
/*      Processing methods                                                      */
/*                                                                              */
/********************************************************************************/

DiadSymptom findSymptom()
{
   DiadStackFrame frm = for_stack.getUserFrame();
   String exc = for_thread.getExceptionType(); 
   if (exc != null && frm != null && for_frame != null &&
         frm.getFrameId().equals(for_frame.getFrameId())) {
      if (ASSERTION_EXCEPTIONS.contains(exc)) {
         // return assertion failed symptom
       }
      else {
         // return exception thrown symptom
       }
    }
   
   DisourceFactory srcfac = diad_control.getSourceManager();
   ASTNode stmt = srcfac.getSourceNode(null,frm.getSourceFile(),
         0,frm.getLineNumber(),false,true);
   switch (stmt.getNodeType()) {
      case ASTNode.THROW_STATEMENT :
         // return shouldn't be here symptom
         break;
    }
   String cnts = stmt.toString();
   if (cnts.contains("Log") || cnts.contains(".print")) {
      ASTNode par = stmt.getParent();
      if (par.getNodeType() == ASTNode.IF_STATEMENT) {
         // return shouldn't be here symptom
       }
    }
   
   return null;
}

}       // end of class DicontrolSymptomFinder




/* end of DicontrolSymptomFinder.java */

