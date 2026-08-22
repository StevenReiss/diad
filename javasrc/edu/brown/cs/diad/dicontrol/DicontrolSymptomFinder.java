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

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SwitchStatement;

import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadLocalVariable;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.file.IvyLog;

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
   DisourceManager srcfac = diad_control.getSourceManager();
   String exc = for_thread.getExceptionType(); 
   String excmsg = for_thread.getExceptionDetail();
   
   ASTNode stmt = srcfac.getSourceNode(null,frm.getSourceFile(),
         -1,frm.getLineNumber(),false,true);
   IvyLog.logD("DICONTROL","Found statement " + frm.getSourceFile() + " " +
         frm.getLineNumber() + " " + stmt);
   
   if (stmt == null) {
      IvyLog.logE("DICONTROL","No statement found for " + 
            frm.getSourceFile() + " " + frm.getLineNumber());
      
      ASTNode n = srcfac.getSourceNode(null,frm.getSourceFile(),
            -1,frm.getLineNumber(),false,false);
      
      IvyLog.logI("DICONTROL","AST Node found: " + n);
    }
   
   return findStatementSymptom(frm,stmt,exc,excmsg,null,null);
}


private DicontrolSymptom findStatementSymptom(DiadStackFrame frm,ASTNode stmt,
      String exc,String detail,String msg,String libcall)
{
   if (exc != null && frm != null && for_frame != null &&
         frm.getFrameId().equals(for_frame.getFrameId())) {
      if (ASSERTION_EXCEPTIONS.contains(exc)) { 
         if (stmt.getNodeType() == ASTNode.ASSERT_STATEMENT) {
            AssertStatement astmt = (AssertStatement) stmt;
            if (astmt.getExpression().toString().equals("false")) {
               return new DicontrolSymptom(DiadSymptomType.LOCATION);
             }
          }
         else if (stmt.toString().contains("fail(")) {
            return new DicontrolSymptom(DiadSymptomType.LOCATION);
          }
         DicontrolSymptom symp = new DicontrolSymptom(DiadSymptomType.ASSERTION);
         if (detail != null) symp.setSymptomDetail(detail);
         return symp;
       }
      else {
         DicontrolSymptom symp = new DicontrolSymptom(DiadSymptomType.EXCEPTION,exc);
         if (libcall != null) {
            symp.setSymptomType(DiadSymptomType.LIBRARY_EXCEPTION);
            symp.setOriginalExpression(libcall);
          }
         if (detail != null) symp.setSymptomDetail(detail);
         return symp;
       }
    }
   else if (frm == null && exc != null) {
      DicontrolSymptom symp = new DicontrolSymptom(DiadSymptomType.EXCEPTION,exc);
      if (stmt != null) {
         if (libcall != null) {
            symp.setSymptomType(DiadSymptomType.LIBRARY_EXCEPTION);
            symp.setOriginalExpression(libcall);
          }
         symp.setLocation(stmt);
         if (stmt.getNodeType() == ASTNode.THROW_STATEMENT) {
            symp.setSymptomType(DiadSymptomType.LOCATION);
          }
       }
      if (detail != null) symp.setSymptomDetail(detail);
      return symp;
    }
   
   if (stmt == null) {
      IvyLog.logE("DICONTROL","No statement found for " + frm.getSourceFile() + 
            " " + frm.getLineNumber());
      return null;
    }
   
   IvyLog.logD("DICONTROL","Check symptom statement " + stmt);
   
   DicontrolSymptom fnd = checkCaughtException(stmt);
   if (fnd == null) {
      fnd = checkErrorStatement(stmt);
    }
   if (fnd == null) {
      fnd = checkDefensiveIf(stmt);
    }
   if (fnd == null) {
      fnd = checkDefensiveCase(stmt);
    }
   // possibly check for previous exception if in a catch clause
   
   if (fnd != null && frm == null) {
      fnd.setLocation(stmt);
    }
   
   return fnd;
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

private DicontrolSymptom checkCaughtException(ASTNode stmt)
{
   ASTNode par = stmt.getParent();
   if (par.getNodeType() == ASTNode.BLOCK) {
      ASTNode spar = par.getParent();
      if (spar.getNodeType() == ASTNode.CATCH_CLAUSE) {
         IvyLog.logD("DICONTROL","INSIDE CATCH " + stmt + " @ " + spar);
//       CatchClause cc = (CatchClause) spar;
//       SingleVariableDeclaration svd = cc.getException();
//       String ename = svd.getType().toString();
         String excvar = null;
         DiadStackFrame frm = for_stack.getUserFrame();
         for (String vnm : frm.getLocals()) {
            DiadLocalVariable var = frm.getLocal(vnm);
            String typ = var.getType();
            if (typ.contains("Exception") || 
                  typ.contains("Error") ||
                  typ.contains("Throwable")) {
               excvar = vnm;
//             ename = typ;
               // want to use the last one in the list that is an exception 
             }
          } 
         return getExceptionSymptom(excvar);
       }
    }
   
   return null;
}


private DicontrolSymptom getExceptionSymptom(String excvar)
{
   if (excvar == null) return null;
   
   String expr = excvar;
   for ( ; ; ) {
      DiadValue v0 = for_thread.evaluate(expr + ".getCause()");
      if (v0.isNull()) break;
      expr = expr + ".getCause()";
    }
   
   DiadValue v0 = for_thread.evaluate(expr);
   String exc = v0.getDataType().getName();
   String msg = for_thread.evaluate(expr + ".getMessage()").getString();
   DiadValue v1 = for_thread.evaluate(expr + ".getStackTrace()");
   try {
      DisourceManager src = diad_control.getSourceManager();
      int len = v1.getArrayLength(); 
      String libcall = null;
      for (int i = 0; i < len; ++i) {
         DiadValue vf = v1.getArrayElement(i);
//       String cls = vf.getFieldValue("declaringClass").getString();
//       String mthd = vf.getFieldValue("methodName").getString();
         String filename = vf.getFieldValue("fileName").getString();
         File file = src.findProjectFile(filename); 
         if (file == null) {
            libcall = vf.getFieldValue("methodName").getString();
            continue;
          }
         String proj = src.getProjectForFile(file);
         if (proj == null) continue;
         // need to get actual file
         int lno = (int) vf.getFieldValue("lineNumber").getInt();
         ASTNode stmt = src.getSourceNode(proj,file,
               -1,lno,true,true);
         IvyLog.logD("DICONTROL","Work on caught exception " + stmt + " " +
               libcall);
         if (stmt !=  null) {
            DicontrolSymptom symp = findStatementSymptom(null,stmt,
                  exc,msg,msg,libcall);
            if (symp != null) return symp;
          }
       }
    }
   catch (DiadException t) { 
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
   DicontrolSymptom rslt = null;
   
   ASTNode par = stmt.getParent();
   if (par.getNodeType() != ASTNode.BLOCK) return rslt;
   Block blk = (Block) par; 
   ASTNode spar = par.getParent();
   if (spar.getNodeType() != ASTNode.IF_STATEMENT) return rslt;
   IfStatement ifstmt = (IfStatement) spar;
   ASTNode stmt0 = null;
   
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
            if (isErrorStatement(s1)) {
               stmt0 = s1;
               break;
             }
            else return null;
         default :
            return null;
       }
    }
   
   if (stmt0 != null) {
      rslt = new DicontrolSymptom(DiadSymptomType.LOCATION); 
      updateLocationSymptom(rslt,stmt0);       
      rslt.setOriginalExpression(ifstmt.getExpression().toString());
    }
   
   return rslt;
}


