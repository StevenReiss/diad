/********************************************************************************/
/*                                                                              */
/*              DianalysisAssertionHistory.java                                 */
/*                                                                              */
/*      Find starting point for assertion failure                               */
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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadAssertionData;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompType;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXmlWriter;


class DianalysisAssertionHistory extends DianalysisHistory
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private static int THIS_INDEX = 999999;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisAssertionHistory(DianalysisManager fac,DiadSymptom symp,DiadThread thrd)
{
  super(fac,symp,thrd);
}


/********************************************************************************/
/*                                                                              */
/*      Handle assertion history                                                */
/*                                                                              */
/********************************************************************************/

@Override protected void process(IvyXmlWriter xw) throws DiadException 
{
   getAnalysis().waitForAnalysis();
   
   ASTNode stmt = getSourceStatement();
   AssertionChecker checker = new AssertionChecker();
   if (stmt != null) stmt.accept(checker);
   
   String expr = checker.generateResult();
   
   if (expr == null) throw new DiadException("Can't find exception cause"); 
   
   CommandArgs args = new CommandArgs("QTYPE","EXPRESSION");
   args = addCommandArgs(args);
   
   String sxml = getXmlForStack();
   if (sxml != null) expr += sxml;
   
   Element rslt = getAnalysis().sendFaitMessage("FLOWQUERY",args,expr); 
   outputGraph(rslt,xw);  
}



/********************************************************************************/
/*                                                                              */
/*      Get informationn about the assertion                                    */
/*                                                                              */
/********************************************************************************/

DiadAssertionData getAssertionData()
{
   getAnalysis().waitForAnalysis(); 
   
   try {
      ASTNode stmt = getSourceStatement();
      if (stmt == null) return null;
      AssertionChecker checker = new AssertionChecker();
      stmt.accept(checker);
      if (checker.getExpression() == null) return null;
      return checker;
    }
   catch (DiadException e) { }
   
   return null;
}


/********************************************************************************/
/*                                                                              */
/*      Assertion checker                                                       */
/*                                                                              */
/********************************************************************************/

private class AssertionChecker extends ASTVisitor implements DiadAssertionData { 
   
   private ASTNode use_node;
   private String orig_value;
   private String target_value;
   private boolean is_location;
   
   AssertionChecker() {
      use_node = null;
      orig_value = null;
      target_value = null;
      is_location = false;
    }
   
   String generateResult() {
      if (use_node == null) return null;
      if (orig_value != null) getSymptom().setOriginalValue(orig_value);
      if (target_value != null) getSymptom().setTargetValue(target_value); 
      // should set precision
      return getXmlForLocation("EXPR",use_node,true);
    }
   
   @Override public ASTNode getExpression()     { return use_node; } 
   @Override public String getOriginalValue()   { return orig_value; }
   @Override public String getTargetValue()     { return target_value; }
   @Override public boolean isLocation()        { return is_location; }
   
   private void useNode(ASTNode n,String orig,String tgt) {
      if (use_node == null) {
         use_node = n;
         orig_value = orig;
         target_value = tgt;
       }
    }
   
