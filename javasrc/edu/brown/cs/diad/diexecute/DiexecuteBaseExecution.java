/********************************************************************************/
/*                                                                              */
/*              DiexecuteBaseExecution.java                                     */
/*                                                                              */
/*      Create the base execution if possible                                   */
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



package edu.brown.cs.diad.diexecute;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dianalysis.DianalysisManager;
import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadExecution;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadRepair;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DiexecuteBaseExecution implements DiexecuteConstants, DiadExecution
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiexecuteManager exec_manager;
private DiadSymptom     for_symptom;
private DiadThread      for_thread;
private DiadStackFrame  start_frame;
private DiexecuteExecution base_execution;
private List<DiexecuteAction> setup_actions; 
private String          base_session;

private int             num_checked;
private long            seede_total;
private double          best_score;
private double          location_priority;
private double          repair_priority;
private double          finder_priority;

private boolean         show_all;
private boolean         show_strings;
private boolean         show_arrays;
private int             max_checked_ok;
private int             min_checked_ok;
private long            max_seede_ok;
private long            min_seede_ok;
private int             max_checked;
private long            max_seede_total;
private double          good_score;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteBaseExecution(DiexecuteManager mgr,DiadSymptom symp,DiadThread thrd,
      DiadStackFrame start)
{
   exec_manager = mgr;
   for_symptom = symp;
   for_thread = thrd;
   start_frame = start;
   base_session = null;
   num_checked = 0;
   seede_total = 0;
   location_priority = 1.0;
   repair_priority = 1.0;
   finder_priority = 1.0;
   best_score = 0;
   setup_actions = new ArrayList<>();
   
   DicontrolMain ctrl = mgr.getDiadControl();
   
   show_all = ctrl.getProperty("Diad.trace.show.all",false);
   show_strings = ctrl.getProperty("Diad.trace.show.strings",false);
   show_arrays = ctrl.getProperty("Diad.trace.show.arrays",false);
   max_checked_ok = ctrl.getProperty("Diad.max.checked.ok",MAX_CHECKED_OK);
   min_checked_ok = ctrl.getProperty("Diad.min.checked.ok",MIN_CHECKED_OK);
   max_seede_ok = ctrl.getProperty("Diad.max.seede.ok",MAX_SEEDE_OK); 
   min_seede_ok = ctrl.getProperty("Diad.min.seede.ok",MIN_SEEDE_OK);
   max_checked = ctrl.getProperty("Diad.max.checked",MAX_CHECKED);
   max_seede_total = ctrl.getProperty("Diad.max.seede.total",MAX_SEEDE_TOTAL);
   good_score = ctrl.getProperty("Diad.good.score",GOOD_SCORE); 
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public DiadThread getThread() 
{
   return for_thread;
}


@Override public DiadSymptom getSymptom() 
{
   return for_symptom;
}


@Override public DiexecuteTrace getExecutionTrace()
{
   return base_execution.getSeedeResult();
}


@Override public Collection<DiadLocation> getExecutedLocations(Collection<DiadLocation> base)
{
   DiexecuteCall call = getExecutionTrace().getRootContext();
   Set<String> used = new HashSet<>();
   call.getExecutedLocations(used);
   
   List<DiadLocation> rslt = new ArrayList<>();
   for (DiadLocation loc : base) {
      String s = loc.getFile().getPath() + "@" + loc.getStatementLine();
      if (!used.contains(s)) {
         IvyLog.logD("DIEXECUTE","IGNORE location " + s + " because it isn't executed");
       }
      else {
         rslt.add(loc);
       }
    }
   return rslt;
}


DiexecuteManager getManager()
{
   return exec_manager;
}


/********************************************************************************/
/*                                                                              */
/*      Processing method                                                       */
/*                                                                              */
/********************************************************************************/

DiadTrace createBaseExecution()
{
   DicontrolMain diad = exec_manager.getDiadControl();
   DiadStackFrame frm = for_thread.getStack().getUserFrame();
   File sf = frm.getSourceFile();
   DisourceManager srcmgr = diad.getSourceManager();
   DianalysisManager analmgr = diad.getAnalysisManager();
   String proj = srcmgr.getProjectForFile(sf);
   
   CommandArgs args = new CommandArgs("TYPE","LAUNCH",
         "PROJECT",proj,
         "THREADID",for_thread.getThreadId(),
         "FRAMEID",start_frame.getFrameId());  
   if (show_all) args.put("SHOWALL",true);
   if (show_strings) args.put("TOSTRING",true);
   if (show_arrays) args.put("TOARRAY",true);
   
   Element rslt = diad.sendSeedeMessage(null,"BEGIN",args,null);
   if (!IvyXml.isElement(rslt,"RESULT")) {
      IvyLog.logE("DIEXECUTE","No result from BEGIN message");
      return null;
    }
   Element sessxml = IvyXml.getChild(rslt,"SESSION");
   base_session = IvyXml.getAttrString(sessxml,"ID");
   if (base_session == null) return null;
   if (Thread.currentThread().isInterrupted()) return null;
   
   // Add all the loaded files
   IvyXmlWriter xw = new IvyXmlWriter();
   Collection<File> files = analmgr.getSeedeFiles(for_thread);  
   for (File f : files) {
      xw.begin("FILE");
      xw.field("NAME",f.getPath());
      xw.end("FILE");
    }
   String cnts = xw.toString();
   xw.close();
   diad.sendSeedeMessage(base_session,"ADDFILE",null,cnts);
   if (Thread.currentThread().isInterrupted()) return null;
   
   DiexecuteChangedItems valuechanges = new DiexecuteChangedItems(exec_manager,for_thread,start_frame,for_symptom);
   if (Thread.currentThread().isInterrupted()) return null;
   
   runBaseExecution(null);
   IvyLog.logI("DIEXECUTE","BASE EXECUTION STEPS " + 
         base_execution.getSeedeResult().getSymptomTime());
   if (base_execution.getSeedeResult().getSymptomTime() >= 0) {
      return base_execution.getSeedeResult();
    }
   if (Thread.currentThread().isInterrupted()) return null;
   
   List<DiexecuteAction> pchanges = valuechanges.getParameterActions();
   if (pchanges != null) setup_actions.addAll(pchanges);
   if (setup_actions.size() > 0 && checkBaseExecution(null)) {
      return base_execution.getSeedeResult();
    }
   
   // this needs to be more sophisticated to try multiple changes in series
   List<DiexecuteAction> changes = valuechanges.getResetActions(exec_manager,base_execution);
   if (changes != null) {
      for (DiexecuteAction va : changes) {
         if (Thread.currentThread().isInterrupted()) return null;
         if (checkBaseExecution(va)) {
            setup_actions.add(va);
            return base_execution.getSeedeResult();
          }
       }
    }
   
   IvyLog.logE("DIEXECUTE","BAD BASE EXECUTION");
   
   return null;
}



private boolean checkBaseExecution(DiexecuteAction va)
{
   Element rslt = exec_manager.sendSeedeMessage(base_session,"SUBSESSION",null,null);
   if (!IvyXml.isElement(rslt,"RESULT")) return true;
   Element sessxml = IvyXml.getChild(rslt,"SESSION");
   String ssid = IvyXml.getAttrString(sessxml,"ID");
   try {
      boolean first = true;
      for (DiexecuteAction vs : setup_actions) {
         vs.perform(exec_manager,ssid,for_thread,first); 
         first = false;
       }
      if (va != null) {
         va.perform(exec_manager,ssid,for_thread,first);
         first = false;
       }
      DiexecuteExecution oexec = base_execution;
      runBaseExecution(ssid);
      if (base_execution.getSeedeResult().getSymptomTime() >= 0) return true;
      if (oexec != null) base_execution = oexec;           // else ignore
      return false;
    }
   finally {
      removeSubsession(ssid);
    }
}



void runBaseExecution(String sid)
{
   if (sid == null) sid = base_session;
   base_execution = new DiexecuteExecution(sid,this,null);
   if (Thread.currentThread().isInterrupted()) return;
   base_execution.start(exec_manager);
   if (Thread.currentThread().isInterrupted()) return;
   
   DiexecuteTrace vt = base_execution.getSeedeResult();
   if (vt != null) vt.setupForLaunch(for_thread); 
}


DiexecuteExecution getSubsession(DiadRepair repair) 
{
   if (base_session == null) return null;
   
   Element rslt = exec_manager.sendSeedeMessage(base_session,"SUBSESSION",null,null);
   if (!IvyXml.isElement(rslt,"RESULT")) return null;
   Element sessxml = IvyXml.getChild(rslt,"SESSION");
   String ssid = IvyXml.getAttrString(sessxml,"ID");
   if (ssid == null) return null;
   if (setup_actions != null) {
      boolean first = true;
      for (DiexecuteAction va : setup_actions) {
         va.perform(exec_manager,ssid,for_thread,first);
         first = false;
       }
    }
   
   DiexecuteExecution ve = new DiexecuteExecution(ssid,this,repair);
   
   return ve;
}


void removeSubsession(String ssid)
{
   exec_manager.sendSeedeMessage(ssid,"REMOVE",null,null);
   exec_manager.unregister(ssid);
}


String handleEdits(String ssid,String edits)
{
   Element rslt = exec_manager.sendSeedeMessage(ssid,"EDITFILE",null,edits);
   
   String sts = IvyXml.getAttrString(rslt,"STATUS");
   if (sts == null) sts = "FAIL";
   return sts;
}

@Override public void clear()
{
   if (base_session == null) return;
   removeSubsession(base_session);
   base_session = null;
}



/********************************************************************************/
/*                                                                              */
/*      Execution comparison methods                                            */
/*                                                                              */
/********************************************************************************/

double checkValidResult(DiexecuteExecution ve)
{
   DiexecuteTrace e2 = base_execution.getSeedeResult();
   DiexecuteTrace e1 = ve.getSeedeResult();
   if (e1 == null || e1.isCompilerError()) return 0;
   if (ve.getRepair() == null) return 1; 
   
   DiexecuteChecker checker = new DiexecuteChecker(this,
         e2,e1,ve.getRepair()); 
   
   return checker.check();
}


public boolean checkTestResult(DiexecuteTrace testtrace)
{
   DiexecuteTrace testtr = (DiexecuteTrace) testtrace;
   DiexecuteTrace origtrace = base_execution.getSeedeResult();
   if (testtr.isCompilerError()) return false;
   DiexecuteChecker checker = new DiexecuteChecker(this,origtrace,testtr,null);
   boolean fg = checker.checkTest();
   return fg;
}



public synchronized boolean canCheckResult(double locpri,double findpri)
{
   if (base_execution.getSeedeResult().getSymptomTime() < 0) 
      return false;
   
   boolean rslt = true;
   if (haveGoodResult()) {
      if (num_checked > max_checked_ok) rslt = false;
      if (num_checked < min_checked_ok && seede_total > max_seede_ok) rslt = false;
      if (!rslt && forceCheck(locpri,findpri)) rslt = true;
    }
   if (num_checked > max_checked) rslt = false;
   else if (seede_total < min_seede_ok) rslt = true;
   else if (seede_total > max_seede_total) rslt = false;
   
   if (rslt)  return rslt;
   
   return rslt;
}


public boolean haveGoodResult()
{
   return best_score >= good_score;
}


public boolean forceCheck(double locpri,double findpri)
{
   if (locpri < location_priority * 0.8) return false;
   
   double usepri = 0.95;
   double bestrepair = (locpri/4.0 + 0.75 +  1.0  + usepri)/3.0 * findpri;
   if (bestrepair < repair_priority) return false;
   
   IvyLog.logD("VALDIATE","Force check at this point");
   
   return true;
}




synchronized void noteSeedeLength(long t,DiadRepair repair,double score) 
{
   num_checked++;
   seede_total += t;
   if (score > best_score) { 
      best_score = score;
      double p = repair.getLocation().getPriority();
      if (p < location_priority) location_priority = p;
      double p1 = repair.getPriority();
      if (p1 < repair_priority) repair_priority = p1;
      double p2 = repair.getFinderPriority();
      if (p2 < finder_priority) finder_priority = p2;
    }
   repair.setCount(num_checked); 
   repair.setSeedeCount(seede_total);
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public JSONObject getJsonExecTrace()
{
   return base_execution.getSeedeResult().getJsonExecTrace();
}


@Override public JSONObject getJsonLocalTrace(String callid) 
{
   return base_execution.getSeedeResult().getJsonLocalTrace(callid);  
}

@Override public JSONArray getJsonLineTrace(String callid)
{
   return base_execution.getSeedeResult().getJsonLineTrace(callid);  
}

@Override public JSONObject getJsonVarTrace(String callid,String var)
{ 
   return base_execution.getSeedeResult().getJsonVarTrace(callid,var); 
}


@Override public JSONObject getJsonVarHistory(String callid,String var,
      int line,long when)
{ 
   return base_execution.getSeedeResult().getJsonVarHistory(callid,var,line,when);  
}


@Override public JSONObject getJsonVarValue(String callid,String var,
      int line,long when)
{ 
   return base_execution.getSeedeResult().getJsonVarValue(callid,var,line,when);   
}



}       // end of class DiexecuteBaseExecution




/* end of DiexecuteBaseExecution.java */

