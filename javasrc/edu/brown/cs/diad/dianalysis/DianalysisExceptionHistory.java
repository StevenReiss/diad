/********************************************************************************/
/*                                                                              */
/*              DianalysisExceptionHistory.java                                 */
/*                                                                              */
/*      Find flow starting point for exception being throws                     */
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



package edu.brown.cs.diad.dianalysis;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadDataType;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSymbol;
import edu.brown.cs.ivy.jcomp.JcompType;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisExceptionHistory extends DianalysisHistory
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String exception_type;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisExceptionHistory(DianalysisManager fac,DiadSymptom symp,DiadThread thrd)
{
   super(fac,symp,thrd);
   
   exception_type = symp.getSymptomItem();                                                             
}



/********************************************************************************/
/*                                                                              */
/*      Processing methods                                                      */
/*                                                                              */
/********************************************************************************/

@Override protected void process(IvyXmlWriter xw) throws DiadException
{
   getAnalysis().waitForAnalysis();
   
   String expr = getExceptionCause();
   if (expr == null)
      throw new DiadException("Can't find exception cause for " + exception_type);
   
   CommandArgs args = new CommandArgs("QTYPE","EXPRESSION");
   args = addCommandArgs(args);
   
   String sxml = getXmlForStack();
   if (sxml != null) expr += sxml;
   
   Element rslt = getAnalysis().sendFaitMessage("FLOWQUERY",args,expr);
   outputGraph(rslt,xw);
}




/********************************************************************************/
/*                                                                              */
/*      Get expression causing the exception                                    */
/*                                                                              */
/********************************************************************************/

ASTNode getExceptionNode() 
{
   try {
      ASTNode stmt = getSourceStatement();
      
      ExceptionChecker checker = null;
      switch (exception_type) {
         case "java.lang.NullPointerException" :
            checker = new NullPointerChecker();
            break;
         case "java.lang.ArrayIndexOutOfBoundsException" :
            checker = new ArrayIndexOutOfBoundsChecker();
            break;
         case "java.lang.IndexOutOfBoundsException" :
         case "java.util.NoSuchElementException" :
            checker = new IndexOutOfBoundsChecker();
            break;
         case "java.lang.StringIndexOutOfBoundsException" :
            checker = new StringIndexOutOfBoundsChecker();
            break;
         case "java.lang.StackOverflowError" :
            checker = new StackOverflowChecker();
            break;
         case "java.lang.ClassCastException" :
            checker = new ClassCastChecker();
            break;
       }
      
      if (checker != null && stmt != null) {
         checker.doCheck(stmt);
         return checker.getResult();
       }
    }
   catch (DiadException e) { }
   
   return null;
}



private String getExceptionCause() throws DiadException
{
   ASTNode stmt = getSourceStatement();
   
   if (exception_type == null || stmt == null) return null;
   
   ExceptionChecker checker = null;
   if (stmt instanceof ThrowStatement) {
      checker = new ThrowChecker();
    }
   else {
      switch (exception_type) {
         case "java.lang.NullPointerException" :
            checker = new NullPointerChecker();
            break;
         case "java.lang.ArrayIndexOutOfBoundsException" :
            checker = new ArrayIndexOutOfBoundsChecker();
            break;
         case "java.lang.IndexOutOfBoundsException" :
         case "java.util.NoSuchElementException" :
            checker = new IndexOutOfBoundsChecker();
            break;
         case "java.lang.StringIndexOutOfBoundsException" :
            checker = new StringIndexOutOfBoundsChecker();
            break;
         case "java.lang.StackOverflowError" :
            checker = new StackOverflowChecker();
            break;
         case "java.lang.ClassCastException" :
            checker = new ClassCastChecker();
            break;
       }
    }
   
   if (checker != null) {
      checker.doCheck(stmt);
      String loc = checker.generateResult();
      if (loc != null) return loc;
    }
   
   return null;
}


private abstract class ExceptionChecker extends ASTVisitor {
   
   private ASTNode use_node;
   private String orig_value;
   private String target_value;
   
   ExceptionChecker() {
      use_node = null;
      orig_value = null;
      target_value = null;
    }
   
   void doCheck(ASTNode n) {
      n.accept(this);
    }
   
   protected void useNode(ASTNode n,String orig,String tgt) {
      if (use_node == null) {
         use_node = n;
         orig_value = orig;
         target_value = tgt;
       }
    }
   
