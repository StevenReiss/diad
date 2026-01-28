/********************************************************************************/
/*                                                                              */
/*              DicontrolCandidate.java                                         */
/*                                                                              */
/*      Candidate for bug repair                                                */
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



package edu.brown.cs.diad.dicontrol;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dianalysis.DianalysisManager;
import edu.brown.cs.diad.dicore.DiadCandidateCallback;
import edu.brown.cs.diad.dicore.DiadExecution;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.diexecute.DiexecuteManager;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.swing.SwingEventListenerList;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DicontrolCandidate implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private DiadThread      for_thread;
private DiadStackFrame  for_frame;
private DiadCandidateState  candidate_state; 
private DiadSymptom     candidate_symptom;
private Collection<DiadLocation> location_set;
private DiadStackFrame  start_frame;
private DiadExecution   base_execution;
private Collection<DiadLocation> exec_locations;
private String          candidate_id;
private SwingEventListenerList<DiadCandidateCallback> candidate_listeners;
private CandidateThread candidate_processor;
private Set<File>       candidate_files;
private DiadAnalysisFileMode file_mode;

private static AtomicInteger candidate_counter = new AtomicInteger(0);



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolCandidate(DicontrolMain ctrl,DiadThread thrd)
{
   diad_control = ctrl;
   for_thread = thrd;
   candidate_state = DiadCandidateState.FINDING_SYMPTOM; 
   candidate_symptom = null;
   location_set = null;
   start_frame = null;
   base_execution = null;
   exec_locations = null;
   
   candidate_listeners = new SwingEventListenerList<>(DiadCandidateCallback.class);
   candidate_processor = null;
   candidate_id = "DIAD_C_" + candidate_counter.incrementAndGet();
   candidate_files = new HashSet<>();
   
   file_mode = diad_control.getProperty("Diad.file.mode",
         DiadAnalysisFileMode.FAIT_FILES);
   
   IvyLog.logD("DICONTROL","Setup candidate " + candidate_id + " for " + thrd);
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DiadThread getThread()                          { return for_thread; }
DiadCandidateState getState()                   { return candidate_state; }
DiadSymptom getSymptom()                        { return candidate_symptom; }
String getId()                                  { return candidate_id; }

void addCandidateListener(DiadCandidateCallback cb)
{
   candidate_listeners.add(cb);
}

void removeCandidateListener(DiadCandidateCallback cb)
{
   candidate_listeners.remove(cb);
}


/********************************************************************************/
/*                                                                              */
/*      State change methods                                                    */
/*                                                                              */
/********************************************************************************/

void setState(DiadCandidateState st)
{
   if (st == candidate_state) return;
   
   IvyLog.logD("DICONTROL","Set candidate " + candidate_id + " = " + st);
   
   candidate_state = st;
   if (st == DiadCandidateState.DEAD) {
      // need to leave thread alone
      CommandArgs args = new CommandArgs("DEBUGID",candidate_id);
      diad_control.sendLimbaMessage("DEBUGREMOVE",args,null);
      for_frame = null;
      candidate_symptom = null;
      location_set = null;
      start_frame = null;
      base_execution = null;
      exec_locations = null;
      candidate_files.clear();
    }
   
   for (DiadCandidateCallback cb : candidate_listeners) {
      cb.stateChanged();
    }
}

/********************************************************************************/
/*                                                                              */
/*      Action methods                                                          */
/*                                                                              */
/********************************************************************************/

void start() 
{
   if (candidate_processor != null) {
      stopProcessing();
    }
   
   IvyLog.logD("DICONTROL","Start candidate " + candidate_id +
         " " + candidate_state);

   switch (candidate_state) {
      case FINDING_SYMPTOM :
      case DOING_ANALYSIS :
      case FINDING_EXECUTED_LOCATIONS :
      case DOING_BASE_EXECUTION :
         candidate_processor = new CandidateThread();
         candidate_processor.start();
         break;
      case NO_SYMPTOM_FOUND :   
         break;
      case NO_USER_STACK :
      case DEAD :
         candidate_state = DiadCandidateState.DEAD;
         break;
    }
}


void setSymptom(DiadSymptom symp)
{
   // set symptom if possible and restart
}

void addFiles(Collection<File> files)
{
   // add to files, if any new files, restart
}

void setFileMode(DiadAnalysisFileMode mode)
{
   file_mode = mode;
   // restart analysis
}

void terminate()
{
   stopProcessing();
}


private synchronized void stopProcessing()
{
   IvyLog.logD("DICONTROL","Stop processing " + getId() + 
         candidate_processor);
   
   while (candidate_processor != null && candidate_processor.isAlive()) {
      if (!candidate_processor.isInterrupted()) {
         IvyLog.logD("DICONTROL","Interrupting processor");
         candidate_processor.interrupt();
       }
      try {
         wait(100);
       }
      catch (InterruptedException e) { }  
    }
   
   setState(DiadCandidateState.DEAD);
   candidate_processor = null;
}



/********************************************************************************/
/*                                                                              */
/*      Query methods : Stack                                                   */
/*                                                                              */
/********************************************************************************/

JSONArray getJsonStack()
{
   JSONArray rslt = new JSONArray();
   boolean use = false;
   for (DiadStackFrame frm : for_thread.getStack().getFrames()) {
      if (!use && frm.equals(for_frame)) use = true;
      if (use) {
         JSONObject jo = frm.toJson(); 
         rslt.put(jo);
       }
      if (frm.equals(start_frame)) use = false;
    }
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Query methods : Locations                                               */
/*                                                                              */
/********************************************************************************/

JSONArray getJsonLocations(boolean all)
{
   JSONArray rslt = new JSONArray();
   
   Collection<DiadLocation> base = (all ? location_set : exec_locations);
   
   Map<String,List<DiadLocation>> bymethod = new LinkedHashMap<>();
   for (DiadLocation dloc : base) {
      String m = dloc.getFullMethod();
      List<DiadLocation> ll = bymethod.get(m);
      if (ll == null) {
         ll = new ArrayList<>();
         bymethod.put(m,ll);
       }
      ll.add(dloc);
    }
   
   for (Map.Entry<String,List<DiadLocation>> ent : bymethod.entrySet()) {
      Map<Integer,LocationSummary> found = new TreeMap<>();
      for (DiadLocation loc1 : ent.getValue()) {
         LocationSummary sum = found.get(loc1.getLineNumber());
         if (sum == null) {
            sum = new LocationSummary(loc1);
            found.put(loc1.getLineNumber(),sum);
          }
         else {
            sum.merge(loc1);
          }
       }
      JSONObject obj = new JSONObject();
      obj.put("METHOD",ent.getKey());
//    DiadLocation loc0 = ent.getValue().get(0);
//    obj.put("FILE",loc0.getFile());
//    obj.put("START_POSITION",loc0.getMethodOffset());
//    obj.put("END_POSITION",loc0.getMethodEndOffset());
      JSONArray arr = new JSONArray();
      for (LocationSummary sum : found.values()) {
         int lno = sum.getLineNumber();
         arr.put(lno);
//       JSONObject sobj = sum.toJson();
//       arr.put(sobj);
       }
      obj.put("LINES",arr);
      rslt.put(obj);
    }
   
   return rslt;
}


private static class LocationSummary {
  
   private int line_number;
   private int start_offset;
   private int end_offset;
   private double loc_priority;
   
   LocationSummary(DiadLocation loc) {
      line_number = loc.getLineNumber();
      start_offset = loc.getStartOffset();
      end_offset = loc.getEndOffset();
      loc_priority = loc.getPriority();
    }
   
   void merge(DiadLocation loc) {
      start_offset = Math.min(start_offset,loc.getStartOffset());
      end_offset = Math.max(end_offset,loc.getEndOffset());
      loc_priority = Math.max(loc_priority,loc.getPriority());
    }
   
// JSONObject toJson() {
//    JSONObject rslt = new JSONObject();
//    rslt.put("LINE",line_number); 
//    rslt.put("PRIORITY",loc_priority);
//    return rslt;
//  }
   
   int getLineNumber()          { return line_number; }
   
}       // end of inner class LocationSummary



/********************************************************************************/
/*                                                                              */
/*      Query methods ; executions                                              */
/*                                                                              */
/********************************************************************************/

JSONObject getJsonExecTrace()
{
   if (base_execution == null) return null;
   
   return base_execution.getJsonExecTrace(); 
}


JSONObject getJsonLocalTrace(String callid)
{
   return base_execution.getJsonLocalTrace(callid); 
}


JSONArray getJsonLineTrace(String callid)
{
   if (base_execution == null) return null;
   
   return base_execution.getJsonLineTrace(callid); 
}


JSONObject getJsonVarTrace(String callid,String var)
{
   return base_execution.getJsonVarTrace(callid,var); 
}


JSONObject getJsonVarHistory(String callid,String var,int line,long when)
{
   return base_execution.getJsonVarHistory(callid,var,line,when); 
}


JSONObject getJsonVarValue(String callid,String var,int line,long when)
{
   return base_execution.getJsonVarValue(callid,var,line,when);
}


/********************************************************************************/
/*                                                                              */
/*      LLM interface methods                                                   */
/*                                                                              */
/********************************************************************************/

Element askLimba(IvyXmlWriter xw,DiadAskType typ,String query)
{
   if (candidate_state != DiadCandidateState.READY) return null;
   
   Map<String,String> keymap = diad_control.getKeyMap();
   keymap.put("SYMPTOM",candidate_symptom.getText()); 
   keymap.put("DEBUGID",candidate_id);
   keymap.put("THREAD",for_thread.getThreadId());
   keymap.put("FRAME",for_frame.getFrameId());
   keymap.put("STARTFRAME",start_frame.getFrameId());
   keymap.put("METHOD",for_frame.getFullMethodName());
   keymap.put("LINE",String.valueOf(for_frame.getLineNumber()));
   keymap.put("PROCESS",for_thread.getProcessId());
   int cid = base_execution.getExecutionTrace().getRootContext().getContextId();
   keymap.put("CALLID",String.valueOf(cid));
   
   String prompt = diad_control.getPrompt(typ.toString());
   prompt = IvyFile.expandName(prompt,keymap);
   
   String ask = diad_control.getQuery(typ.toString()); 
   ask = IvyFile.expandName(ask,keymap);
   
   if (ask == null && query != null) ask = query;
   else if (query != null) ask = ask + " " + query;
   
   CommandArgs args = new CommandArgs("USECONTEXT",true,
         "ID",getId(),
         "TOOLS","PROJECT,DEBUG");
   
   IvyXmlWriter xw1 = new IvyXmlWriter();
   xw1.cdataElement("PROMPT",prompt);
   xw1.begin("CONTEXT");
   xw1.field("KEY","DEBUGID");
   xw1.field("VALUE",candidate_id);
   xw1.end("CONTEXT");
   xw1.cdataElement("CONTENTS",ask);
   String cnts = xw1.toString();
   xw1.close();
   
   Element rslt = diad_control.sendLimbaMessage("QUERY",args,cnts);
   
   IvyLog.logD("DICONTROL","LIMBA RESULT: " + IvyXml.convertXmlToString(rslt));
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw) 
{
   xw.begin("CANDIDATE");
   xw.field("ID",candidate_id);
   xw.field("STATE",candidate_state);
   if (for_thread != null) for_thread.outputXml(xw);
   if (for_frame != null) for_frame.outputXml(xw);
   if (candidate_symptom != null) {
      candidate_symptom.outputXml(xw);
    }
   if (start_frame != null) {
      xw.begin("STARTFRAME");
      start_frame.outputXml(xw);
      xw.end("STARTFRAME");
    }
   if (candidate_files != null) {
      xw.begin("FILES");
      for (File f : candidate_files) {
         xw.textElement("FILE",f.getPath());
       }
      xw.end("FILES");
    }
   if (exec_locations != null) {
      xw.begin("EXECLOCATIONS");
      for (DiadLocation loc : exec_locations) {
         loc.outputXml(xw);
       }
      xw.end("EXECLOCATIONS");
    }
   xw.end("CANDIDATE");
}



/********************************************************************************/
/*                                                                              */
/*      Thread to process the candidate                                         */
/*                                                                              */
/********************************************************************************/

private final class CandidateThread extends Thread {
   
   CandidateThread() {
      super("CandidateProcessor_" + for_thread.getThreadName()); 
    }
   
   @Override public void run() {
      DianalysisManager anal = diad_control.getAnalysisManager();
      DiexecuteManager exec = diad_control.getExecuteManager();
      
      for ( ; ; ) {
         try {
            checkInterrupted();
            IvyLog.logD("DICONTROL","Candidate " + candidate_id + " :: " +
                  candidate_state);
            switch (candidate_state) {
               case FINDING_SYMPTOM :  
                  candidate_symptom = null;
                  base_execution = null;
                  location_set = null;
                  exec_locations = null;
                  start_frame = null;
                  DiadStack stk = for_thread.getStack();
                  if (stk == null || for_thread.isInternal()) { 
                     setState(DiadCandidateState.NO_USER_STACK);
                     return;
                   }
                  if (checkInterrupted()) break;
                  for_frame = stk.getUserFrame();
                  if (for_frame == null) {
                     setState(DiadCandidateState.NO_USER_STACK);
                     return;
                   }
                  if (checkInterrupted()) break;
                  DicontrolSymptomFinder finder =
                     new DicontrolSymptomFinder(diad_control,for_thread,
                           stk,for_frame);
                  candidate_symptom = finder.findSymptom();
                  if (checkInterrupted()) break;
                  if (candidate_symptom != null) {
                     IvyLog.logD("DICONTROL","Candidate Symptom " + 
                           candidate_symptom.getText());
                     setState(DiadCandidateState.DOING_ANALYSIS);
                   }
                  else {
                     setState(DiadCandidateState.NO_SYMPTOM_FOUND);
                   }
                  break;
               case DOING_ANALYSIS :
                  if (checkInterrupted()) break;
                  anal.addFiles(file_mode,candidate_files,for_thread);  
                  if (checkInterrupted()) break;
                  Boolean fg = anal.waitForAnalysis(); 
                  if (fg == null || checkInterrupted()) break;
                  if (fg) {
                     setState(DiadCandidateState.FINDING_ALL_LOCATIONS);
                   }
                  else {
                     setState(DiadCandidateState.NO_ANALYSIS);
                   }
                  break;
               case FINDING_ALL_LOCATIONS :
                  location_set = null;
                  if (checkInterrupted()) break;
                  Collection<DiadLocation> locs = anal.findInitialLocations(
                        candidate_symptom,for_thread);
                  if (checkInterrupted()) break;
                  if (locs == null ||locs.isEmpty()) {
                     setState(DiadCandidateState.NO_LOCATIONS_FOUND); 
                   } 
                  else {
                     location_set = locs;
                     setState(DiadCandidateState.FINDING_STARTING_FRAME);
                   }
                  break; 
               case FINDING_STARTING_FRAME :
                  start_frame = null;
                  if (checkInterrupted()) break;
                  start_frame = exec.getStartingFrame(candidate_symptom,
                        for_thread,location_set);
                  if (checkInterrupted()) break;
                  if (start_frame == null) {
                     setState(DiadCandidateState.NO_START_FRAME);
                   }
                  else {
                     setState(DiadCandidateState.DOING_BASE_EXECUTION);
                   }
                  break;
               case DOING_BASE_EXECUTION :
                  base_execution = exec.createBaseExecution(candidate_symptom,  
                        for_thread,start_frame);
                  if (checkInterrupted()) break;
                  if (base_execution == null) {
                     setState(DiadCandidateState.NO_BASE_EXECUTION); 
                   }
                  else {
                     setState(DiadCandidateState.FINDING_EXECUTED_LOCATIONS);
                   }
                  break;
               case FINDING_EXECUTED_LOCATIONS :
                  exec_locations = base_execution.getExecutedLocations(location_set); 
                  if (exec_locations == null) {
                     setState(DiadCandidateState.NO_FINAL_LOCATIONS);   
                   }
                  else {
                     setState(DiadCandidateState.PREPARING_DATA);
                   } 
                  // restrict location set by base execution
                  break;
               case PREPARING_DATA :
                  // might want to find repairs 
                  setState(DiadCandidateState.READY);
                  break;
               case NO_SYMPTOM_FOUND :
               case READY : 
                  synchronized (this) {
                     for ( ; ; ) {
                        try {
                           wait(10000);
                         }
                        catch (InterruptedException e) {
                           break;
                         }
                      }
                   }
                  return;
               case DEAD :
               case INTERUPTED : 
                  cleanup(true);
                  return;
               case NO_USER_STACK :
               case NO_ANALYSIS :
               case NO_START_FRAME :
               case NO_BASE_EXECUTION :
               case NO_LOCATIONS_FOUND :
               default :
                  cleanup(false);
                  // need to remove base execution from seede
                  return;
             }
          }
         catch (Throwable e) {
            if (isInterrupted()) {
               return;
             }
            IvyLog.logE("DICONTROL","Problem processing candidate",e);
            setState(DiadCandidateState.DEAD);
            return;
          }
       }
    }
   
   
   private boolean checkInterrupted() {
      if (isInterrupted()) {
         IvyLog.logD("DICONTROL","Candidate interrupted");
         setState(DiadCandidateState.INTERUPTED); 
         return true;
       }
      return false;
    }
   
   private void cleanup(boolean all) {
      if (all) {
         candidate_symptom = null;
         location_set = null;
         exec_locations = null;
         start_frame = null;
       }
      if (base_execution != null) {
         base_execution.clear();  
         base_execution = null;
       }
    }
   
}       // end of inner class CandidateThread





}       // end of class DicontrolCandidate




/* end of DicontrolCandidate.java */