   @Override public boolean visit(MethodInvocation mi) {
      String nm = mi.getName().getIdentifier();
      int ct = mi.arguments().size();
      int givenidx = -1;
      int targetidx = -1;
      switch (nm) {
         case "equals" :
            givenidx = THIS_INDEX;
            targetidx = 0;
            break;
         case "assertArrayEquals" :
            break;
         case "assertEquals" :
         case "assertSame" :
         case "assertNotEquals" :
         case "assertNotSame" :
            if (ct == 2) { 
               givenidx = 0;
               targetidx = 1;
             }
            else if (ct == 3) {
               ASTNode arg1 = (ASTNode) mi.arguments().get(1);
               JcompType t1 = JcompAst.getExprType(arg1);
               if (t1.isFloatingType()) {
                  givenidx = 0;
                  targetidx = 1;
                }
               else {
                  givenidx = 1;
                  targetidx = 2;
                }
             }
            else if (ct == 4) {
               givenidx = 1;
               targetidx = 2;
             }
            break;
         case "assertNull" :
         case "assertNotNull" :
         case "assertTrue" :
         case "assertFalse" :
         case "assertThrows" :
            if (ct == 1) givenidx = 0;
            else givenidx = 1;
            break;
         case "assertThat" :
            if (ct == 2) {
               ASTNode arg1 = getArgument(1,mi); 
               JcompType t1 = JcompAst.getExprType(arg1);
               if (t1.isBooleanType()) givenidx = 1;
               else givenidx = 0;
             }
            else givenidx = 1;
            break;
         case "fail" :
            break;
         default :
            return false;
       }
      
      if (givenidx >= 0 && targetidx >= 0) {
         boolean flip = false;
         ASTNode ng = getArgument(givenidx,mi);
         ExprChecker exckg = new ExprChecker();
         ng.accept(exckg);
         ASTNode nt = getArgument(targetidx,mi);
         ExprChecker exckt = new ExprChecker();
         nt.accept(exckt);
         if (!exckg.foundCall() && !exckg.foundVariable()) flip = true;
         else if (!exckg.foundCall() && exckt.foundCall()) flip = true;
         IvyLog.logD("DIANALYSIS","CHECK FLIP " + exckg.foundCall() + " " +
               exckg.foundVariable() +
               " " + exckt.foundCall() + " " + exckt.foundVariable() + " " + 
               flip);
         if (flip) {
            int idx = givenidx;
            givenidx = targetidx;
            targetidx = idx;
          }
       }
      
      if (givenidx < 0) return false;
      
      String given = getSourceValue(mi,givenidx);
      String target = null;
      
      switch (nm) {
         case "assertArrayEquals" :
            break;
         case "assertEquals" :
         case "assertSame" :
            target = getTargetValue(mi,targetidx);
            break;
         case "assertNotEquals" :
         case "assertNotSame" :
            break;
         case "assertNull" :
            target = "null";
            break;
         case "assertNotNull" :
            target = "Non-Null";
            break;
         case "assertThat" :
         case "assertTrue" :
            target = "true";
            break;
         case "assertFalse" :
            target = "false";
            break;
         case "assertThrows" :
            break;
         case "fail" :
            is_location = true;
            break;
         default :
            break;
       }
      Expression ex = getArgument(givenidx,mi);
      useNode(ex,given,target);
      
      return false;
    }
   
   private Expression getArgument(int idx,MethodInvocation mi) {
      if (idx == THIS_INDEX) return mi.getExpression();
      return (Expression) mi.arguments().get(idx);
    }
   
   @Override public boolean visit(AssertStatement stmt) {
      useNode(stmt.getExpression(),"false","true");
      return false;
    }
   
   private String getSourceValue(MethodInvocation mi,int idx) {
      DiadValue bv = getDiadValue(mi,idx);
      if (bv == null) return null;
      return bv.getDataType().getName() + " " + bv.toString();
    }
   
   private String getTargetValue(MethodInvocation mi,int idx) {
      DiadValue bv = getDiadValue(mi,idx);
      if (bv == null) return null;
      return bv.toString();
    }
   
   private DiadValue getDiadValue(MethodInvocation mi,int idx) {
      if (idx < 0) return null;
      Expression ex = getArgument(idx,mi); 
      DiadValue bv = getThread().evaluate(ex.toString());
      return bv;
    }
   
}       // end of inner class AssertionChecker


private static class ExprChecker extends ASTVisitor {
   
   private boolean found_call;
   private boolean found_var;
   
   ExprChecker() {
      found_call = false;
      found_var = false;
    } 
   
   boolean foundCall()                  { return found_call; }
   boolean foundVariable()              { return found_var; }
   
   @Override public void endVisit(SimpleName n) {
      found_var = true;
    }
   
   @Override public void endVisit(MethodInvocation n) {
      found_call = true;
    }
   
}       // end of inner class ExprChecker



}       // end of class DianalysisAssertionHistory




/* end of DianalysisAssertionHistory.java */

