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
import java.util.Set;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadConstants.DiadAnalysisFileMode;
import edu.brown.cs.diad.dicore.DiadConstants.DiadAnalysisState;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public class DianalysisFactory implements DianalysisConstants
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



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DianalysisFactory(DicontrolMain ctrl)
{
   diad_control = ctrl;
   loaded_files = new HashSet<>();
   done_allfiles = false;
   analysis_state = DiadAnalysisState.INITIAL;
}



/********************************************************************************/
/*                                                                              */
/*      Methods to add files and setup analysis                                 */
/*                                                                              */
/********************************************************************************/

public void addFiles(DiadAnalysisFileMode mode,Collection<File> files,DiadThread thrd)
{ 
   Set<File> use = new HashSet<>();
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
   
   if (files != null) use.addAll(files);
   if (add != null) use.addAll(files);
   
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
      loaded_files.addAll(files);
      String cnts = buf.toString();
      Element xw = diad_control.sendFaitMessage("ADDFILE",null,cnts);
      if (IvyXml.isElement(xw,"RESULT")) {
	 if (IvyXml.getAttrBool(xw,"ADDED")) {
            analysis_state = DiadAnalysisState.PENDING;
	  }
       }
      synchronized (this) {
         if (analysis_state == DiadAnalysisState.FILES) {
            analysis_state = DiadAnalysisState.READY;
            notifyAll();
          }
       }
    }
   else {
      IvyLog.logD("DIANALYSIS","No files to add for " + thrd.getThreadName() + " "
            + mode);
    }
}


/********************************************************************************/
/*                                                                              */
/*      Wait for analysis\                                                      */
/*                                                                              */
/********************************************************************************/

public synchronized boolean waitForAnalysis()
{
   long start = System.currentTimeMillis();
   for ( ; ; ) {
      switch (analysis_state) {
         case NONE :
            return false;
         case PENDING :
            break;
         case READY :
            return true;
       }
      try {
         wait(10000);
       }
      catch (InterruptedException e) { }
    }
}


/********************************************************************************/
/*                                                                              */
/*      File getting routines                                                   */
/*                                                                              */
/********************************************************************************/


private File getFileForClass(String cls)
{
   Set<String> files = new HashSet<>();
   files.add(cls);
   Set<File> rslt = new HashSet<>();
   File dir = findFilesForClasses(files,rslt);
   for (File f : rslt) {
      return f;
    }
   if (dir == null) return null;
   int idx = cls.lastIndexOf(".");
   String fnm = cls.substring(idx+1) + ".java";
   File frslt = new File(dir,fnm);
   return frslt;
}



private Set<File> findStackFiles(DiadThread thrd)
{
   Set<File> rslt = new HashSet<>();
   if (thrd == null) return rslt;
   String threadid = thrd.getThreadId();
   
   CommandArgs args = new CommandArgs("THREAD",threadid);
   // REPLACE USING DiadThread methods
// Element r = sendBubblesXmlReply("GETSTACKFRAMES",null,args,null);
   Element sf = IvyXml.getChild(r,"STACKFRAMES");
   for (Element th : IvyXml.children(sf,"THREAD")) {
      String id = IvyXml.getAttrString(th,"ID");
      if (threadid == null || threadid.equals(id)) {
	 for (Element frm : IvyXml.children(th,"STACKFRAME")) {
	    String fnm = IvyXml.getAttrString(frm,"FILE");
	    if (fnm != null) {
	       File f = new File(fnm);
               try {
                  f = f.getCanonicalFile();
                }
               catch (IOException e) {
                  IvyLog.logE("Problem getting canonical file " + f,e);
                }
               if (f.getPath().startsWith("/pro")) {
                  IvyLog.logD("DIANALYSIS","Canonical path added: " + f);
                }
	       if (f.exists()) {
		  rslt.add(f);
		}
	     }
	  }
       }
    }
   
   IvyLog.logD("DIANALYSIS","Done adding stack files");
   
   return rslt;
}



