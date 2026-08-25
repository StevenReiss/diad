/********************************************************************************/
/*                                                                              */
/*              DianalysisHistory.java                                          */
/*                                                                              */
/*      Generalization of a history query                                       */
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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadNodeContext;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSource;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

abstract class DianalysisHistory implements DianalysisConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DianalysisManager for_analysis;
private DiadSymptom for_symptom;
private DiadThread  for_thread;
private DiadStackFrame for_frame;
private DiadNodeContext node_context;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisHistory(DianalysisManager fac,DiadSymptom symp,DiadThread thrd)
{
   for_analysis = fac;
   for_symptom = symp;
   for_thread = thrd;
   for_frame = null;
   
   if (thrd != null) {
      DiadStack stk = for_thread.getStack();
      if (stk != null) {
         for_frame = stk.getUserFrame();
       }
    }
   
   node_context = null;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

protected DianalysisManager getAnalysis()
{
   return for_analysis;
}

protected DisourceManager getSourceManager()
{
   return for_analysis.getSourceManager(); 
}

protected DicontrolMain getDiadControl()
{
   return for_analysis.getDiadControl();
}

protected String getProject()
{
   if (for_thread == null) return null;
   DiadStack stk = for_thread.getStack();
   if (stk == null) return null;
   DiadStackFrame frm = stk.getUserFrame();
   if (frm == null) return null;
   File f1 = frm.getSourceFile();
   if (f1 == null) return null;
   
   return getSourceManager().getProjectForFile(f1);
}

protected DiadSymptom getSymptom()
{
   return for_symptom;
}

protected DiadThread getThread()
{
   return for_thread;
}

protected DiadStackFrame getFrame()
{
   return for_frame;
}

protected DiadNodeContext getNodeContext()
{
   return node_context;
}


/********************************************************************************/
/*                                                                              */
/*      Processing methods                                                      */
/*                                                                              */
/********************************************************************************/

protected abstract void process(IvyXmlWriter xw) throws DiadException;



/********************************************************************************/
/*                                                                              */
/*      Find last component of a tree                                           */
/*                                                                              */
/********************************************************************************/

protected static String getNodeTypeName(ASTNode n) 
{
   String typ = n.getClass().getName();
   int idx = typ.lastIndexOf(".");
   if (idx > 0) typ = typ.substring(idx+1);
   return typ;
}


/********************************************************************************/
/*                                                                              */
/*      Find statement of stopping point                                        */
/*                                                                              */
/********************************************************************************/

protected ASTNode getSourceStatement() 
{
    ASTNode stmt = getSourceManager().getSourceNode(getProject(),for_frame.getSourceFile(),
         -1,for_frame.getLineNumber(),true,true);
    
    return stmt;
}


protected ASTNode getSymptomStatement()
{
   DicontrolMain diad = for_analysis.getDiadControl();
   ASTNode stmt = diad.getSymptomLocation(for_symptom);
   if (stmt == null) stmt = getSourceStatement();
   return stmt;
}


protected ASTNode getResolvedSourceStatement() throws DiadException
{
   return getSourceManager().getSourceNode(getProject(),
         for_frame.getSourceFile(),
         0,for_frame.getLineNumber(),true,true);
}


protected ASTNode findNode(CompilationUnit cu,String text,int line) 
{
   if (cu == null) return null;
   int off = -1;
   if (line > 0) {
      off = cu.getPosition(line,0);
      while (off < text.length()) {
         char c = text.charAt(off);
         if (!Character.isWhitespace(c)) break;
         ++off;
       }
    }
   ASTNode node = JcompAst.findNodeAtOffset(cu,off);   
   return node;
}


protected ASTNode getStatementOf(ASTNode node)
{
   while (node != null) {
      if (node instanceof Statement) break;
      node = node.getParent();
    }  
   
   return node;
}



/********************************************************************************/
/*                                                                              */
/*      Add location arguments for FAIT query                                   */
/*                                                                              */
/********************************************************************************/

protected CommandArgs addCommandArgs(CommandArgs args) 
{
   if (args == null) args = new CommandArgs();
   args.put("FILE",for_frame.getSourceFile().getAbsolutePath());
   args.put("LINE",for_frame.getLineNumber());
   args.put("METHOD",for_frame.getFullMethodName());
   
   int conddepth = getDiadControl().getProperty("Diad.cond.depth",4);
   int querydepth = getDiadControl().getProperty("Diad.query.depth",10);
   
   if (args.get("CONDDEPTH") == null) args.put("CONDDEPTH",conddepth);
   if (args.get("DEPTH") == null) args.put("DEPTH",querydepth);
   
   return args;
}




/********************************************************************************/
/*                                                                              */
/*      Handle getting relevant information for query                           */
/*                                                                              */
/********************************************************************************/

protected String getXmlForLocation(String elt,ASTNode node,boolean next)
{
   if (node == null) return null;
   
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      addXmlForLocation(elt,node,next,xw);
      return xw.toString();
    }
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

protected void outputGraph(Element hrslt,IvyXmlWriter xw) throws DiadException
{
   if (hrslt == null) throw new DiadException("Can't find history");
   
   DianalysisGraph dg = new DianalysisGraph(for_analysis);
   dg.outputGraph(hrslt,for_symptom,xw);
}



/********************************************************************************/
/*                                                                              */
/*      Output helper methods                                                   */
/*                                                                              */
/********************************************************************************/

protected String getXmlForStack()
{
   DiadStack stk = for_thread.getStack();
   if (stk == null) return null;
   
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      xw.begin("STACK");
      for (DiadStackFrame bsf : stk.getFrames()) {
         xw.begin("FRAME");
         xw.field("CLASS",bsf.getClassName());
         xw.field("METHOD",bsf.getMethodName());
         xw.field("SIGNATURE",bsf.getMethodSignature());
         xw.field("FSIGN",bsf.getFormatSignature());
         xw.end("FRAME");
       }
      xw.end("STACK");
      return xw.toString();
    }
}


