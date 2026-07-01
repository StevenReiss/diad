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
import edu.brown.cs.diad.dicore.DiadCandidate;
import edu.brown.cs.diad.dicore.DiadCandidateCallback;
import edu.brown.cs.diad.dicore.DiadRepair;
import edu.brown.cs.diad.dicore.DiadExecution;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.diad.diexecute.DiexecuteManager;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.swing.SwingEventListenerList;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DicontrolCandidate implements DicontrolConstants, DiadCandidate 
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
private DiadSymptom     user_symptom;
private Collection<DiadLocation> location_set;
private DiadStackFrame  start_frame;
private DiadStackFrame  user_frame;
private DiadExecution   base_execution;
private Collection<DiadLocation> exec_locations;
private String          query_response;
private String          candidate_id;
private SwingEventListenerList<DiadCandidateCallback> candidate_listeners;
private CandidateThread candidate_processor;
private Set<File>       candidate_files;
private DiadAnalysisFileMode file_mode;

private static AtomicInteger candidate_counter = new AtomicInteger(0);
private static final String QUERY_COMMAND =
   "<DIAD DO='ASKLIMBA' DEBUGID='$ID' TYPE='EXPLAIN' />";



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
   user_symptom = null;
   user_frame = null;
   query_response = null;
   
   candidate_listeners = new SwingEventListenerList<>(DiadCandidateCallback.class);
   candidate_processor = null;
   candidate_id = "DIAD_C_" + candidate_counter.incrementAndGet();
   candidate_files = new HashSet<>();
   
   file_mode = diad_control.getProperty("Diad.file.mode",
         DiadAnalysisFileMode.FAIT_FILES);
   
   IvyLog.logD("DICONTROL","Setup candidate " + candidate_id + 
         " for " + thrd.getThreadId());
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public DiadThread getThread()                { return for_thread; }
@Override public DiadCandidateState getState()         { return candidate_state; }
@Override public DiadSymptom getSymptom()              { return candidate_symptom; } 
@Override public String getId()                         { return candidate_id; } 
@Override public Collection<DiadLocation> getLocations() 
{
   return location_set; 
}

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
      query_response = null;
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