   protected boolean haveNode() {
      return use_node != null;
    }
   
   String generateResult() {
      if (use_node == null) return null;
      if (orig_value != null) getSymptom().setOriginalValue(orig_value);
      if (target_value != null) getSymptom().setTargetValue(target_value);
      return getXmlForLocation("EXPR",use_node,true);
    }
   
   ASTNode getResult() {
      return use_node; 
    }
   
}       // end of inner class ExceptionChecker




/********************************************************************************/
/*                                                                              */
/*      Checker for null pointer exceptions                                     */
/*                                                                              */
/********************************************************************************/

private final class NullPointerChecker extends ExceptionChecker {
   
   @Override public void endVisit(ArrayAccess aa) {
      checkForNull(aa.getIndex());
      checkForNull(aa.getArray());
    }
   
   @Override public void endVisit(FieldAccess fa) {
      checkForNull(fa.getExpression());
    }
   
   @Override public void endVisit(MethodInvocation mi) {
      checkForNull(mi.getExpression());
    }
   
   @Override public boolean visit(InfixExpression ex) {
      if (haveNode()) return false;
      if (ex.getOperator() == InfixExpression.Operator.CONDITIONAL_AND) {
         checkAndAnd(ex);
         return false;
       }
      else if (ex.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) {
         checkOrOr(ex);
         return false;
       } 
      return true;
    }
   
   @Override public void endVisit(InfixExpression ex) {
      if (ex.getOperator() == InfixExpression.Operator.PLUS) {
         checkPlus(ex);
       }
      else if (ex.getOperator() == InfixExpression.Operator.EQUALS) ;
      else if (ex.getOperator() == InfixExpression.Operator.NOT_EQUALS) ;
      else {
         checkForNull(ex.getLeftOperand());
         checkForNull(ex.getRightOperand());
         for (Object o : ex.extendedOperands()) {
            Expression ope = (Expression) o;
            checkForNull(ope);
          }
       }
    }
   
   @Override public void endVisit(SwitchStatement ss) {
      checkForNull(ss.getExpression());
    }
   
   @Override public void endVisit(WhileStatement ws) {
      checkForNull(ws.getExpression());
    }
   
   @Override public void endVisit(IfStatement fs) {
      checkForNull(fs.getExpression());
    }
   
   @Override public void endVisit(EnhancedForStatement fs) {
      checkForNull(fs.getExpression());
    }
   
   @Override public boolean visit(IfStatement is) {
      if (haveNode()) return false;
      boolean fg = checkBoolean(is.getExpression());
      if (haveNode()) return false;
      if (fg) {
         is.getThenStatement().accept(this);
       }
      else if (is.getElseStatement() != null) {
         is.getElseStatement().accept(this);
       }
      return false;
    }
   
   @Override public void endVisit(DoStatement ds) {
      checkForNull(ds.getExpression()); 
    }
   
   @Override public boolean visit(ConditionalExpression ce) {
      if (haveNode()) return false;
      boolean fg = checkBoolean(ce.getExpression());
      if (haveNode()) return false;
      if (fg) {
         ce.getThenExpression().accept(this);
       }
      else {
         ce.getElseExpression().accept(this);
       }
      return false;
    }
   
   
   private void checkPlus(InfixExpression ex) { }
   
   private void checkAndAnd(InfixExpression ex) { 
      if (haveNode()) return;
      if (!checkBoolean(ex.getLeftOperand())) return;
      if (haveNode()) return;
      if (!checkBoolean(ex.getRightOperand())) return;
      if (haveNode()) return;
      for (Object o : ex.extendedOperands()) {
         Expression eop = (Expression) o;
         if (haveNode()) return;
         if (!checkBoolean(eop)) return;
       }
    }
   
   private void checkOrOr(InfixExpression ex) { 
      if (haveNode()) return;
      if (checkBoolean(ex.getLeftOperand())) return;
      if (haveNode()) return;
      if (checkBoolean(ex.getRightOperand())) return;
      for (Object o : ex.extendedOperands()) {
         Expression eop = (Expression) o;
         if (haveNode()) return;
         if (checkBoolean(eop)) return;
       }
    }
   