protected static void addXmlForLocation(String elt,ASTNode node,boolean next,IvyXmlWriter xw)
{
   if (node == null) return;
   
   CompilationUnit cu = (CompilationUnit) node.getRoot();
   
   ASTNode use = node;
   ASTNode after = null;
   
   if (next) {
      use = node.getParent();
      after = node;
    }
   else {
      after = getAfterNode(node);
    }
   
   xw.begin(elt);
   xw.field("START",use.getStartPosition());
   xw.field("END",use.getStartPosition() + node.getLength());
   xw.field("LINE",cu.getLineNumber(use.getStartPosition()));
   xw.field("NODETYPE",getNodeTypeName(use));
   xw.field("NODETYPEID",use.getNodeType());
   
   if (after != null) {
      StructuralPropertyDescriptor spd = after.getLocationInParent();
      xw.field("AFTER",spd.getId());
      xw.field("AFTERSTART",after.getStartPosition());
      xw.field("AFTEREND",after.getStartPosition() + after.getLength());
      xw.field("AFTERTYPE",getNodeTypeName(after));
      xw.field("AFTERTYPEID",after.getNodeType());
    }
   xw.textElement("TEXT",node.toString());
   xw.end(elt);
}



protected String getSymptomLocation() throws DiadException
{
   DicontrolMain diad = for_analysis.getDiadControl();
   ASTNode stmt = diad.getSymptomLocation(for_symptom);
   if (stmt != null) return getExecLocation(stmt);
   
   return getExecLocation();
}


protected String getExecLocation() throws DiadException
{
   ASTNode node = getSourceStatement();
   if (node == null) return null;
   
   return getExecLocation(for_frame.getSourceFile(),
         for_frame.getLineNumber(),
         node);
}



protected String getExecLocation(ASTNode node)
{
   CompilationUnit cu = (CompilationUnit) node.getRoot();
   JcompSource js = JcompAst.getSource(node);
   File src = new File(js.getFileName());
   int lno = cu.getLineNumber(node.getStartPosition());
   
   return getExecLocation(src,lno,node);
}


protected String getExecLocation(File src,int lno,ASTNode node)
{
   String rslt = null;
   if (node == null) return null;
   
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("LOCATION");
   xw.field("FILE",src);
   xw.field("LINE",lno);
   xw.field("START",node.getStartPosition());
   xw.field("END",node.getStartPosition() + node.getLength());
   xw.field("NODETYPE",getNodeTypeName(node));
   xw.field("NODETYPEID",node.getNodeType());
   xw.end("LOCATION");
   rslt = xw.toString();
   xw.close();
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Get after node for a tree node                                          */
/*                                                                              */
/********************************************************************************/

protected static ASTNode getAfterNode(ASTNode expr)
{
   if (expr == null) return null;
   
   AfterFinder af = new AfterFinder();
   expr.accept(af);
   return af.getAfterNode();
}



private static class AfterFinder extends ASTVisitor {
   
   private ASTNode start_node;
   private ASTNode last_node;
   
   AfterFinder() {
      start_node = null;
      last_node = null;
    }
   
   ASTNode getAfterNode()               { return last_node; }
   
   @Override public boolean preVisit2(ASTNode n) {
      if (start_node == null) {
         start_node = n;
         last_node = null;
       }
      return true;
    }
   
   @Override public void postVisit(ASTNode n) {
      if (n == start_node) {
         start_node = null;
       }
      else last_node = n;
    }
   
}       // end of inner class AfterFinder


}       // end of class DianalysisHistory


/* end of DianalysisHistory.java */