void start(DiadCandidateState start) 
{
   if (start != null) {
      if (candidate_state.ordinal() < start.ordinal()) {
         start = candidate_state;
       }
      if (candidate_state == DiadCandidateState.DEAD) {
         start = DiadCandidateState.INITIAL;
       }
    }
   
   if (candidate_processor != null) {
      stopProcessing(false);
    }
   
   if (start != null) {
      candidate_state = start; 
      if (start == DiadCandidateState.INITIAL) {
         candidate_symptom = null;
         location_set = null;
         exec_locations = null;
         start_frame = null;
         if (base_execution != null) {
            base_execution.clear();  
            base_execution = null;
          }
       }
    }
   
   IvyLog.logD("DICONTROL","Start candidate " + candidate_id +
         " " + candidate_state);

   switch (candidate_state) {
      case INITIAL :
      case FINDING_SYMPTOM :
      case DOING_ANALYSIS :
      case FINDING_STARTING_FRAME :
      case FINDING_ALL_LOCATIONS :
      case DOING_BASE_EXECUTION :
      case FINDING_EXECUTED_LOCATIONS :
      case PREPARING_DATA :
      case DOING_QUERY :
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
   user_symptom = symp;
   start(DiadCandidateState.INITIAL);
}

void addFiles(Collection<File> files)
{
   // add to files, if any new files, restart
}


DiadAnalysisFileMode getFileMode()
{
   return file_mode;
}

void setFileMode(DiadAnalysisFileMode mode)
{
   file_mode = mode;
   start(DiadCandidateState.DOING_ANALYSIS);
}

void setStartFrame(String frameid)
{
   DiadStackFrame frame = null;
   if (frameid != null) {
      for (DiadStackFrame fm : for_thread.getStack().getFrames()) {
         if (frameid.equals(fm.getFrameId())) {
            frame = fm;
            break;
          }
       }
      if (frame == null) {
         IvyLog.logE("DICONTROL","Start frame not found: " + frameid);
         return;
       }
    }
   user_frame = frame;
   start(DiadCandidateState.FINDING_STARTING_FRAME);
}


void terminate()
{
   stopProcessing(true);
}


private synchronized void stopProcessing(boolean report)
{
   IvyLog.logD("DICONTROL","Stop processing " + getId() + " " +
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
   
   if (report) {
      setState(DiadCandidateState.DEAD);
    }
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
/*      EVALUATE command                                                        */
/*                                                                              */
/********************************************************************************/

JSONObject getEvaluate(String frameid,String expr,int levels)
{
    JSONObject rslt = new JSONObject();
    DiadStackFrame usefrm = null;
    
    if (frameid == null) usefrm = null;
    else if (frameid.equals("0") || frameid.equals("*")) usefrm = for_frame;
    else {
        for (DiadStackFrame frm : for_thread.getStack().getFrames()) {
            if (frm.getFrameId().equals(frameid)) {
                usefrm = frm;
                break;
             }
         }
     }
    
    DiadValue dv = for_thread.evaluate(expr,usefrm);
   
    if (dv == null) {
        rslt.put("ERROR","No value returned");
     }
    else {
        rslt.put("VALUE",dv.toJson(levels)); 
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
   
   if (base == null) return rslt;
   
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

JSONObject getJsonLocalTrace(String callid)
{
   if (base_execution == null) return new JSONObject();
   
   return base_execution.getJsonLocalTrace(callid); 
}


JSONArray getJsonLineTrace(String callid)
{
   if (base_execution == null) return new JSONArray();
   
   return base_execution.getJsonLineTrace(callid); 
}


JSONObject getJsonVarTrace(String callid,String var)
{
   if (base_execution == null) return new JSONObject();
   
   return base_execution.getJsonVarTrace(callid,var); 
}


JSONObject getJsonVarHistory(String callid,String var,int line,long when)
{
   if (base_execution == null) {
      // try doing a flow query here
      return new JSONObject();
    }
   
   return base_execution.getJsonVarHistory(callid,var,line,when); 
}


JSONObject getJsonVarValue(String callid,String var,int line,long when)
{
   if (base_execution == null) return new JSONObject();
   
   return base_execution.getJsonVarValue(callid,var,line,when);
}


JSONArray getJsonMethodCalls(String method)
{
   return base_execution.getJsonMethodCalls(method); 
}


/********************************************************************************/
/*                                                                              */
/*      Validate interface                                                      */
/*                                                                              */
/********************************************************************************/

void validate(IvyXmlWriter xw,DiadRepair repair)
{
   DiadValidationStatus sts = DiadValidationStatus.NO_BASE_EXECUTION;
   if (base_execution != null) {
      sts = base_execution.validate(repair);  
    }
   
   xw.begin("VALIDATION");
   xw.field("STATUS",sts);
   xw.end("VALIDATION");
}




/********************************************************************************/
/*                                                                              */
/*      LLM interface methods                                                   */
/*                                                                              */
/********************************************************************************/

public Element askLimba(DiadAskType typ,String query,boolean nohistory)
{
   String tools = "PROJECT,DEBUG";
   switch (candidate_state) {
      case READY :
      case DOING_QUERY :
         tools = "PROJECT,DEBUG,DIAD";
         break;
      case NO_ANALYSIS :
      case NO_LOCATIONS_FOUND :
      case NO_BASE_EXECUTION :
      case NO_FINAL_LOCATIONS :
         tools = "PROJECT,DEBUG";
         break;
      default :
         return null;
    }
   if (base_execution == null && location_set == null) {
      if (typ == DiadAskType.EXPLAIN) typ = DiadAskType.BASEEXPLAIN;
      if (typ == DiadAskType.REPAIRS) typ = DiadAskType.BASEREPAIRS;
    }
   
   switch (typ) {
      case BASEEXPLAIN :
      case BASEREPAIRS :
         tools = "PROJECT,DEBUG";
         break;
      case BUILDER :
         tools = "PROJECT";
         break;
    }
   
   Map<String,String> keymap = diad_control.getKeyMap();
   keymap.put("SYMPTOM",candidate_symptom.getText()); 
   keymap.put("DEBUGID",candidate_id);
   keymap.put("THREAD",for_thread.getThreadId());
   keymap.put("FRAME",for_frame.getFrameId());
   keymap.put("STARTFRAME",start_frame.getFrameId());
   keymap.put("METHOD",for_frame.getFullMethodName());
   keymap.put("LINE",String.valueOf(for_frame.getLineNumber()));
   keymap.put("PROCESS",for_thread.getProcessId());
   keymap.put("EXTRA",query);
   boolean havelocs = location_set != null && !location_set.isEmpty();
   boolean execloca = exec_locations != null && !exec_locations.isEmpty();
   boolean haveexec = base_execution != null;
   if (havelocs) keymap.put("HAVELOCS","TRUE");
   if (execloca) keymap.put("EXECLOCS","TRUE");
   if (haveexec) keymap.put("HAVEEXEC","TRUE");
   
   if (base_execution != null) {
      int cid = base_execution.getExecutionTrace().getRootContext().getContextId();
      keymap.put("CALLID",String.valueOf(cid));
      int cid1 = base_execution.getExecutionTrace().getSymptomContext().getContextId();
      keymap.put("STOPID",String.valueOf(cid1));
    }
   
   String prompt = diad_control.getPrompt(typ.toString());
// prompt = IvyFile.expandName(prompt,keymap);
   prompt = DicontrolExpander.expand(prompt,keymap);
   
   String ask = diad_control.getQuery(typ.toString()); 
   ask = IvyFile.expandName(ask,keymap);
   
   if (ask == null && query != null) ask = query;
   else if (query != null) ask = ask + " " + query;
   
   CommandArgs args = new CommandArgs("USECONTEXT",true,
         "ID",getId(),
         "NOHISTORY",nohistory,
         "TOOLS",tools
   );
   
   IvyXmlWriter xw1 = new IvyXmlWriter();
   xw1.begin("PROMPT");
   xw1.field("REPLACE",true);
   xw1.cdata(prompt);
   xw1.end("PROMPT");
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
   xw.field("AUTO_QUERY",diad_control.getProperty("Diad.auto.query",false));
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
   if (query_response != null && !query_response.isEmpty()) {
      xw.cdataElement("RESPONSE",query_response);
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
               case INITIAL :
                  setState(DiadCandidateState.FINDING_SYMPTOM);
                  anal = diad_control.getAnalysisManager();
                  exec = diad_control.getExecuteManager();
                  break;
               case FINDING_SYMPTOM :  
                  findSymptom();
                  if (checkInterrupted()) break;
                  if (candidate_symptom != null) {
                     IvyLog.logD("DICONTROL","Candidate Symptom " + 
                           candidate_symptom.getSymptomType());
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
               case NO_LOCATIONS_FOUND :
               case FINDING_STARTING_FRAME :
                  findStartingFrame();
                  if (checkInterrupted()) break;
                  if (start_frame == null) {
                     setState(DiadCandidateState.NO_START_FRAME);
                   }
                  else {
                     setState(DiadCandidateState.DOING_BASE_EXECUTION);
                   }
                  break;
               case NO_START_FRAME :
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
                  if (base_execution == null) {
                     exec_locations = location_set;
                   }
                  else {
                     exec_locations = base_execution.getExecutedLocations(location_set); 
                   }
                  if (exec_locations == null) {
                     setState(DiadCandidateState.NO_FINAL_LOCATIONS);   
                   }
                  else {
                     setState(DiadCandidateState.PREPARING_DATA);
                   } 
                  // restrict location set by base execution
                  break;
               case NO_FINAL_LOCATIONS :
               case PREPARING_DATA :
                  if (diad_control.getProperty("Diad.auto.query",false)) {
                     setState(DiadCandidateState.DOING_QUERY);
                   }
                  else {
                     setState(DiadCandidateState.READY);
                   }
                  break;
               case DOING_QUERY : 
                  if (checkInterrupted()) break;
                  runQuery();
                  if (checkInterrupted()) break;
                  // nandle null response here?
                  setState(DiadCandidateState.READY);
                  break;
               case NO_SYMPTOM_FOUND :
               case READY : 
                  waitForInterrupt(); 
                  return;
               case DEAD :
               case INTERRUPTED : 
                  cleanup(true);
                  return;
               case NO_USER_STACK :
               case NO_ANALYSIS :
               case NO_BASE_EXECUTION :
//             case NO_START_FRAME :
//             case NO_FINAL_LOCATIONS :
               default :
                  cleanup(false);
                  // need to remove base execution from seede
                  return;
             }
          }
         catch (InterruptedException e) {
            IvyLog.logD("DICONTROL","Interrupted exception");
            if (isInterrupted()) {
               return;
             }
          }
         catch (Throwable e) {
            IvyLog.logE("DICONTROL","Problem processing candidate",e);
            if (isInterrupted()) {
               return;
             }
            setState(DiadCandidateState.DEAD);
            return;
          }
       }
    }
   
   private void findSymptom() {
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
      if (checkInterrupted()) return;
      for_frame = stk.getUserFrame();
      if (for_frame == null) {
         setState(DiadCandidateState.NO_USER_STACK);
         return;
       }
      if (checkInterrupted()) return;
      if (user_symptom == null) {
         DicontrolSymptomFinder finder =
            new DicontrolSymptomFinder(diad_control,for_thread,
                  stk,for_frame);
         candidate_symptom = finder.findSymptom();
       }
      else {
         candidate_symptom = user_symptom;
       }
    }
   
   private void findStartingFrame() {
      start_frame = null;
      DiexecuteManager exec = diad_control.getExecuteManager();
      if (user_frame == null) {
         start_frame = exec.getStartingFrame(candidate_symptom,
               for_thread,location_set);
       }
      else {
         start_frame = user_frame;
       }
    }
   
   private void runQuery() throws Exception {
      query_response = null;
      String cmd = QUERY_COMMAND.replace("$ID",candidate_id);
      IvyLog.logD("DICONTROL","Run query " + cmd);
      Element xml = IvyXml.convertStringToXml(cmd);
      DicontrolCommand qcmd = DicontrolCommand.createCommand(diad_control,xml);
      IvyXmlWriter xw = new IvyXmlWriter();
      xw.begin("RESULT");
      qcmd.process(xw);
      xw.end("RESULT");
      try {
         IvyLog.logD("DICONTROL","Query returned " + xw.toString());
         Element resp = IvyXml.convertStringToXml(xw.toString());
         query_response = IvyXml.getTextElement(resp,"RESPONSE");
         IvyLog.logD("DICONTROL","Query text " + query_response);
       }
      catch (Exception e) {
         IvyLog.logE("DICONTROL","Problem parsing response",e);
         IvyLog.logD("DICONTROL","Response: " + xw.toString());
         query_response = "Bad response from LLM";
       }
   }
   
   private boolean checkInterrupted() {
      if (isInterrupted()) {
         IvyLog.logD("DICONTROL","Candidate interrupted");
         setState(DiadCandidateState.INTERRUPTED); 
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
   
   
   private void waitForInterrupt() {
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
    }
   
}       // end of inner class CandidateThread





}       // end of class DicontrolCandidate




/* end of DicontrolCandidate.java */