private void updateLocationSymptom(DicontrolSymptom symp,ASTNode stmt)
{
   IvyLog.logD("DICONTROL","Check location symptom " + stmt);
   if (stmt.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
      LocationText lt = new LocationText();
      stmt.accept(lt);
      String txt = lt.getOutputText();
      IvyLog.logD("DICONTROL","Location text result: " + txt);
      if (txt != null && !txt.isBlank()) {
         symp.setSymptomDetail(txt);
       }
    }
}



private class LocationText extends ASTVisitor {
   
   private StringBuffer output_text;
   private int level_count;
   
   LocationText() {
      output_text = new StringBuffer();
      level_count = 0;
    }
   
   String getOutputText() {
      return output_text.toString();
    }
   
   @Override public boolean visit(MethodInvocation mi) {
      if (level_count == 0) {
         level_count = 1;
         for (Object o : mi.arguments()) {
            ASTNode arg = (ASTNode) o;
            IvyLog.logD("DICONTROL","Location arg " + arg);
            // might want to evaluate the expression here
            arg.accept(this);
          }
         output_text.append("\n");
         level_count = 0;
       } 
      return false;
    }
   
   @Override public boolean preVisit2(ASTNode n) {
      if (level_count == 0) return true;
      if (n instanceof Expression) {
         switch (n.getNodeType()) {
            case ASTNode.STRING_LITERAL :
            case ASTNode.CHARACTER_LITERAL :
            case ASTNode.NULL_LITERAL :
            case ASTNode.NUMBER_LITERAL :
               return true;
            default :
               DiadValue v = for_thread.evaluate("String.valueOf(" + n.toString() + ")");
               if (v != null) {
                  output_text.append(v.getString());
                  output_text.append(" ");
                }
               return false;
          }
       }
      return true;
    }
   
   @Override public void endVisit(StringLiteral nd) {
      output_text.append(nd.getLiteralValue());
    }
   
   @Override public void endVisit(CharacterLiteral nd) {
      output_text.append(nd.charValue());
    }
   
   @Override public void endVisit(NullLiteral nd) {
      output_text.append("null");
    }
   
   @Override public void endVisit(NumberLiteral nd) {
      output_text.append(nd.getToken());
    }
   
}       // end of inner class LocationText

   




private DicontrolSymptom checkDefensiveCase(ASTNode stmt)
{
   DicontrolSymptom rslt = null;
   
   ASTNode par = stmt.getParent();
   if (par.getNodeType() != ASTNode.SWITCH_STATEMENT) return rslt;
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
   
   if (start == null) return rslt;
   
   boolean check = false;
   boolean isok = false;
   ASTNode stmt2 = null;
   for (Object o2 : ss.statements()) {
      Statement s2 = (Statement) o2;
      if (s2 == start) check = true;
      else if (check && s2 == end) {
         break;
       }
      else if (!check) continue;
      switch (s2.getNodeType()) {
         case ASTNode.SWITCH_CASE :
            return rslt;
         case ASTNode.EMPTY_STATEMENT :
         case ASTNode.RETURN_STATEMENT :
         case ASTNode.THROW_STATEMENT :
         case ASTNode.CONTINUE_STATEMENT :
         case ASTNode.BREAK_STATEMENT :
            isok = true;
            break;
         case ASTNode.EXPRESSION_STATEMENT :
            if (!isErrorStatement(s2)) return rslt;
            stmt2 = s2;
            break;
         default :
            return null;
       } 
    }
   
   if (isok && stmt2 != null) {
      rslt = new DicontrolSymptom(DiadSymptomType.LOCATION);
      updateLocationSymptom(rslt,stmt2);
      rslt.setOriginalExpression("*SWITCH*");
    }
   
   return rslt;
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