private Set<File> findComputedFiles(DiadThread thrd)
{
   if (thrd == null) return findAllSourceFiles();
   String threadid = thrd.getThreadId();
   Set<File> rslt = new HashSet<>();
   Set<File> roots = new HashSet<>();
   
   CommandArgs args = new CommandArgs("THREAD",threadid);
// Element r = sendBubblesXmlReply("GETSTACKFRAMES",null,args,null);
   // REPLACE USING thrd methods
   Element sf = IvyXml.getChild(r,"STACKFRAMES");
   for (Element th : IvyXml.children(sf,"THREAD")) {
      String id = IvyXml.getAttrString(th,"ID");
      if (threadid == null || threadid.equals(id)) {
         boolean fnd = false;
	 for (Element frm : IvyXml.children(th,"STACKFRAME")) {
            String fty = IvyXml.getAttrString(frm,"FILETYPE");
            if (!fty.equals("JAVAFILE")) {
               if (!fnd) continue;
               break;
             }
	    String fnm = IvyXml.getAttrString(frm,"FILE");
            if (fnm == null) break;
            int lno = IvyXml.getAttrInt(frm,"LINENO");
            File f = new File(fnm);
            if (!f.exists() || lno < 0) continue;
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
   if (base.size() < 40) return base;
   
   if (thrd == null) return base;
   String threadid = thrd.getThreadId();
   
   CommandArgs bargs = null;
   Element frslt = diad_control.sendFaitMessage("BEGIN",bargs,null);
   if (!IvyXml.isElement(frslt,"RESULT")) {
      throw new RuntimeException("BEGIN for session failed");
    }
   
   if (analysis_state == analysis_state.NONE) {
      analysis_state = analysis_state.PENDING; 
      CommandArgs aargs = new CommandArgs("REPORT","SOURCE");
      aargs.put("REPORT","FULL_STATS");
      int nth = getFaitThreads();
      if (nth > 0) aargs.put("THREADS",nth);
      Element arslt = diad_control.sendFaitMessage("ANALYZE",aargs,null);
      if (!IvyXml.isElement(arslt,"RESULT")) {
         analysis_state = analysis_state.NONE;
         throw new RuntimeException("ANALYZE for session failed");
       }
      waitForAnalysis();
      analysis_state = analysis_state.FILES;
    }
   
   Set<File> rslt = new HashSet<>();
   Set<String> mthds = new HashSet<>();
   
   CommandArgs args = new CommandArgs("THREAD",threadid);
   // REPLACE BY USING thrd.getStack();
   Element sf = IvyXml.getChild(r,"STACKFRAMES");
   for (Element th : IvyXml.children(sf,"THREAD")) {
      String id = IvyXml.getAttrString(th,"ID");
      if (threadid == null || threadid.equals(id)) {
         boolean fnd = false;
	 for (Element frm : IvyXml.children(th,"STACKFRAME")) {
            String fty = IvyXml.getAttrString(frm,"FILETYPE");
            if (fty == null || !fty.equals("JAVAFILE")) {
               if (!fnd) continue;
               break;
             }
	    String fnm = IvyXml.getAttrString(frm,"FILE");
            if (fnm == null) break;
            int lno = IvyXml.getAttrInt(frm,"LINENO");
            File f = new File(fnm);
            if (!f.exists() || lno < 0) continue;
            String snm = IvyXml.getAttrString(frm,"SIGNATURE");
            String mnm = IvyXml.getAttrString(frm,"METHOD");
            mthds.add(mnm+snm);
	  }
       }
    }
   
   IvyXmlWriter xw = new IvyXmlWriter();
   for (String s : mthds) {
      xw.textElement("METHOD",s);
    }
   Element clsxml = diad_control.sendFaitMessage("FILEQUERY",null,xw.toString());
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
	    rslt.add(f2);
	  }
       }
    }
   
   return rslt;
}




}       // end of class DianalysisFactory




/* end of DianalysisFactory.java */

