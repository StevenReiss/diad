/********************************************************************************/
/*                                                                              */
/*              DianalysisLocationHistory.java                                  */
/*                                                                              */
/*      Find flow starting point for bad location                               */
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
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadAssertionData;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisLocationHistory extends DianalysisHistory
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private static BractAstPattern expr_pattern;

static {
   expr_pattern = BractAstPattern.expression("Ex == Ey","Ex != Ey",
         "Ex.equals(Ey)","!Ex.equals(Ey)");
}



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisLocationHistory(DianalysisFactory fac,DiadSymptom symp,DiadThread thrd)
{
   super(fac,symp,thrd);
}

/********************************************************************************/
/*                                                                              */
/*      Process location query                                                  */
/*                                                                              */
/********************************************************************************/

@Override protected void process(IvyXmlWriter xw) throws DiadException
{
   String locxml = getExecLocation();
   if (locxml == null) {
      IvyLog.logE("STEM","No location for location query");
      throw new DiadException("Location undefined");
    }
   
   Element hrslt = getLocationData(locxml);
   outputGraph(hrslt,xw); 
}



/********************************************************************************/
/*                                                                              */
/*      Set up and execute query                                                */
/*                                                                              */
/********************************************************************************/

private Element getLocationData(String locxml)
{
   getAnalysis().waitForAnalysis();
   
   CommandArgs args = new CommandArgs("QTYPE","LOCATION");
   args = addCommandArgs(args);
   
   String qxml = locxml;
   String sxml = getXmlForStack();
   if (sxml != null) {
      if (qxml == null) qxml = sxml;
      else qxml += sxml;
    }
   Element rslt = getAnalysis().sendFaitMessage("FLOWQUERY",args,qxml);
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Handle special cases where additional information is available          */
/*                                                                              */
/********************************************************************************/

DiadAssertionData getAssertionData()
{
   getAnalysis().waitForAnalysis();
   
   try {
      ASTNode stmt = getSourceStatement();
      ASTNode from = stmt;
      ASTNode par = stmt.getParent();
      while (par.getNodeType() == ASTNode.BLOCK) {
         from = par;
         par = par.getParent();
       }
      if (par.getNodeType() == ASTNode.IF_STATEMENT) {
         IfStatement ifstmt = (IfStatement) par;
         Expression cond = ifstmt.getExpression();
         PatternMap pmap = new PatternMap();
         if (expr_pattern.match(cond,pmap)) return null;
         Expression ex1 = (Expression) pmap.get("x");
         Expression ex2 = (Expression) pmap.get("y");
         boolean comp = false;
         if (cond.getNodeType() == ASTNode.INFIX_EXPRESSION) {
            InfixExpression ifx = (InfixExpression) cond;
            if (ifx.getOperator() == InfixExpression.Operator.NOT_EQUALS) comp = true;
          }
         else if (cond.getNodeType() == ASTNode.PREFIX_EXPRESSION) comp = true;
         if (ifstmt.getElseStatement() == from) comp = !comp;
         if (comp) return null;
         switch (ex1.getNodeType()) {
            case ASTNode.NUMBER_LITERAL :
            case ASTNode.STRING_LITERAL :
            case ASTNode.NULL_LITERAL :
            case ASTNode.TEXT_BLOCK :
               Expression exx = ex1;
               ex1 = ex2;
               ex2 = exx;
               break;
          }
         DiadValue v1 = getThread().evaluate(ex1.toString());
         DiadValue v2 = getThread().evaluate(ex2.toString());
         return new LocationData(ex1,v1.toString(),v2.toString());
       }
    }
   catch (DiadException e) { }
   
   return null;
}




private static class LocationData implements DiadAssertionData {
   
   private Expression use_node;
   private String orig_value;
   private String target_value;
   
   LocationData(Expression nd,String ov,String nv) {
      use_node = nd;
      orig_value = ov;
      target_value = nv;
    }
   
   @Override public ASTNode getExpression()             { return use_node; }
   @Override public String getOriginalValue()           { return orig_value; }
   @Override public String getTargetValue()             { return target_value; }
   @Override public boolean isLocation()                { return false; }
   
}


}       // end of class DianalysisLocationHistory




/* end of DianalysisLocationHistory.java */

