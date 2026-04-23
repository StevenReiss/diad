/********************************************************************************/
/*                                                                              */
/*              DicontrolCommand.java                                           */
/*                                                                              */
/*      Implementation of the variuos commands                                                 */
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

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadRepair;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadConstants.DiadCommand;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

abstract class DicontrolCommand implements DicontrolConstants, DiadCommand
{



/********************************************************************************/
/*                                                                              */
/*      Static creation methods                                                 */
/*                                                                              */
/********************************************************************************/

static DicontrolCommand createCommand(DicontrolMain ctrl,Element xml)
{
   String cmd = IvyXml.getAttrString(xml,"DO");
   if (cmd == null) cmd = "NO_COMMAND";
   cmd = cmd.toUpperCase();
   
   switch (cmd) {
      case "PING" : 
         return new CommandPing(ctrl,xml);
      case "SETUPBUBBLES" :
         return new CommandSetupBubbles(ctrl,xml);
      case "TRANSCRIPT" :
         return new CommandTranscript(ctrl,xml);
      case "CLEARHISTORY" :
         return new CommandClearHistory(ctrl,xml);
      case "TEST" :
         return new CommandTest(ctrl,xml);
      case "DELAY" :
         return new CommandDelay(ctrl,xml);
      case "EXIT" :
         return new CommandExit(ctrl,xml);
      case "SETMODEL" :
         return new CommandSetModel(ctrl,xml);
      case "WAITFORSTATE" :
         return new WaitForState(ctrl,xml);
      case "Q_STACK" :
         return new QueryStack(ctrl,xml);
      case "Q_EVAL" :
          return new QueryEval(ctrl,xml);
      case "Q_LOCATIONS" :
         return new QueryLocations(ctrl,xml);
      case "Q_EXECTRACE" :
         return new QueryExecTrace(ctrl,xml);
      case "Q_LINETRACE" :
         return new QueryLineTrace(ctrl,xml);
      case "Q_VARTRACE" :
         return new QueryVarTrace(ctrl,xml);
      case "Q_VARHISTORY" :
         return new QueryVarHistory(ctrl,xml);
      case "Q_VARVALUE" :
         return new QueryVarValue(ctrl,xml);
      case "Q_METHODCALLS" :
         return new QueryMethodCalls(ctrl,xml);
      case "EXPRESSIONS" :
         return new CommandExpressions(ctrl,xml);
      case "ASKLIMBA" :         
         return new CommandAskLimba(ctrl,xml);
      case "PARAMETER" :
         return new CommandParameter(ctrl,xml);
      case "SYMPTOM" :
         return new CommandSymptom(ctrl,xml);
      case "STARTFRAME" :
         return new CommandStartFrame(ctrl,xml);
      case "VALIDATE" :
         return new CommandValidate(ctrl,xml);
      default :
         IvyLog.logE("DICONTROL","Unknown command " + cmd + " " +
               IvyXml.convertXmlToString(xml));
         return null;
    }
   
         
}

/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

protected DicontrolMain diad_control;
protected String       command_name;
protected String        reply_id;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

protected DicontrolCommand(DicontrolMain ctrl,Element xml) 
{
   diad_control = ctrl;
   command_name = IvyXml.getAttrString(xml,"DO");
   reply_id = IvyXml.getAttrString(xml,"RID");
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String getCommandName()        { return command_name; }

@Override public boolean isImmediate()          { return reply_id == null; }

@Override public abstract void process(IvyXmlWriter xw) throws Exception;
 

/********************************************************************************/
/*                                                                              */
/*      Helper methods                                                          */
/*                                                                              */
/********************************************************************************/

protected boolean isInteger(String v)
{
   if (v == null || v.isEmpty()) return false;
   
   return v.matches("[0-9]+");
}



/********************************************************************************/
/*                                                                              */
/*      Ping command                                                            */
/*                                                                              */
/********************************************************************************/

private static class CommandPing extends DicontrolCommand {  
   
   CommandPing(DicontrolMain ctrl,Element xml) { 
      super(ctrl,xml);
    }
   
   @Override public void process(IvyXmlWriter xw)  {
      xw.text("PONG");
    }

}       // end of inner class CommandPing


/********************************************************************************/
/*                                                                              */
/*      Setup Bubbles command for debugging                                     */
/*                                                                              */
/********************************************************************************/

private static class CommandSetupBubbles extends DicontrolCommand {
   
   private String workspace_name;
   private String mint_name;
   
   CommandSetupBubbles(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      workspace_name = IvyXml.getTextElement(xml,"WORKSPACE");
      mint_name = IvyXml.getAttrString(xml,"MINT");
    }
   
   @Override public void process(IvyXmlWriter xw) { 
      diad_control.getTestManager().setupBedrock(workspace_name,mint_name); 
      
      CommandArgs args = new CommandArgs("MESSAGE",
            "Start workspace " + workspace_name);
      diad_control.sendLimbaMessage("MESSAGE",args,null);
    }

}       // end of inner class CommandSetupBubbles


/********************************************************************************/
/*                                                                              */
/*      Command to run a test case                                              */
/*                                                                              */
/********************************************************************************/

private static class CommandTest extends DicontrolCommand {

   private String project_name;
   private String launch_name;
   private int  continue_count;
   
   CommandTest(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      project_name = IvyXml.getAttrString(xml,"PROJECT");
      launch_name = IvyXml.getAttrString(xml,"LAUNCH");
      continue_count = IvyXml.getAttrInt(xml,"CONTINUE",0);
    }
   
   @Override public void process(IvyXmlWriter xw) {
      diad_control.getTestManager().setupTest(project_name,launch_name,
            continue_count);
      
      CommandArgs args = new CommandArgs("MESSAGE","Start test " +
            project_name + "::" + launch_name);
      diad_control.sendLimbaMessage("MESSAGE",args,null);
    }
   
   
}       // end of inner class CommandTest



/********************************************************************************/
/*                                                                              */
/*      Delay command for use when testing                                      */
/*                                                                              */
/********************************************************************************/

private static class CommandDelay extends DicontrolCommand {

   private long delay_time;
   
   CommandDelay(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      delay_time = IvyXml.getAttrLong(xml,"TIME");
      if (delay_time <= 0) delay_time = 1;
    }
   
   @Override public void process(IvyXmlWriter xw) {
      try {
         Thread.sleep(delay_time);
       }
      catch (InterruptedException e) { }
    }
   
}       // end of inner class CommandDelay



/********************************************************************************/
/*                                                                              */
/*      Wait for state command                                                  */
/*                                                                              */
/********************************************************************************/

private static class WaitForState extends DicontrolCommand {

   private DiadCandidateState target_state;
   
   WaitForState(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      target_state = IvyXml.getAttrEnum(xml,"STATE",DiadCandidateState.FINDING_SYMPTOM);
    }
   
   @Override public void process(IvyXmlWriter xw) {
      while (true) {
         for (DicontrolCandidate cand : diad_control.getActiveCandidates()) { 
            if (cand.getState() == target_state) return;
            switch (cand.getState()) {
               case NO_ANALYSIS :
               case NO_BASE_EXECUTION :
               case NO_LOCATIONS_FOUND :
               case NO_USER_STACK :
               case NO_START_FRAME :
               case NO_SYMPTOM_FOUND :
               case NO_FINAL_LOCATIONS :
               case DEAD :
               case INTERRUPTED :
               case READY : 
                  return;
             }
            try {
               Thread.sleep(1000);
             }
            catch (InterruptedException e) { }
          }
       }
    }
   
}       // end of inner class WaitForState



/********************************************************************************/
/*                                                                              */
/*      Query Commands                                                          */
/*                                                                              */
/********************************************************************************/

private abstract static class QueryCommand extends DicontrolCommand {
   
   private DicontrolCandidate debug_candidate;
   
   protected QueryCommand(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      
      debug_candidate = null;
      String id = IvyXml.getAttrString(xml,"DEBUGID");
      for (DicontrolCandidate cand : diad_control.getActiveCandidates()) {
         if (cand.getId().equals(id)) {
            debug_candidate = cand;
            break;
          }
       }
    }
   
   protected DicontrolCandidate getCandidate() {
      return debug_candidate;
    }
   
   @Override public void process(IvyXmlWriter xw) {
      if (debug_candidate == null) return;
      
      long start = System.currentTimeMillis();
      
      JSONObject jo = getJsonObject();
      JSONArray ja = null;
      if (jo == null) {
         ja = getJsonArray();
       }
      if (jo != null) {
         xw.begin("JSON");
         xw.field("TYPE","OBJECT");
         xw.cdata(jo.toString(2));
         xw.end("JSON");
       }
      else if (ja != null) {
         xw.begin("JSON");
         xw.field("TYPE","ARRAY");
         xw.cdata(ja.toString(2));
         xw.end("JSON");
       }
      else {
         localProcess(xw);
       }
      
      long time = System.currentTimeMillis() - start;
      IvyLog.logI("DICONTROL","Command " + getCommandName() + " TIME = " + time);
    }
   
   protected JSONObject getJsonObject()                 { return null; }
   protected JSONArray getJsonArray()                   { return null; }
   protected void localProcess(IvyXmlWriter xw)         { }
   
}       // end of inner class QueryCommand


private static class QueryStack extends QueryCommand {
   
   QueryStack(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
    }
   
   @Override protected JSONArray getJsonArray() {
      return getCandidate().getJsonStack(); 
    }
   
   
}       // end of inner class QueryStack


private static class QueryEval extends QueryCommand {
    
    private String frame_id;
    private String eval_expr;
    private int output_level;
    
    QueryEval(DicontrolMain ctrl,Element xml) {
        super(ctrl,xml);
        frame_id = IvyXml.getAttrString(xml,"FRAMEID");
        eval_expr = IvyXml.getTextElement(xml,"EXPRESSION");
        output_level = IvyXml.getAttrInt(xml,"LEVELS",3);
     }
    
    @Override protected JSONObject getJsonObject() {
        return getCandidate().getEvaluate(frame_id, 
              eval_expr,output_level);  
     }
    
    
}       // end of inner class QueryStack




private static class QueryLocations extends QueryCommand {
   
   private boolean all_locations;
   
   QueryLocations(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      all_locations = IvyXml.getAttrBool(xml,"ALL");
    }
   
   @Override protected JSONArray getJsonArray() {
      return getCandidate().getJsonLocations(all_locations);  
    }
   

}       // end of inner class QueryLocations


private static class QueryExecTrace extends QueryCommand {
   
   private String call_id;
   
   QueryExecTrace(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      call_id = IvyXml.getAttrString(xml,"CALLID");
    }
   
   @Override protected JSONObject getJsonObject() {
      return getCandidate().getJsonLocalTrace(call_id); 
    }
   

}       // end of inner class QueryExecTrace



private static class QueryLineTrace extends QueryCommand {
   
   private String call_id;
   
   QueryLineTrace(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      call_id = IvyXml.getAttrString(xml,"CALLID");
    }
   
   @Override protected JSONArray getJsonArray() {
      return getCandidate().getJsonLineTrace(call_id);    
    }
   

}       // end of inner class QueryLineTrace


private static class QueryVarTrace extends QueryCommand {
   
   private String call_id;
   private String var_name;
   
   QueryVarTrace(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      call_id = IvyXml.getAttrString(xml,"CALLID");
      var_name = IvyXml.getAttrString(xml,"VARIABLE");
      if (!isInteger(call_id) && isInteger(var_name)) {
         String s = call_id;
         call_id = var_name;
         var_name = s;
       }
      else if (var_name == null && !isInteger(call_id)) {
         var_name = call_id;
         call_id = "0";
       }
    }
   
   @Override protected JSONObject getJsonObject() {
      return getCandidate().getJsonVarTrace(call_id,var_name);    
    }
   
}       // end of inner class QueryVarTrace


private static class QueryVarHistory extends QueryCommand {

   private String call_id;
   private String var_name;
   private int line_number;
   private long exec_time;
   
   QueryVarHistory(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      call_id = IvyXml.getAttrString(xml,"CALLID");
      var_name = IvyXml.getAttrString(xml,"VARIABLE");
      line_number = IvyXml.getAttrInt(xml,"LINE");
      exec_time = IvyXml.getAttrLong(xml,"WHEN");
      if (exec_time < 0) {
         exec_time = IvyXml.getAttrLong(xml,"TIME");
       }
    }
   
   @Override protected JSONObject getJsonObject() {
      return getCandidate().getJsonVarHistory(call_id,var_name,
            line_number,exec_time);    
    }

}       // end of inner class QueryVarHistory


private static class QueryVarValue extends QueryCommand {

   private String call_id;
   private String var_name;
   private int line_number;
   private long exec_time;
   
   QueryVarValue(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      call_id = IvyXml.getAttrString(xml,"CALLID");
      var_name = IvyXml.getAttrString(xml,"VARIABLE");
      line_number = IvyXml.getAttrInt(xml,"LINE");
      exec_time = IvyXml.getAttrLong(xml,"WHEN");
      if (exec_time < 0) {
         exec_time = IvyXml.getAttrLong(xml,"TIME");
       }
    }
   
   @Override protected JSONObject getJsonObject() {
      return getCandidate().getJsonVarValue(call_id,var_name,
            line_number,exec_time);    
    }
   
}       // end of inner class QueryVarValue


private static class QueryMethodCalls extends QueryCommand {

   private String method_name;
   
   QueryMethodCalls(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      method_name = IvyXml.getAttrString(xml,"METHOD");
    }
   
   @Override protected JSONArray getJsonArray() {
      return getCandidate().getJsonMethodCalls(method_name);
    }
   
}       // end of inner class QueryMethodCalls

/********************************************************************************/
/*                                                                              */
/*      Limba commands                                                          */
/*                                                                              */
/********************************************************************************/

private static class CommandAskLimba extends QueryCommand {

   private DiadAskType ask_type; 
   private String ask_text;
   private boolean no_history;
   
   CommandAskLimba(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      ask_type = IvyXml.getAttrEnum(xml,"TYPE",DiadAskType.GENERAL);
      ask_text = IvyXml.getTextElement(xml,"QUESTION");
      no_history = IvyXml.getAttrBool(xml,"NOHISTORY");
    }
   
   @Override public void process(IvyXmlWriter xw) {
      DicontrolCandidate cand = getCandidate();
      if (cand == null) {
         return;
       }
      
      Element rslt = cand.askLimba(xw,ask_type,ask_text,
            no_history); 
      String resp = IvyXml.getTextElement(rslt,"RESPONSE");
      if (resp != null) xw.cdataElement("RESPONSE",resp);
      for (Element c : IvyXml.children(rslt,"PATCH")) {
         String txt = IvyXml.getText(c);
         if (txt != null) {
            xw.cdataElement("PATCH",txt);
          }
       }
    }
   
   @Override public String getCommandName() {
      return "ASKLIMBA-" + ask_type;
    }
   
}       // end of inner class AskLimba



/********************************************************************************/
/*                                                                              */
/*      Parameter commands                                                      */
/*                                                                              */
/********************************************************************************/

private static class CommandParameter extends DicontrolCommand {
   
   private DicontrolCandidate debug_candidate;
   private Map<String,String> set_values;
   
   CommandParameter(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      debug_candidate = null;
      String id = IvyXml.getAttrString(xml,"DEBUGID");
      if (id != null && !id.isEmpty()) {
         for (DicontrolCandidate cand : diad_control.getActiveCandidates()) {
            if (cand.getId().equals(id)) {
               debug_candidate = cand;
               break;
             }
          }
       }
      set_values = new HashMap<>();
      for (Element set : IvyXml.children(xml,"SET")) {
         String name = IvyXml.getAttrString(set,"KEY");
         String val = IvyXml.getTextElement(set,"VALUE");
         if (val == null || val.isEmpty()) val = IvyXml.getText(set);
         if (name != null && val != null) {
            set_values.put(name,val);
          }
       }
    }
    
   @Override public void process(IvyXmlWriter xw) {
      for (Map.Entry<String,String> ent : set_values.entrySet()) {
         switch (ent.getKey()) {
            case "AUTO_QUERY" :
               diad_control.setProperty("Diad.auto.query",ent.getValue());
               break;
            case "FILEMODE" :
               DiadAnalysisFileMode mode = findFileMode(ent.getValue());
               if (debug_candidate == null && mode != null) {
                  diad_control.setProperty("Diad.file.mode",mode.toString());
                }
               else if (mode != null) {
                  debug_candidate.setFileMode(mode);
                }
               break;
            case "SEEDE_STEPS" :
               try {
                  int v = Integer.parseInt(ent.getValue());
                  diad_control.setProperty("Diad.max.seede.steps",String.valueOf(v));
                  if (debug_candidate != null) {
                     debug_candidate.start(DiadCandidateState.DOING_BASE_EXECUTION);
                   }
                }
               catch (NumberFormatException e) { }
               break;
            case "SEEDE_DEPTH" :
               try {
                  int v = Integer.parseInt(ent.getValue());
                  diad_control.setProperty("Diad.max.seede.depth",String.valueOf(v));
                  if (debug_candidate != null) {
                     debug_candidate.start(DiadCandidateState.DOING_BASE_EXECUTION);
                   }
                }
               catch (NumberFormatException e) { }
               break;
            default :
               if (ent.getKey().startsWith("Diad.")) {
                  diad_control.setProperty(ent.getKey(),ent.getValue());
                }
               else {
                  IvyLog.logE("DICONTROL","Unknown parameter " + ent.getKey());
                }
               break;
          }
       }
      xw.begin("PARAMETERS");
      DiadAnalysisFileMode mode = diad_control.getProperty(
            "Diad.file.mode",DiadAnalysisFileMode.FAIT_FILES);
      if (debug_candidate != null) mode = debug_candidate.getFileMode();
      xw.field("FILEMODE",mode);
      long mxtime = diad_control.getProperty("Diad.max.seede.steps",MAX_SEEDE_STEPS);
      int mxdepth = diad_control.getProperty("Diad.max.seede.depth",MAX_SEEDE_DEPTH); 
      xw.field("SEEDE_STEPS",mxtime);
      xw.field("SEEDE_DEPTH",mxdepth);
      xw.field("AUTO_QUERY",diad_control.getProperty("Diad.auto.query",false));
      xw.field("MODEL",diad_control.getProperty("Diad.ollama.model"));
      xw.end("PARAMETERS");
    }
   
   private DiadAnalysisFileMode findFileMode(String val) {
      try {
         return Enum.valueOf(DiadAnalysisFileMode.class,val);
       }
      catch (IllegalArgumentException e) { }
      
      return null;
    }
   
}       // end of inner calss CommandParameter



/********************************************************************************/
/*                                                                              */
/*      Symptom command                                                         */
/*                                                                              */
/********************************************************************************/

private static class CommandSymptom extends QueryCommand {
   
   private Element symptom_xml;
   
   CommandSymptom(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      symptom_xml = IvyXml.getChild(xml,"SYMPTOM");
    }
   
   @Override public void process(IvyXmlWriter xw) {
      DicontrolSymptom symp = null;
      if (symptom_xml != null) {
         symp = new DicontrolSymptom(symptom_xml);
         if (symp.getSymptomType() == DiadSymptomType.NONE) symp = null;
       }
      getCandidate().setSymptom(symp);
    }
   
}       // end of inner class CommandSymptom



/********************************************************************************/
/*                                                                              */
/*      Start frame command                                                     */
/*                                                                              */
/********************************************************************************/

private static class CommandStartFrame extends QueryCommand {
   
   private String frame_id;
   
   CommandStartFrame(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      frame_id = IvyXml.getAttrString(xml,"FRAME");
    }
   
   @Override public void process(IvyXmlWriter xw) {
      if (frame_id != null) {
         getCandidate().setStartFrame(frame_id); 
       }
      DiadThread thrd = getCandidate().getThread();
      if (thrd != null) {
         DiadStack stk = thrd.getStack();
         if (stk != null) {
            boolean use = false;
            xw.begin("FRAMES");
            for (DiadStackFrame frm : stk.getFrames()) {
               if (!use && frm.isUserFrame()) use = true;
               if (use) {
                  frm.outputXml(xw);
                }
             }
            xw.end("FRAMES");
          }
       }   
    }
   
}       // end of inner class CommandStartFrame



/********************************************************************************/
/*                                                                              */
/*      Set model command                                                       */
/*                                                                              */
/********************************************************************************/

private static class CommandSetModel extends QueryCommand {

   private String model_name;
   
   CommandSetModel(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      model_name = IvyXml.getAttrString(xml,"MODEL");
    }
   
   @Override public void process(IvyXmlWriter xw) {
      if (model_name != null) {
         CommandArgs args = new CommandArgs("MODEL",model_name);
         diad_control.setProperty("Diad.ollama.model",model_name);
         diad_control.sendLimbaMessage("SETMODEL",args,null);
       }
      Element mdls = diad_control.sendLimbaMessage("LIST",null,null);
      
      xw.begin("MODELS");
      for (Element mxml : IvyXml.children(mdls,"MODEL")) {
         String mdl = IvyXml.getText(mxml);
         xw.begin("MODEL");
         xw.field("NAME",mdl);
         if (mdl.equals(model_name)) xw.field("ACTIVE",true);
         xw.end("MODEL");
       }
      xw.end("MODELS");
    }

}       // end of inner class CommandSetModel



/********************************************************************************/
/*                                                                              */
/*      TRANSCRIPT command                                                      */
/*                                                                              */
/********************************************************************************/

private static class CommandTranscript extends QueryCommand {

   private String file_name;
   private boolean do_append;
   
   CommandTranscript(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      file_name = IvyXml.getAttrString(xml,"FILE");
      do_append = IvyXml.getAttrBool(xml,"APPEND");
    }
   
   @Override public void process(IvyXmlWriter xw) {
      CommandArgs args = new CommandArgs("FILE",file_name,
            "APPEND",do_append);
      diad_control.sendLimbaMessage("TRANSCRIPT",args,null);
    }

}       // end of inner class CommandTranscript



/********************************************************************************/
/*                                                                              */
/*      CLEARHISTORY command                                                    */
/*                                                                              */
/********************************************************************************/

private static class CommandClearHistory extends QueryCommand {
   
   CommandClearHistory(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
    }
   
   @Override public void process(IvyXmlWriter xw) {
      CommandArgs args = new CommandArgs("ID",getCandidate().getId());
      diad_control.sendLimbaMessage("CLEAR",args,null);
    }
   
}       // end of inner class CommandClearHistory



/********************************************************************************/
/*                                                                              */
/*      Command EXPRESSIONS -- find expressions at a line                       */
/*                                                                              */
/********************************************************************************/

private static class CommandExpressions extends QueryCommand {
   
   private String project_name;
   private String file_name;
   private int source_offset; 
   private int line_number;
   
   CommandExpressions(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      project_name = IvyXml.getAttrString(xml,"PROJECT");
      file_name = IvyXml.getAttrString(xml,"FILE");
      source_offset = IvyXml.getAttrInt(xml,"OFFSET");
      line_number = IvyXml.getAttrInt(xml,"LINE");
    }
   
   @Override public void process(IvyXmlWriter xw) {
      DisourceManager srcmgr = diad_control.getSourceManager();
      srcmgr.getExpressionsInStatement(xw,project_name,file_name,
            source_offset,line_number);
    }
   
}       // end of inner class CommandExpressions


/********************************************************************************/
/*                                                                              */
/*      Validate command                                                        */
/*                                                                              */
/********************************************************************************/

private static class CommandValidate extends QueryCommand {
   
   private DiadRepair for_repair;
   
   CommandValidate(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      for_repair = new DicontrolRepair(IvyXml.getChild(xml,"EDITS")); 
    }
   
   @Override public void process(IvyXmlWriter xw) {
      getCandidate().validate(xw,for_repair);  
    }
}

/********************************************************************************/
/*                                                                              */
/*      Exit command                                                            */
/*                                                                              */
/********************************************************************************/

private static class CommandExit extends DicontrolCommand {  

   CommandExit(DicontrolMain ctrl,Element xml) { 
      super(ctrl,xml);
    }
   
   @Override public void process(IvyXmlWriter xw)  {
      System.exit(0);
    }

}       // end of inner class CommandPing





}       // end of class DicontrolCommand




/* end of DicontrolCommand.java */