   private boolean checkBoolean(Expression ex) {
      ex.accept(this);
      if (haveNode()) return false;
      DiadValue bv = evaluate(ex.toString());
      if (bv == null) return false;
      if (bv.isNull()) {
         useNode(ex,"null","Non-Null");
         return false;
       }
      
      return bv.getBoolean();
    }
   
   private void checkForNull(Expression ex) {
      if (ex == null || haveNode()) return;
      DiadValue bv = evaluate(ex.toString());
      if (bv != null && bv.isNull()) {
         useNode(ex,"null","Non-Null");
       }
    }
   
}       // end of inner class NullPointerChecker dc




/********************************************************************************/
/*                                                                              */
/*      Checker for index out of bounds                                         */
/*                                                                              */
/********************************************************************************/

private final class ArrayIndexOutOfBoundsChecker extends ExceptionChecker {

@Override public void endVisit(ArrayAccess aa) {
   DiadValue bv = evaluate("(" + aa.getArray().toString() + ").length");
   if (bv == null) return;
   long bnd = bv.getInt();
   DiadValue abv = evaluate(aa.getIndex().toString());
   if (abv == null) return;
   long idx = abv.getInt();
   if (idx < 0 || idx >= bnd) useNode(aa,Long.toString(idx),null);
}

}       // end of inner class ArrayIndexOutOfBoundsChecker



private final class IndexOutOfBoundsChecker extends ExceptionChecker {
   
   @Override public void endVisit(MethodInvocation mi) {
      if (mi.getExpression() == null) return;
      JcompType jt = JcompAst.getExprType(mi.getExpression());
      String jtname = jt.getName();
      int idx0 = jtname.indexOf("<");
      if (idx0 > 0) jtname = jtname.substring(0,idx0);
      switch (jtname) {
         case "java.util.ArrayList" :
         case "java.util.List" :
         case "java.util.Vector" :
         case "java.util.Queue" :
         case "java.util.ArrayDeque" :
         case "java.util.LinkedList" :
         case "java.util.PriorityQueue" :
         case "java.util.Deque" :
            break;
         default :
            return;
       }
      switch (mi.getName().getIdentifier()) {
         case "get" :
         case "remove" :
         case "removeFirst" :
         case "removeLast" :
            break;
         default :
            return;
       }
      
      DiadValue bv = evaluate("(" + mi.getExpression() + ").size()");
      if (bv == null) return;
      long bnd = bv.getInt();
      List<?> args = mi.arguments();
      long idx = 0;
      if (args.size() > 0) {
         Expression eidx = (Expression) args.get(0);
         DiadValue bidx = evaluate(eidx.toString());
         if (bidx == null && bnd > 0) return;
         if (bidx != null) idx = bidx.getInt();
       }
      if (idx < 0 || idx >= bnd) useNode(mi,Long.toString(idx),null);
    }
   
}       // end of inner class IndexOutOfBoundsChecker


private final class StringIndexOutOfBoundsChecker extends ExceptionChecker {
   
   @Override public void endVisit(MethodInvocation mi) {
      JcompType jt = JcompAst.getExprType(mi.getExpression());
      String jtname = jt.getName();
      int idx0 = jtname.indexOf("<");
      if (idx0 > 0) jtname = jtname.substring(0,idx0);
      String ex = null;
      switch (jtname) {
         case "java.lang.String" :
            ex = mi.getExpression().toString();
            switch (mi.getName().getIdentifier()) {
               case "charAt" :
               case "codePointAt" :
               case "codePointBefore" :
               case "codePointCount" :
               case "offsetByCodeePoints" :
               case "getBytes" :
               case "substring" :
               case "subSequence" :
                  checkIndex(mi,ex,0);
                  break;
               case "getChars" :
                  useNode(mi,null,null);
                  break;
               default :
                  return;
             }
            break;
         case "java.lang.Character" :
            switch (mi.getName().getIdentifier()) {
               case "codePointAt" :
                  ex = mi.arguments().get(0).toString();
                  if (ex != null) checkIndex(mi,ex,1);
                  break;
               default :
                  return;
             }
            break;
         default :
            return;
       }
    }
   
