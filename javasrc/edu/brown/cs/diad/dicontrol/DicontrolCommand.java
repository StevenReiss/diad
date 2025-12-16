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

DicontrolCommand createCommand(DicontrolMain ctrl,Element xml)
{
   String cmd = IvyXml.getAttrString(xml,"DO");
   cmd = cmd.toUpperCase();
   
   switch (cmd) {
      case "PING" : 
         return new CommandPing(ctrl,xml);
      case "SETUPBUBBLES" :
          return new CommandSetupBubbles(ctrl,xml);
      case "EXIT" :
          return new CommandExit(ctrl,xml);
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
   reply_id = IvyXml.getAttrString(xml,"ID");
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
      diad_control.setupBedrock(workspace_name,mint_name); 
    }

}       // end of inner class CommandSetupBubbles


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

