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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadNodeContext;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.disource.DisourceFactory;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

abstract class DianalysisHistory implements DianalysisConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DianalysisFactory for_analysis;
private DiadSymptom for_symptom;
private DiadThread  for_thread;
private DiadStackFrame for_frame;
private DiadNodeContext node_context;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisHistory(DianalysisFactory fac,DiadSymptom symp,DiadThread thrd)
{
   for_analysis = fac;
   for_symptom = symp;
   for_thread = thrd;
   for_frame = for_thread.getStack().getUserFrame();
   node_context = null;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

protected DianalysisFactory getAnalysis()
{
   return for_analysis;
}

protected DisourceFactory getSourceManager()
{
   return for_analysis.getSourceManager(); 
}

protected DicontrolMain getDiadControl()
{
   return for_analysis.getDiadControl();
}

protected String getProject()
{
   File f1 = for_thread.getStack().getUserFrame().getSourceFile();
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

protected ASTNode getSourceStatement() throws DiadException
{
    ASTNode stmt = getSourceManager().getSourceNode(getProject(),for_frame.getSourceFile(),
         0,for_frame.getLineNumber(),true,true);
    
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
   args.put("METHOD",for_frame.getMethodName());
   
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
// RoseLog.logD("STEM","HISTORY RESULT: " + IvyXml.convertXmlToString(hrslt));
   
   if (hrslt == null) throw new DiadException("Can't find history");
   xw.begin("RESULT");
   if (for_symptom != null) for_symptom.outputXml(xw); 
   xw.begin("NODES");
   int lsz = 0;
   int tsz = 0;
   long ttim = 0;
   for (Element qrslt : IvyXml.children(hrslt,"QUERY")) {
      Element grslt = IvyXml.getChild(qrslt,"GRAPH");
      int sz = IvyXml.getAttrInt(grslt,"SIZE");
      tsz += sz;
      ttim += IvyXml.getAttrLong(grslt,"TIME");
      if (sz > 0) lsz += processGraphNodes(grslt,xw);
    }
   xw.end("NODES");
   xw.end("RESULT");
   
   IvyLog.logI("DIANALYSIS","Location query counts, GRAPH: " + tsz + 
         " NODES: " + lsz + " TIME: " + ttim);
}


private int processGraphNodes(Element gelt,IvyXmlWriter xw)
{
   Map<String,GraphNode> locs = new HashMap<>();
   
   List<GraphNode> allnodes = new ArrayList<>();
   for (Element nelt : IvyXml.children(gelt,"NODE")) {
      GraphNode gn = new GraphNode(nelt);
      if (gn.shouldCheck()) allnodes.add(gn);
    }
   
   Set<File> done = new HashSet<>();
   for ( ; ; ) {
      File workon = null;
      for (GraphNode gn : allnodes) {
         File gfile = gn.getFile();
         if (done.contains(gfile)) continue;
         if (workon == null) {
            workon = gfile;
            gn.getLineNumber();
          }
         else if (gfile.equals(workon)) gn.getLineNumber();
       }
      if (workon == null) break;
      done.add(workon); 
    }
   
   for (GraphNode gn : allnodes) {
      if (!gn.isValid()) continue;
      String id = gn.getLocationString();
      GraphNode ogn = locs.get(id);
      if (ogn != null) {
         if (ogn.getPriority() >= gn.getPriority()) continue;
       }
      locs.put(id,gn);
    }
   for (GraphNode gn : locs.values()) {
      gn.outputXml(xw);
    }
   
   return locs.size();
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



protected String getExecLocation() throws DiadException
{
   String rslt = null;
   ASTNode node = getSourceStatement();
   if (node == null) return null;
   
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("LOCATION");
   xw.field("FILE",for_frame.getSourceFile());
   xw.field("LINE",for_frame.getLineNumber());
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
   
}



private class GraphNode {

   private DiadLocation node_location;
   private double node_priority;
   private String node_reason;
   private String node_type;
   
   GraphNode(Element nelt) {
      Element locelt = IvyXml.getChild(nelt,"LOCATION");
      node_location = new DiadLocation(for_analysis.getDiadControl(),
            locelt,null); 
      node_reason = IvyXml.getAttrString(nelt,"REASON");
      node_priority = IvyXml.getAttrDouble(nelt,"PRIORITY",0.5);
      Element point = IvyXml.getChild(nelt,"POINT");
      node_type = IvyXml.getAttrString(point,"NODETYPE");
    }
   
   boolean isValid() {
      if (node_location == null || node_reason == null) return false;
      if (node_location.getFile() == null) return false;
      if (!node_location.getFile().exists()) return false;
      if (node_location.getLineNumber() <= 0) return false;
      if (node_type == null) return false;
      switch (node_type) {
         case "MethodDeclaration" :
            return false;
         default :
            
       }
      
      return true;
    }
   
   boolean shouldCheck() {
      if (node_location == null || node_reason == null) return false;
      if (node_location.getFile() == null) return false;
      if (!node_location.getFile().exists()) return false;
      if (node_type == null) return false;
      switch (node_type) {
         case "MethodDeclaration" :
            return false;
         default :
            
       }
      
      return true;
    }
   
   double getPriority()                    { return node_priority; }
   
   String getLocationString() {
      String s = node_location.getFile().getPath();
      s += "@" + node_location.getLineNumber();
      s += ":" + node_location.getStartOffset();
      s += "-" + node_location.getEndOffset();
      return s;
    }
   
   File getFile() {
      return  node_location.getFile();
    }
   
   int getLineNumber() {
      return node_location.getLineNumber();
    }
   
   void outputXml(IvyXmlWriter xw) {
      xw.begin("NODE");
      xw.field("PRIORITY",node_priority);
      xw.field("REASON",node_reason);
      node_location.outputXml(xw);
      xw.end("NODE");
    }
   
}       // end of inner class GraphNode

}       // end of class DianalysisHistory




/* end of DianalysisHistory.java */

