/********************************************************************************/
/*                                                                              */
/*              DianalysisLocations.java                                        */
/*                                                                              */
/*      Handle fault localization using FAIT                                     */
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.disource.DisourceFactory;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAnnotation;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSymbol;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisLocations implements DianalysisConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DianalysisFactory for_analysis;
private DiadSymptom for_symptom;
private DiadThread  for_thread;
private Set<File> seede_files;
 


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/
 
DianalysisLocations(DianalysisFactory anal,DiadSymptom sym,DiadThread thrd)
{
   for_analysis = anal;
   for_symptom = sym;
   for_thread = thrd;
   seede_files = null;
}



/********************************************************************************/
/*                                                                              */
/*      Find initial locations                                                  */
/*                                                                              */
/********************************************************************************/

Collection<DiadLocation> findInitialLocations()
{
   DianalysisHistory hq = setupHistory();
   Set<String> execlocs = null;
   Set<File> oldfiles = seede_files;
   seede_files = null;
   List<DiadLocation> rslt = new ArrayList<>();
   
   if (hq == null) {
      IvyLog.logE("DIANALYSIS","No location history for " + for_symptom);
      return null;
    }
   
   DisourceFactory src = for_analysis.getSourceManager();
   Element xml = null;
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      hq.process(xw);
      xml = IvyXml.convertStringToXml(xw.toString());
    }
   catch (DiadException e) {
      IvyLog.logE("DIANALYSIS","Problem finding locations for problem",e);
      return null;
    }
   Set<File> usefiles = new HashSet<>();
   for (Element nodes : IvyXml.children(xml,"NODES")) {
      IvyLog.logD("DIANALYSIS","RESULT OF LOCATION QUERY " + IvyXml.convertXmlToString(nodes));
      Map<String,DiadLocation> done = new HashMap<>();
      int ctr0 = 0;
      int ctr1 = 0;
      int ctr2 = 0;
      int ctr3 = 0;
      for (Element n : IvyXml.children(nodes,"NODE")) {
         ++ctr0;
         double p = IvyXml.getAttrDouble(n,"PRIORITY");
         String reason = IvyXml.getAttrString(n,"REASON");
         Element locelt = IvyXml.getChild(n,"LOCATION");
         String fnm = IvyXml.getAttrString(xml,"FILE");
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
         if (!isLocationRelevant(src,loc)) {
            ++ctr3;
            continue;
          }
         String s = loc.getFile().getPath() + "@" + loc.getStatementLine();
         if (execlocs != null && !execlocs.contains(s)) {
            IvyLog.logD("DIANALYSIS","IGNORE location " + s + 
                  " because it isn't executed");
            ++ctr1;
            continue;
          }
         usefiles.add(loc.getFile());
         DiadLocation oloc = done.putIfAbsent(s,loc);
         if (oloc != null) {
            double p2 = oloc.getPriority();
            if (p1 > p2) oloc.setPriority(p1);
            ++ctr2;
          }
         else {
            IvyLog.logD("DIANALYSIS","USE LOCATION " + loc);
            rslt.add(loc);
          }
       }   
      IvyLog.logI("DIANALYSIS","LOOK AT " + ctr0 + " LOCATIONS, ELIMINATE " + ctr1 + 
            " DUP " + ctr2 + " IRRELEVANT " + ctr3 + " USE " + (ctr0-ctr1-ctr2-ctr3) + 
            " FILES " + usefiles.size());
      if (!usefiles.isEmpty()) {
         Set<File> sfiles = new HashSet<>();
         if (oldfiles != null) oldfiles.removeAll(usefiles);
         for (File f : usefiles) {
            if (f.getName().endsWith("Exception.java")) continue;
            sfiles.add(f);
          }
         handleRemoveSeedeFiles(oldfiles);
         seede_files = sfiles;
       }
    }
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Translate symptom into a location and variables                         */
/*                                                                              */
/********************************************************************************/

private DianalysisHistory setupHistory()
{
   DianalysisHistory hq = null;
   switch (for_symptom.getSymptomType()) {
      case ASSERTION :
         hq = new DianalysisAssertionHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case CAUGHT_EXCEPTION :
         break;
      case EXCEPTION :
         hq = new DianalysisExceptionHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case EXPRESSION :
         hq = new DianalysisExpressionHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case LOCATION :
         hq = new DianalysisLocationHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case NONE :
         break;
      case NO_EXCEPTION :
         break;
      case VARIABLE :
         hq = new DianalysisVariableHistory(for_analysis,
               for_symptom,for_thread);
         break;
    }
  
   return hq;
}


/********************************************************************************/
/*                                                                              */
/*      Check relevance of a location                                           */
/*                                                                              */
/********************************************************************************/

private boolean isLocationRelevant(DisourceFactory src,DiadLocation loc)
{
   for (String s : for_symptom.ignorePatterns()) {
      String nm = loc.getMethod(); 
      if (nm.matches(s)) return false;
    }
   
   if (for_symptom.ignoreMain() || 
         for_symptom.ignoreTests() || 
         for_symptom.ignoreDriver()) { 
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
            if (for_symptom.ignoreMain()) {
               if (js.getName().equals("main") && js.isStatic() &&
                     js.getType().getBaseType().isVoidType()) {
                  IvyLog.logD("DIANALYSIS","IGNORE MAIN " + js.getFullName());
                  return false;
                }
             }
            if (for_symptom.ignoreTests() && js.getAnnotations() != null) {
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
            if (for_symptom.ignoreTests() && js.getName().startsWith("test")) {
               IvyLog.logD("DIANALYSIS","IGNORE TEST " + js.getFullName());
               return false;
             }
            if (for_symptom.ignoreDriver()) {
               DiadLocation loc0 = for_symptom.getBugLocation();
               if (loc != null && loc.getMethod().equals(loc0.getMethod())) {
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
/*      Remove files used by seede                                              */
/*                                                                              */
/********************************************************************************/

private void handleRemoveSeedeFiles(Collection<File> files) 
{
   // This routine shoule be in the factory class to manage files associated
   //     with FAIT and Seede
        
   if (files == null || files.isEmpty()) return;
   
// for (Iterator<File> it = files.iterator(); it.hasNext(); ) {
//    File f1 = it.next();
//    if (!added_files.contains(f1)) it.remove();
//  }
   if (files.isEmpty()) return;
   
   StringBuffer buf = new StringBuffer();
   int ct = 0;
   for (File f1 : files) {
      if (files.contains(f1)) {
         buf.append("<FILE NAME='");
         buf.append(f1.getAbsolutePath());
         buf.append("' />");
         ++ct;
       }
    }
   
// if (ct > 0) {
//    loaded_files.removeAll(files); 
//    added_files.removeAll(files);
//    if (seede_files != null) seede_files.removeAll(files);
//    Element xw = sendSeedeMessage(session_id,"REMOVEFILE",null,buf.toString());
//    if (!IvyXml.isElement(xw,"RESULT")) {
//       RoseLog.logE("STEM","Problem removing seede files");
//     }
//  } 
}


}       // end of class DianalysisLocations




/* end of DianalysisLocations.java */

