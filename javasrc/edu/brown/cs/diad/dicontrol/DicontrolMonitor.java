/********************************************************************************/
/*										*/
/*		DicontrolMonitor.java						*/
/*										*/
/*	Message server interface for DIAD					*/
/*										*/
/********************************************************************************/
/*	Copyright 2025 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.diad.dicontrol;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintArguments;
import edu.brown.cs.ivy.mint.MintConstants;
import edu.brown.cs.ivy.mint.MintControl;
import edu.brown.cs.ivy.mint.MintDefaultReply;
import edu.brown.cs.ivy.mint.MintHandler;
import edu.brown.cs.ivy.mint.MintMessage;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.mint.MintConstants.MintSyncMode;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DicontrolMonitor implements DicontrolConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private DicontrolMain	diad_control;
private MintControl	mint_control;
private Map<String,EvalData> eval_handlers;

private static Random   random_gen = new Random();


 
/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

DicontrolMonitor(DicontrolMain gm,String mintid)
{
   diad_control = gm;
   eval_handlers = new HashMap<>();
   
   mint_control = MintControl.create(mintid,MintSyncMode.ONLY_REPLIES);
   mint_control.register("<BUBBLES DO='EXIT' />",new ExitHandler());
   mint_control.register("<DIAD DO='_VAR_0' />",new CommandHandler());
   mint_control.register("<BEDROCK TYPE='_VAR_0' />",new IDEHandler());
   
   IvyLog.logD("DICONTROL","Listening for messages on " + mintid);
}


/********************************************************************************/
/*                                                                              */
/*      Command handler                                                         */
/*                                                                              */
/********************************************************************************/

private String processCommand(String cmd,Element xml) throws DiadException
{
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      xw.begin("RESULT");
      DiadCommand dcmd = diad_control.setupDiadCommand(xml);
      if (dcmd.isImmediate()) { 
         dcmd.process(xw);
       }
      else { 
         String rid = IvyXml.getAttrString(xml,"RID");
         if (rid == null) {
            rid = "DIAD_" + random_gen.nextInt(1000000);
          }
         CommandProcessor cp = new CommandProcessor(dcmd,rid);
         xw.field("RID",rid);
         cp.start();
       }
      
      xw.end("RESULT");
      
      return xw.toString();
    }
}




private final class CommandHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      IvyLog.logD("DICONTROL","PROCESS MSG COMMAND " + msg.getText());
      String cmd = args.getArgument(0);
      Element xml = msg.getXml();
      String rslt = null;
      try {
         rslt = processCommand(cmd,xml);
       }
      catch (DiadException e) {
         String xmsg = "DIAD error in command " + cmd + ": " + e;
         IvyLog.logE("DIAD",xmsg,e);
         IvyXmlWriter xw = new IvyXmlWriter();
         xw.cdataElement("ERROR",xmsg);
         rslt = xw.toString();
         xw.close();
       }
      catch (Throwable t) {
         IvyLog.logE("DIAD","Problem processing MSG command " + cmd,t);
         IvyXmlWriter xw = new IvyXmlWriter();
         String xmsg = "Problem processing command " + cmd + ":" + t;
         xw.begin("ERROR");
         xw.textElement("MESSAGE",xmsg);
         xw.end("ERROR");
         rslt = xw.toString();
         xw.close();
       }
      IvyLog.logD("DIAD","Reply for " + cmd + ": " + rslt);
      msg.replyTo(rslt);
    }
   
}       // end of inner class CommandHandler


/********************************************************************************/
/*                                                                              */
/*      Background command processor                                            */
/*                                                                              */
/********************************************************************************/

private class CommandProcessor extends Thread {

   private DiadCommand for_command;
   private String reply_id;
   
   CommandProcessor(DiadCommand cmd,String rid) {
      super("DICONTROL_" + cmd.getCommandName() + "_" + rid);
      for_command = cmd;
      reply_id = rid;
    }
   
   @Override public void run() {
      try (IvyXmlWriter xw = new IvyXmlWriter()) {
         xw.begin("DIADREPLY");
         xw.field("RID",reply_id);
         xw.begin("RESULT");
         for_command.process(xw);
         xw.end("RESULT");
         xw.end("DIADREPLY");
         IvyLog.logD("DIAD","Send reply " + xw.toString());
         mint_control.send(xw.toString());
       }
      catch (Throwable t) {
         IvyXmlWriter xw = new IvyXmlWriter();
         xw.begin("DIADREPLY");
         xw.field("RID",reply_id);
         xw.begin("ERROR");
         xw.textElement("MESSAGE",t);
         xw.end("ERROR");
         xw.end("DIADREPLY");
         IvyLog.logD("DIAD","Send error reply " + xw.toString());
         mint_control.send(xw.toString());
         xw.close();
       }
    }

}       // end of inner class CommandProcessor


/********************************************************************************/
/*										*/
/*	Send messages to front end						*/
/*										*/
/********************************************************************************/

boolean sendPing()
{
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      xw.begin("DIADREPLY");
      xw.field("DO","PING");
      xw.end("DIADREPLY");
      MintDefaultReply mdr = new MintDefaultReply();
      mint_control.send(xw.toString(),mdr,
	    MintConstants.MINT_MSG_FIRST_NON_NULL);
      String s = mdr.waitForString();
      if (s != null) return true;
    }
   return false;
}


