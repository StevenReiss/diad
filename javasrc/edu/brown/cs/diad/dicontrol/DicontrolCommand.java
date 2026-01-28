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

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadConstants.DiadCommand;
import edu.brown.cs.ivy.file.IvyLog;
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
   cmd = cmd.toUpperCase();
   
   switch (cmd) {
      case "PING" : 
         return new CommandPing(ctrl,xml);
      case "SETUPBUBBLES" :
         return new CommandSetupBubbles(ctrl,xml);
      case "TEST" :
         return new CommandTest(ctrl,xml);
      case "DELAY" :
         return new CommandDelay(ctrl,xml);
      case "EXIT" :
         return new CommandExit(ctrl,xml);
      case "WAITFORSTATE" :
         return new WaitForState(ctrl,xml);
      case "Q_STACK" :
         return new QueryStack(ctrl,xml);
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
      case "ASKLIMBA" :
         return new CommandAskLimba(ctrl,xml);
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
               case INTERUPTED :
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
      if (call_id != null && !call_id.isEmpty()) {
         return getCandidate().getJsonLocalTrace(call_id); 
       }
      else {
         return getCandidate().getJsonExecTrace();  
       }
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



/********************************************************************************/
/*                                                                              */
/*      Limba commands                                                          */
/*                                                                              */
/********************************************************************************/

private static class CommandAskLimba extends QueryCommand {

   private DiadAskType ask_type; 
   private String ask_text;
   
   CommandAskLimba(DicontrolMain ctrl,Element xml) {
      super(ctrl,xml);
      ask_type = IvyXml.getAttrEnum(xml,"TYPE",DiadAskType.GENERAL);
      ask_text = IvyXml.getTextElement(xml,"QUESTION");
    }
   
   @Override public void process(IvyXmlWriter xw) {
      Element rslt = getCandidate().askLimba(xw,ask_type,ask_text); 
      String resp = IvyXml.getTextElement(rslt,"RESPONSE");
      if (resp != null) xw.cdataElement("RESPONSE",resp);
    }
   
}       // end of inner class AskLimba


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