   private boolean checkIndex(MethodInvocation mi,String ex,int arg) {
      DiadValue bv = evaluate("(" + ex + ").length()");
      if (bv == null) bv = evaluate("(" + ex + ").length");
      if (bv == null) return false;
      long bnd = bv.getInt();
      List<?> args = mi.arguments();
      long idx = 0;
      if (args.size() > arg) {
         Expression eidx = (Expression) args.get(arg);
         DiadValue bidx = evaluate(eidx.toString());
         if (bidx == null && bnd > 0) return false;
         if (bidx != null) idx = bidx.getInt();
       }
      if (idx < 0 || idx >= bnd) {
         useNode(mi,Long.toString(idx),null);
         return true;
       }
      return false;
    }
   
}       // end of inner class StringIndexOutOfBoundsChecker


/********************************************************************************/
/*                                                                              */
/*      Class Cast Exception checker                                            */
/*                                                                              */
/********************************************************************************/

private final class ClassCastChecker extends ExceptionChecker {

@Override public void endVisit(CastExpression c) {
   DiadValue cbv = evaluate(c.getExpression().toString());
   if (cbv == null) return;
   DiadDataType bt = cbv.getDataType();
   JcompType jt = JcompAst.getJavaType(c.getType());
   if (jt.getName().equals(bt.getName())) return;
   useNode(c.getExpression(),bt.toString(),null);   
}

}       // end of inner class ClassCastChecker





/********************************************************************************/
/*                                                                              */
/*      Stack Overflow handler                                                  */
/*                                                                              */
/********************************************************************************/

private class StackOverflowChecker extends ExceptionChecker {
   
   private String find_method;
   private String find_signature;
   
   StackOverflowChecker() {
      find_method = null;
      find_signature = null;
    }
   
   @Override void doCheck(ASTNode stmt) {
      DiadStack stk = getThread().getStack();
      Map<String,Integer> cnts = new HashMap<>();
      for (DiadStackFrame frm : stk.getFrames()) {
         File f = frm.getSourceFile();
         int lno = frm.getLineNumber();
         String mthd = frm.getMethodName();
         String sgn = frm.getMethodSignature();
         String key = f.getPath() + "@" + lno + "@" + mthd + "@" + sgn;
         Integer v = cnts.get(key);
         if (v == null) v = 0;
         cnts.put(key,v+1);
       }
      String most = null;
      int mostv = 0;
      for (Map.Entry<String,Integer> ent : cnts.entrySet()) {
         if (ent.getValue() > mostv) {
            most = ent.getKey();
            mostv = ent.getValue();
          }
       }
      String [] items = most.split("@");
      Integer lno = Integer.parseInt(items[1]);
      ASTNode n = getSourceManager().getSourceNode(getProject(),
            new File(items[0]),-1,lno,true,true);
      find_method = items[2];
      find_signature = items[3];
      n.accept(this);     
    }
   
   @Override public boolean visit(MethodInvocation mi) {
      if (mi.getName().getIdentifier().equals(find_method)) {
         JcompSymbol js = JcompAst.getReference(mi.getName());
         String ftynm = js.getType().getJavaTypeName();
         if (find_signature == null || find_signature.equals(ftynm)) {
            useNode(mi,null,null);
          }
       }
      return true;
    }

}       // end of inner class StackOverflowChecker



/********************************************************************************/
/*                                                                              */
/*      Throw checker -- handle explicit throws                                 */
/*                                                                              */
/********************************************************************************/

private class ThrowChecker extends ExceptionChecker {
   
   ThrowChecker() {}
   
   @Override void doCheck(ASTNode stmt) {
      ASTNode prev = stmt;
      for (ASTNode p = stmt.getParent(); p != null; p = p.getParent()) {
         if (p instanceof IfStatement) {
            IfStatement ifstmt = (IfStatement) p;
            Boolean val = null;
            if (prev == ifstmt.getThenStatement()) val = true;
            else if (prev == ifstmt.getElseStatement()) val = false;
            String oval = (prev == null ? null : Boolean.toString(val));
            String tval = (prev == null ? null : Boolean.toString(!val));
            useNode(ifstmt.getExpression(),oval,tval);
            break;
          }
         // TODO: should check for previous statement with break/continue in a block
         prev = p;
       }
    }

}       // end of inner class StackOverflowChecker




/********************************************************************************/
/*                                                                              */
/*      Evaluate an expression in the current frame                             */
/*                                                                              */
/********************************************************************************/

private DiadValue evaluate(String expr) {
   return getThread().evaluate(expr);
}


}       // end of class DianalysisExceptionHistory




/* end of DianalysisExceptionHistory.java */