/********************************************************************************/
/*										*/
/*	Send messages to Eclipse						*/
/*										*/
/********************************************************************************/

void pongEclipse()
{
   mint_control.register("<BEDROCK TYPE='PING' />",new BubblesPingHandler());
}


boolean pingEclipse()
{
   MintDefaultReply rply = new MintDefaultReply();
   String msg = "<BUBBLES DO='PING' />";
   IvyLog.logD("DICONTROL","Send to bubbles: " + msg);
   mint_control.send(msg,rply,MintControl.MINT_MSG_FIRST_NON_NULL);

   String r = rply.waitForString(500);

   return r != null;
}


Element sendBubblesMessage(String cmd,CommandArgs args,String cnts)
{
   MintDefaultReply rply = new MintDefaultReply();
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("BUBBLES");
   xw.field("DO",cmd);
   if (args != null) {
      for (Map.Entry<String,Object> ent : args.entrySet()) {
	 xw.field(ent.getKey(),ent.getValue());
       }
    }
   if (cnts != null) {
      xw.xmlText(cnts);
    }
   xw.end("BUBBLES");
   
   String msg = xw.toString();
   xw.close();
   
   IvyLog.logD("DICONTROL","Send to bubbles: " + msg);

   mint_control.send(msg,rply,MintControl.MINT_MSG_FIRST_NON_NULL);
   
   Element rslt = rply.waitForXml(60000);
   
   IvyLog.logD("DICONTROL","Reply from bubbles: " + IvyXml.convertXmlToString(rslt));
   
   return rslt;
}


private final class ExitHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      System.exit(0);
    }

}	// end of inner class ExitHandler


private final class BubblesPingHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      msg.replyTo("<PONG/>");
    }

}	// end of inner class ExitHandler



/********************************************************************************/
/*                                                                              */
/*      Handle messages from the back end                                       */
/*                                                                              */
/********************************************************************************/

protected class IDEHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      String cmd = args.getArgument(0);
      Element e = msg.getXml();
      
      switch (cmd) {
         case "ELISION" :
            return;
       }
      
      IvyLog.logD("DICONTROL","Message from IDE " + cmd + " " + msg.getText());
      
      try {
         switch (cmd) {
            case "EDITERROR" :
            case "FILEERROR" :
            case "FILEERRORS" :
            case "PRIVATEERROR" :
            case "AUTOBUILDDONE" :
            case "LAUNCHCONFIGEVENT" :
            case "NAMES" :
            case "ENDNAMES" :
            case "PROGRESS" :
            case "BUILDDONE" :
            case "FILECHANGE" :
            case "PROJECTDATA" :
            case "PROJECTOPEN" :
               break;
            case "EDIT" :
               msg.replyTo();
               File f = new File(IvyXml.getAttrString(e,"FILE"));
               diad_control.getRunManager().noteFileEdited(f);  
               break;
            case "RESOURCE" :
               for (Element re : IvyXml.children(e,"DELTA")) {
                  diad_control.getRunManager().handleResourceChange(re);
                }
               break;
            case "CONSOLE" :
            case "BREAKEVENT" :
               msg.replyTo();
               break;
            case "RUNEVENT" :
               for (Element re : IvyXml.children(e,"RUNEVENT")) {
                  IvyLog.logD("DICONTROL","Handle run event " + re);
                  diad_control.getRunManager().handleRunEvent(re);  
                }
               break;
            case "PING" :
            case "PING1" :
            case "PING2" :
            case "PING3" :
            case "PING4" :
            case "PING5" :
               msg.replyTo("<PONG/>");
               break;
            case "EVALUATION" :
               String eid = IvyXml.getAttrString(e,"ID");
               if (eid != null && eid.startsWith("DIAD")) {
                  EvalData ed = new EvalData(e);
                  synchronized (eval_handlers) {
                     eval_handlers.put(eid,ed);
                     eval_handlers.notifyAll();
                   }
                }
               msg.replyTo();
               break;
            case "STOP" :
               IvyLog.logI("DICONTROL","STOP received from eclipse");
               System.exit(1);
               break;
            default :
               break;
          }
       }
      catch (Throwable t) {
         IvyLog.logE("DICONTROL","Unknown eclipse message " + cmd,t);
         msg.replyTo();
       }
    }

}	// end of inner class IDEHandler



/********************************************************************************/
/*                                                                              */
/*      Handle evaluations                                                      */
/*                                                                              */
/********************************************************************************/

public Element waitForEvaluation(String id)
{
   synchronized (eval_handlers) {
      for ( ; ; ) {
	 EvalData ed = eval_handlers.remove(id);
	 if (ed != null) {
	    return ed.getResult();
	  }
	 try {
	    eval_handlers.wait(5000);
	  }
	 catch (InterruptedException e) { }
       }
    }
}



private static class EvalData {

   private Element eval_result;
   
   EvalData(Element rslt) {
      eval_result = rslt;
    }
   
   Element getResult() {
      return eval_result;
    }

}	// end of inner class EvalData

}	// end of class DicontrolMonitor




/* end of DicontrolMonitor.java */

