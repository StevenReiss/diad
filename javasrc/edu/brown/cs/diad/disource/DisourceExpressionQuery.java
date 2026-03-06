/********************************************************************************/
/*                                                                              */
/*              DisourceExpressionQuery.java                                    */
/*                                                                              */
/*      description of class                                                    */
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



package edu.brown.cs.diad.disource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import edu.brown.cs.diad.dianalysis.DianalysisManager;
import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DisourceExpressionQuery implements DisourceConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private ASTNode         source_ast;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DisourceExpressionQuery(DicontrolMain diad,ASTNode src)
{
   source_ast = src;
}


/********************************************************************************/
/*                                                                              */
/*      Processing methods                                                      */
/*                                                                              */
/********************************************************************************/

void process(IvyXmlWriter xw)
{
   if (source_ast == null) return;
   
   Map<String,ASTNode> exprs = new LinkedHashMap<>();
   ExprFinder exprfinder = new ExprFinder(exprs);
   
   ASTNode node = source_ast;
   
   while (node != null) {
      node.accept(exprfinder); 
      StructuralPropertyDescriptor spd = node.getLocationInParent();
      if (spd.isChildListProperty()) {
         ASTNode par = node.getParent();
         List<?> chlds = (List<?>) par.getStructuralProperty(spd);
         int idx = chlds.indexOf(node);
         if (idx == 0) node = par.getParent();
         else node = (ASTNode) chlds.get(idx-1);
         node = getStatementOf(node);
       }
      else if (spd.isChildProperty()) {
         node = getStatementOf(node.getParent());
       }
    }
   
   for (ASTNode n : exprs.values()) {
      DianalysisManager.addXmlForLocation("EXPR",n,true,xw);
    }
}



private ASTNode getStatementOf(ASTNode node)
{
   while (node != null) {
      if (node instanceof Statement) break;
      node = node.getParent();
    }  
   
   return node;
}



private static class ExprFinder extends ASTVisitor {
   
   private Map<String,ASTNode> expr_set;
   
   ExprFinder(Map<String,ASTNode> exprs) {
      expr_set = exprs;
    }
   
   @Override public void postVisit(ASTNode n) {
      if (n instanceof Expression) {
         switch (n.getNodeType()) {
            case ASTNode.NORMAL_ANNOTATION :
            case ASTNode.MARKER_ANNOTATION :
            case ASTNode.SINGLE_MEMBER_ANNOTATION :
            case ASTNode.ARRAY_INITIALIZER :
            case ASTNode.ASSIGNMENT :
            case ASTNode.BOOLEAN_LITERAL :
            case ASTNode.CAST_EXPRESSION :
            case ASTNode.CHARACTER_LITERAL :
            case ASTNode.CLASS_INSTANCE_CREATION :
            case ASTNode.CONDITIONAL_EXPRESSION :
            case ASTNode.INSTANCEOF_EXPRESSION :
            case ASTNode.LAMBDA_EXPRESSION :
            case ASTNode.SIMPLE_NAME :
            case ASTNode.NULL_LITERAL :
            case ASTNode.PARENTHESIZED_EXPRESSION :
            case ASTNode.POSTFIX_EXPRESSION :
            case ASTNode.STRING_LITERAL :
            case ASTNode.SWITCH_EXPRESSION :
            case ASTNode.THIS_EXPRESSION :
            case ASTNode.TEXT_BLOCK :
            case ASTNode.TYPE_LITERAL :
            case ASTNode.VARIABLE_DECLARATION_EXPRESSION :
               return;
            case ASTNode.PREFIX_EXPRESSION :
               PrefixExpression pfx = (PrefixExpression) n;
               PrefixExpression.Operator op = pfx.getOperator();
               if (op == PrefixExpression.Operator.DECREMENT  ||
                     op == PrefixExpression.Operator.INCREMENT)
                  return;
               break;
            case ASTNode.INFIX_EXPRESSION :
               InfixExpression ifx = (InfixExpression) n;
               InfixExpression.Operator iop = ifx.getOperator();
               if (iop == InfixExpression.Operator.CONDITIONAL_AND ||
                     iop == InfixExpression.Operator.CONDITIONAL_OR)
                  return;
               break;
            default :
               break;
          }
         String exp = n.toString();
         if (expr_set.get(exp) == null) {
            expr_set.put(exp,n);
          }
       }
    }
   
}       // end of inner class ExprFinder




}       // end of class DisourceExpressionQuery




/* end of DisourceExpressionQuery.java */

