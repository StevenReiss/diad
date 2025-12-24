/********************************************************************************/
/*                                                                              */
/*              DianalysisFactory.java                                          */
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



package edu.brown.cs.diad.dianalysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadConstants.DiadAnalysisFileMode;
import edu.brown.cs.diad.dicore.DiadConstants.DiadAnalysisState;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public class DianalysisManager implements DianalysisConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private Set<File>       loaded_files;
private boolean         done_allfiles;
private DiadAnalysisState analysis_state;
private String          session_id;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DianalysisManager(DicontrolMain ctrl)
{
   diad_control = ctrl;
   loaded_files = new HashSet<>();
   
   done_allfiles = false;
   analysis_state = DiadAnalysisState.NONE;
   session_id = null;
   
   Random r = new Random();
   String sid = "DIAD_" + r.nextInt(10000000);
   CommandArgs args = new CommandArgs("SID",sid);
   Element rslt = sendFaitMessage("BEGIN",args,null);
   if (!IvyXml.isElement(rslt,"RESULT")) {
      analysis_state = DiadAnalysisState.NONE;
    }
   Element sess = IvyXml.getChild(rslt,"SESSION");
   sid = IvyXml.getAttrString(sess,"ID",sid);
   if (sid != null) session_id = sid;
   startAnalysis();
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DicontrolMain   getDiadControl()                        { return diad_control; }

DisourceManager getSourceManager()
{
   return diad_control.getSourceManager(); 
}


/********************************************************************************/
/*                                                                              */
/*      Methods to add files and setup analysis                                 */
/*                                                                              */
/********************************************************************************/

public void addFiles(DiadAnalysisFileMode mode,Collection<File> files,DiadThread thrd)
{ 
   Set<File> use = new HashSet<>();
   
   IvyLog.logD("DIANALYSIS","Add files for " + thrd.getThreadName() + " " + mode);
   
   Set<File> add = getInitialFileSet(mode,thrd);
   if (files != null) use.addAll(files);
   if (add != null) use.addAll(add);
   
   Set<File> nset = new HashSet<>();
   for (File f : use) {
      File f1 = IvyFile.getCanonical(f);
      if (!f1.exists()) continue;
      if (!loaded_files.contains(f1)) {
         nset.add(f1);
       }
    }
   
   StringBuffer buf = new StringBuffer();
   int ct = 0;
   for (File f : nset) {
      buf.append("<FILE NAME='");
      buf.append(f.getAbsolutePath());
      buf.append("' />");
      ++ct;
    }
   if (ct > 0) {
      loaded_files.addAll(nset);
      String cnts = buf.toString();
      Element xw = sendFaitMessage("ADDFILE",null,cnts);
      if (IvyXml.isElement(xw,"RESULT")) {
	 if (IvyXml.getAttrBool(xw,"ADDED")) {
            analysis_state = DiadAnalysisState.PENDING;
	  }
       }
    }
   else {
      IvyLog.logD("DIANALYSIS","No files to add for " + thrd.getThreadName() + " " +
            mode);
    }
}


private Set<File> getInitialFileSet(DiadAnalysisFileMode mode,DiadThread thrd)
{
   Set<File> add = null;
   
   switch (mode) {
      case ALL_FILES :
         if (!done_allfiles){
            add = findAllSourceFiles();
            done_allfiles = true;
          } 
         break;
      case COMPUTED_FILES:
         add = findComputedFiles(thrd);
         break;
      case FAIT_FILES :
         add = findFaitFiles(thrd);
         break;
      case STACK_FILES :
         add = findStackFiles(thrd);
         break;
      case USER_FILES :
         break;
    }
   
   return add;
}


/********************************************************************************/
/*                                                                              */
/*      Find initial set of fault locations                                     */
/*                                                                              */
/********************************************************************************/

public Collection<DiadLocation> findInitialLocations(DiadSymptom symp,DiadThread thrd)
{
   DianalysisLocations locs = new DianalysisLocations(this,symp,thrd);
   
   return locs.findInitialLocations();
}

/********************************************************************************/
/*                                                                              */
/*      Wait for analysis                                                       */
/*                                                                              */
/********************************************************************************/

private void startAnalysis()
{
   if (analysis_state == DiadAnalysisState.NONE) {
      analysis_state = DiadAnalysisState.PENDING;
      CommandArgs aargs = new CommandArgs("REPORT","FULL_STATS",
            "ID",session_id);
      int nth = diad_control.getProperty("Diad.fait.threads",4); 
      if (nth > 0) aargs.put("THREADS",nth);
      Element arslt = sendFaitMessage("ANALYZE",aargs,null);
      if (!IvyXml.isElement(arslt,"RESULT")) {
         analysis_state = DiadAnalysisState.FAIL;
         IvyLog.logE("DIANALYSIS","Fait analysis failed " + 
               IvyXml.convertXmlToString(arslt));
       }
    }
}


/********************************************************************************/
/*                                                                              */
/*      Send messages                                                           */
/*                                                                              */
/********************************************************************************/


Element sendFaitMessage(String cmd,CommandArgs args,String cnts)
{
   if (session_id != null) {
      if (args == null) args = new CommandArgs();
      if (args.get("SID") == null) args.put("SID",session_id);
    }
   
   Element rslt = diad_control.sendFaitMessage(cmd,args,cnts);
   
   return rslt;
}



public synchronized void handleAnalysis(Element xml)
{
   IvyLog.logD("DIANALYSIS","Analysis received: " + IvyXml.convertXmlToString(xml));
   
   String id = IvyXml.getAttrString(xml,"ID");
   if (session_id == null || !session_id.equals(id)) return;
   
   boolean started = IvyXml.getAttrBool(xml,"STARTED");
   boolean aborted = IvyXml.getAttrBool(xml,"ABORTED");
   
   if (started || aborted) {
      analysis_state = DiadAnalysisState.PENDING;
    }
   else {
      analysis_state = DiadAnalysisState.READY;
    }
   
   notifyAll();
}




public synchronized Boolean waitForAnalysis()
{
   for ( ; ; ) {
      switch (analysis_state) {
         case NONE :
         case FAIL : 
            return false;
         case PENDING :
            break;
         case READY :
            return true;
       }
      try {
         wait(10000);
       }
      catch (InterruptedException e) {
         return null;
       }
    }
}


/********************************************************************************/
/*                                                                              */
/*      File getting routines                                                   */
/*                                                                              */
/********************************************************************************/

private Set<File> findStackFiles(DiadThread thrd)
{
   Set<File> rslt = new HashSet<>();
   if (thrd == null) return rslt;
   
   for (DiadStackFrame frm : thrd.getStack().getFrames()) {
      File f = frm.getSourceFile();
      if (frm.isUserFrame() && f.exists()) {
         f = IvyFile.getCanonical(f);
         rslt.add(f);
       }
    }
   
   IvyLog.logD("DIANALYSIS","Done adding stack files");
   
   return rslt;
}



private Set<File> findComputedFiles(DiadThread thrd)
{
   if (thrd == null) return findAllSourceFiles();
   Set<File> rslt = new HashSet<>();
   Set<File> roots = new HashSet<>();
   
   for (DiadStackFrame frm : thrd.getStack().getFrames()) {
       if (frm.isUserFrame()) {
          File f = frm.getSourceFile();
          int lno = frm.getLineNumber();
          if (lno > 0 && f.exists()) {
             f = IvyFile.getCanonical(f);
             roots.add(f);
           }
        }
    }
   
   findFilesForUnits(roots,rslt);
   
   return rslt;
}



private Set<File> findFaitFiles(DiadThread thrd) throws RuntimeException
{
   Set<File> base = findAllSourceFiles();
   if (base == null || base.size() < 40) return base;
   
   if (thrd == null) return base;
   
   Set<File> rslt = new HashSet<>();
   Set<String> mthds = new HashSet<>();
   
   for (DiadStackFrame frm : thrd.getStack().getFrames()) {
      File f = frm.getSourceFile();
      if (frm.isUserFrame() && frm.getLineNumber() > 0 && f.exists()) {
         String snm = frm.getMethodName();
         String mnm = frm.getMethodSignature();
         mthds.add(mnm+snm);
       }
    }
   
   waitForAnalysis();
   
   IvyXmlWriter xw = new IvyXmlWriter();
   for (String s : mthds) {
      xw.textElement("METHOD",s);
    }
   Element clsxml = sendFaitMessage("FILEQUERY",null,xw.toString());
   xw.close();
   Set<String> clsset = new HashSet<>();
   for (Element celt : IvyXml.children(clsxml,"CLASS")) {
      String cnm = IvyXml.getText(celt);
      clsset.add(cnm);
    }
   findFilesForClasses(clsset,rslt);
   
   return rslt; 
}


private File findFilesForClasses(Set<String> clsset,Set<File> rslt)
{
   IvyLog.logD("DIANALYSIS","FIND FILES FOR CLASSES " + clsset);
   
   Element r = diad_control.sendBubblesMessage("PROJECTS",null,null);
   if (!IvyXml.isElement(r,"RESULT")) {
      IvyLog.logE("DIANALYSIS","Problem getting project information: " + IvyXml.convertXmlToString(r));
    }
   
   File dir = null;
   String pkg = null;
   for (String s : clsset) {
      int idx = s.lastIndexOf(".");
      if (idx > 0) {
         String p = s.substring(0,idx);
         if (pkg == null) pkg = p;
         else if (!pkg.equals(p)) {
            pkg = null;
            break;
          }
       }
    }
   
   Map<String,String> classmap = new HashMap<>();
   for (Element pe : IvyXml.children(r,"PROJECT")) {
      String pnm = IvyXml.getAttrString(pe,"NAME");
      CommandArgs args = new CommandArgs("CLASSES",true,
            "PROJECT",pnm);
      Element cxml = diad_control.sendBubblesMessage("OPENPROJECT",args,null);
      Element clss = IvyXml.getChild(IvyXml.getChild(cxml,"PROJECT"),"CLASSES");
      
      for (Element c : IvyXml.children(clss,"TYPE")) {
         String tnm = IvyXml.getAttrString(c,"NAME");
         tnm = tnm.replace("$",".");
         String fnm  = IvyXml.getAttrString(c,"SOURCE");
         if (tnm != null && fnm != null) classmap.put(tnm,fnm);
         int idx = tnm.lastIndexOf(".");
         if (idx > 0 && dir == null) {
            String p = tnm.substring(0,idx);
            if (pkg != null && pkg.equals(p)) {
               File f = new File(fnm);
               dir = f.getParentFile();
             }
          }
       }
    }
   
   for (String s : clsset) {
      s = s.replace("$",".");
      String fnm = classmap.get(s);
      if (fnm != null) rslt.add(new File(fnm));
      else {
         if (!isAnonymousClass(s))
            IvyLog.logE("DIANALYSIS","Class " + s + " not found for FILEADD");
       }
    }
   
   return dir;
}


private boolean isAnonymousClass(String s)
{
   int idx = s.lastIndexOf(".");
   if (idx > 0 && idx < s.length()-1) {
      char c = s.charAt(idx+1);
      if (Character.isDigit(c)) return true;
    }
   return false;
}



private void findFilesForUnits(Set<File> roots,Set<File> rslt)
{
   Queue<File> todo = new ArrayDeque<>(roots);
   
   IvyLog.logD("DIANALYSIS","FIND FILES FOR UNITS " + roots);
   
   Element r = diad_control.sendBubblesMessage("PROJECTS",null,null);
   if (!IvyXml.isElement(r,"RESULT")) {
      IvyLog.logE("DIANALYSIS","Problem getting project information: " + IvyXml.convertXmlToString(r));
    }
   
   Map<String,String> classmap = new HashMap<>();
   for (Element pe : IvyXml.children(r,"PROJECT")) {
      String pnm = IvyXml.getAttrString(pe,"NAME");
      CommandArgs args = new CommandArgs("CLASSES",true,
            "PROJECT",pnm);
      Element cxml = diad_control.sendBubblesMessage("OPENPROJECT",args,null);
      Element clss = IvyXml.getChild(IvyXml.getChild(cxml,"PROJECT"),"CLASSES");
      
      for (Element c : IvyXml.children(clss,"TYPE")) {
         String tnm = IvyXml.getAttrString(c,"NAME");
         String fnm  = IvyXml.getAttrString(c,"SOURCE");
         if (tnm != null && fnm != null) classmap.put(tnm,fnm);
       }
    }
   
   while (!todo.isEmpty()) {
      File work = todo.remove();
      if (rslt.add(work)) {
         Set<String> nf = getFilesForFile(work,classmap);
         for (String fnm : nf) {
            File f1 = new File(fnm);
            if (f1.exists() && !rslt.contains(f1)) todo.add(f1);
          }
       }
    }
}



private Set<String> getFilesForFile(File f,Map<String,String> classmap)
{
   Set<String> use = new HashSet<>();
   try {
      String src = IvyFile.loadFile(f);
      CompilationUnit cu = JcompAst.parseSourceFile(src);
      PackageDeclaration pd = cu.getPackage();
      if (pd != null) {
         addAllFilesForPackage(pd.getName().getFullyQualifiedName(),classmap,use);
       }
      for (Object o : cu.imports()) {
         ImportDeclaration id = (ImportDeclaration) o;
         String nm = id.getName().getFullyQualifiedName();
         int nln = nm.length();
         if (id.isOnDemand()) {
            addAllFilesForPackage(nm,classmap,use);
          }
         else {
            for (int i = nln; i > 0; i = nm.lastIndexOf(".",i-1)) {
               String s1 = nm.substring(0,i);
               String r = classmap.get(s1);
               if (r != null) {
                  use.add(r);
                  break;
                }
             }
          }
       }
    }
   catch (IOException e) { }
   
   return use;
}



private void addAllFilesForPackage(String pkg,Map<String,String> classmap,Set<String> use)
{
   int nln = pkg.length();
   for (String s : classmap.keySet()) {
      if (s.startsWith(pkg)) {
         int idx = s.lastIndexOf(".");
         if (idx == nln) use.add(classmap.get(s));
       }
    }
}



private Set<File> findAllSourceFiles()
{
   Element r = diad_control.sendBubblesMessage("PROJECTS",null,null);
   if (!IvyXml.isElement(r,"RESULT")) {
      IvyLog.logE("DIANALYSIS","Problem getting project information: " + IvyXml.convertXmlToString(r));
      return null;
    }
   
   Set<File> allfiles = new HashSet<>();
   for (Element pe : IvyXml.children(r,"PROJECT")) {
      String pnm = IvyXml.getAttrString(pe,"NAME");
      allfiles.addAll(getProjectSourceFiles(pnm));
    }
   
   return allfiles;
}




private Set<File> getProjectSourceFiles(String proj)
{
   IvyLog.logD("DIANALYSIS","Get project source files " + proj);
   
   Set<File> rslt = new HashSet<>();
   CommandArgs cargs = new CommandArgs("FILES",true,
         "PROJECT",proj);
   Element pxml = diad_control.sendBubblesMessage("OPENPROJECT",cargs,null);
   Element p1 = IvyXml.getChild(IvyXml.getChild(pxml,"PROJECT"),"FILES");
   
   for (Element fe : IvyXml.children(p1,"FILE")) {
      if (IvyXml.getAttrBool(fe,"SOURCE")) {
	 File f2 = new File(IvyXml.getText(fe));
	 if (f2.exists() && f2.getName().endsWith(".java")) {
	    try {
	       f2 = f2.getCanonicalFile();
	     }
	    catch (IOException e) {
	       continue;
	     }
            IvyLog.logD("DIANALYSIS","Add source file " + f2);
	    rslt.add(f2);
	  }
       }
    }
   
   return rslt;
}




}       // end of class DianalysisFactory




/* end of DianalysisFactory.java */

