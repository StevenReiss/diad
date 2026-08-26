/********************************************************************************/
/*                                                                              */
/*              DianalysisGraph.java                                            */
/*                                                                              */
/*      Manage graphs coming from FAIT                                          */
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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAnnotation;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSymbol;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisGraph implements DianalysisConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DianalysisManager       analysis_manager;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisGraph(DianalysisManager mgr)
{
   analysis_manager = mgr;
}


/********************************************************************************/
/*                                                                              */
/*      Convert flow graph to Node graph                                        */
/*                                                                              */
/********************************************************************************/

boolean outputGraph(Element hrslt,DiadSymptom symp,IvyXmlWriter xw)
{
   if (hrslt == null) return false;
   
   xw.begin("RESULT");
   if (symp!= null) symp.outputXml(xw); 
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
   
   return true;
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
/*      Convert graph to list of DiadLocations                                  */
/*                                                                              */
/********************************************************************************/

List<DiadLocation> getLocationResult(Element xml,DiadSymptom symp)
{
   List<DiadLocation> rslt = new ArrayList<>();
   DisourceManager src = analysis_manager.getSourceManager();  
   
   for (Element nodes : IvyXml.children(xml,"NODES")) {
      IvyLog.logD("DIANALYSIS","RESULT OF LOCATION QUERY " + IvyXml.convertXmlToString(nodes));
      Map<String,DiadLocation> done = new HashMap<>();
      for (Element n : IvyXml.children(nodes,"NODE")) {
         double p = IvyXml.getAttrDouble(n,"PRIORITY");
         String reason = IvyXml.getAttrString(n,"REASON");
         Element locelt = IvyXml.getChild(n,"LOCATION");
         String fnm = IvyXml.getAttrString(locelt,"FILE");
         if (fnm == null) {
            IvyLog.logE("DIANALYSIS","Graph element without FILE " +
                  IvyXml.convertXmlToString(n));
            continue;
          }
         File f = new File(fnm);
         String proj = src.getProjectForFile(f);
         DiadLocation loc = new DiadLocation(null,locelt,proj); 
         double p1 = loc.getPriority();
         p1 = p1 * p;
         loc.setPriority(p1);
         loc.setReason(reason);
         IvyLog.logD("DIANALYSIS","Consider file " + loc.getFile() +
               " " + loc.getLineNumber());
         //TODO:  need to map location line number to start of statement
         if (!isLocationRelevant(symp,src,loc)) {
            continue;
          }
         String s = loc.getFile().getPath() + "@" + loc.getStatementLine();
         DiadLocation oloc = done.putIfAbsent(s,loc);
         if (oloc != null) {
            double p2 = oloc.getPriority();
            if (p1 > p2) oloc.setPriority(p1);
          }
         else {
            IvyLog.logD("DIANALYSIS","USE LOCATION " + loc);
            rslt.add(loc);
          }
       }   
    }
   
   return rslt;
}


/********************************************************************************/
/*                                                                              */
/*      Check relevance of a location based on symptom                          */
/*                                                                              */
/********************************************************************************/

private boolean isLocationRelevant(DiadSymptom symp,DisourceManager src,DiadLocation loc)
{
   if (symp == null) return true;
   
   for (String s : symp.ignorePatterns()) {
      String nm = loc.getMethod(); 
      if (nm.matches(s)) return false;
    }
   
   if (symp.ignoreMain() || 
         symp.ignoreTests() || 
         symp.ignoreDriver()) { 
      ASTNode n = src.getSourceNode(loc.getProject(),
            loc.getFile(),loc.getStartOffset(),
            loc.getLineNumber(),true,false);
      while (n != null) {
         if (n instanceof MethodDeclaration) break;
         n = n.getParent();
       }
      if (n != null) {
         JcompSymbol js = JcompAst.getDefinition(n);
         if (js != null) {
            if (symp.ignoreMain()) {
               if (js.getName().equals("main") && js.isStatic() &&
                     js.getType().getBaseType().isVoidType()) {
                  IvyLog.logD("DIANALYSIS","IGNORE MAIN " + js.getFullName());
                  return false;
                }
             }
            if (symp.ignoreTests() && js.getAnnotations() != null) {
               for (JcompAnnotation ja : js.getAnnotations()) {
                  if (ja.getAnnotationType().getName().equals("org.junit.Test")) {
                     IvyLog.logD("DIANALYSIS","IGNORE TEST " + js.getFullName());
                     return false;
                   }
                }
               if (js.isPublic() && js.getName().startsWith("test")) {
                  IvyLog.logD("DIANALYSIS","IGNORE TEST " + js.getFullName());
                  return false;
                }
             }
            if (symp.ignoreTests() && js.getName().startsWith("test")) {
               IvyLog.logD("DIANALYSIS","IGNORE TEST " + js.getFullName());
               return false;
             }
            if (symp.ignoreDriver()) {
               DiadLocation loc0 = symp.getBugLocation();
               if (loc != null && loc0 != null && 
                     loc.getMethod().equals(loc0.getMethod())) {
                  IvyLog.logD("DIANALYSIS","IGNORE DRIVER " + js.getFullName());
                  return false; 
                }
             }
          }
       }
    }
   
   return true;
}



/********************************************************************************/
/*                                                                              */
/*      Graph node representation                                               */
/*                                                                              */
/********************************************************************************/

private class GraphNode {
   
   private DiadLocation node_location;
   private double node_priority;
   private String node_reason;
   private String node_type;
   
   GraphNode(Element nelt) {
      Element locelt = IvyXml.getChild(nelt,"LOCATION");
      if (locelt == null) {
         String file = IvyXml.getAttrString(nelt,"FILE");
         if (file == null) {
            IvyLog.logE("DIANALYSIS","Graph node with no location " + 
                  IvyXml.convertXmlToString(nelt));
          }
         else {
            IvyLog.logI("DIANALYSIS","File " + file + 
                  " is binary for analysis");
          }
         node_location = null;
       }
      else {
         node_location = new DiadLocation(analysis_manager.getDiadControl(),
               locelt,null); 
       }
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




}       // end of class DianalysisGraph




/* end of DianalysisGraph.java */

