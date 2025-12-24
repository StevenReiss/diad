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
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;

import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.disource.DisourceManager;

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
         return new DicontrolSymptom(DiadSymptomType.ASSERTION);
       }
      else {
         return new DicontrolSymptom(DiadSymptomType.EXCEPTION,exc);
       }
    }
   
   DisourceManager srcfac = diad_control.getSourceManager();
   ASTNode stmt = srcfac.getSourceNode(null,frm.getSourceFile(),
         0,frm.getLineNumber(),false,true);
   
   DicontrolSymptom fnd = checkErrorStatement(stmt);
   if (fnd != null) return fnd;
   fnd = checkCaughtExcpetion(stmt);
   if (fnd != null) return fnd;
   fnd = checkDefensiveIf(stmt);
   if (fnd != null) return fnd;
   fnd = checkDefensiveCase(stmt);
   if (fnd != null) return fnd;
   
   return null;
}


/********************************************************************************/
/*                                                                              */
/*      Check for potential errors at this point                                */
/*                                                                              */
/********************************************************************************/

private DicontrolSymptom checkErrorStatement(ASTNode stmt)
{
   switch (stmt.getNodeType()) {
      case ASTNode.THROW_STATEMENT :
         return new DicontrolSymptom(DiadSymptomType.LOCATION);
    }
   
   return null;
}


/********************************************************************************/
/*                                                                              */
/*      Check for exception handling                                            */
/*                                                                              */
/********************************************************************************/

private DicontrolSymptom checkCaughtExcpetion(ASTNode stmt)
{
   ASTNode par = stmt.getParent();
   if (par.getNodeType() == ASTNode.BLOCK) {
      ASTNode spar = par.getParent();
      if (spar.getNodeType() == ASTNode.CATCH_CLAUSE) {
         CatchClause cc = (CatchClause) spar;
         SingleVariableDeclaration svd = cc.getException();
         String ename = svd.getName().getIdentifier();
         return new DicontrolSymptom(DiadSymptomType.CAUGHT_EXCEPTION,ename);
       }
    }
   
   return null;
}


/********************************************************************************/
/*                                                                              */
/*      Check for defensive code                                                */
/*                                                                              */
/********************************************************************************/

private DicontrolSymptom checkDefensiveIf(ASTNode stmt)
{
   ASTNode par = stmt.getParent();
   if (par.getNodeType() != ASTNode.BLOCK) return null;
   Block blk = (Block) par; 
   ASTNode spar = par.getParent();
   if (spar.getNodeType() != ASTNode.IF_STATEMENT) return null;
   
   boolean isok = false;
   for (Object o1 : blk.statements()) {
      Statement s1 = (Statement) o1;
      switch (s1.getNodeType()) {
         case ASTNode.EMPTY_STATEMENT :
         case ASTNode.RETURN_STATEMENT :
         case ASTNode.THROW_STATEMENT :
         case ASTNode.CONTINUE_STATEMENT :
         case ASTNode.BREAK_STATEMENT :
            break;
         case ASTNode.EXPRESSION_STATEMENT :
            if (isErrorStatement(s1)) isok = true;
            else return null;
            break;
         default :
            return null;
       }
    }
   
   if (isok) {
      return new DicontrolSymptom(DiadSymptomType.LOCATION); 
    }
   
   return null;
}


private DicontrolSymptom checkDefensiveCase(ASTNode stmt)
{
   ASTNode par = stmt.getParent();
   if (par.getNodeType() != ASTNode.SWITCH_STATEMENT) return null;
   
   SwitchStatement ss = (SwitchStatement) par;
   Statement start = null;
   Statement end = null;
   Statement laststart = null;
   for (Object o1 : ss.statements()) {
      Statement s1 = (Statement) o1;
      if (s1.getNodeType() == ASTNode.SWITCH_CASE) {
         if (start != null && end == null) {
            end = s1;
          }
         else if (start == null) {
            laststart = s1;
          }
       }
      else if (s1 == stmt) {
         start = laststart;
       }   
    }
   
   if (start == null) return null;
   
   boolean check = false;
   boolean isok = false;
   for (Object o2 : ss.statements()) {
      Statement s2 = (Statement) o2;
      if (s2 == start) check = true;
      else if (check && s2 == end) {
         break;
       }
      switch (s2.getNodeType()) {
         case ASTNode.SWITCH_CASE :
            return null;
         case ASTNode.EMPTY_STATEMENT :
         case ASTNode.RETURN_STATEMENT :
         case ASTNode.THROW_STATEMENT :
         case ASTNode.CONTINUE_STATEMENT :
         case ASTNode.BREAK_STATEMENT :
            isok = true;
            break;
         case ASTNode.EXPRESSION_STATEMENT :
            if (!isErrorStatement(s2)) return null;
            break;
         default :
            return null;
       } 
    }
   
   if (isok) {
      return new DicontrolSymptom(DiadSymptomType.LOCATION);
    }
   
   return null;
}


private boolean isErrorStatement(Statement s)
{
   if (s.getNodeType() != ASTNode.EXPRESSION_STATEMENT) return false;
   
   String cnts = s.toString();
   if (cnts.contains("Log") || cnts.contains("log")) return true;
   if (cnts.contains(".print")) return true;
   
   return false;
}


}       // end of class DicontrolSymptomFinder




/* end of DicontrolSymptomFinder.java */

