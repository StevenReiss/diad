/********************************************************************************/
/*                                                                              */
/*              DiexecuteExecution.java                                         */
/*                                                                              */
/*      Representation of a SEEDE execution                                     */
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

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadRepair;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteExecution implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/


enum ExecState { INITIAL, PENDING, READY, INTERRUPT };

private String          session_id;
private DiexecuteTrace  seede_result;
private ExecState       exec_state;
private DiexecuteBaseExecution for_context;
private DiadRepair      for_repair;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteExecution(String sid,DiexecuteBaseExecution ctx,DiadRepair repair)
{
   session_id = sid;
   for_context = ctx;
   seede_result = null;
   exec_state = ExecState.INITIAL;
   for_repair = repair;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

String getSessionId()                          { return session_id; }

long getExecutionTime()
{
   if (exec_state != ExecState.READY || seede_result == null) return 0;
   return seede_result.getExecutionTime();
}

DiexecuteBaseExecution getContext()             { return for_context; }

DiadRepair getRepair()                          { return for_repair; }



/********************************************************************************/
/*                                                                              */
/*      Start method                                                            */
/*                                                                              */
/********************************************************************************/

void start(DiexecuteManager vfac)
{
   synchronized (this) {
      seede_result = null;
      exec_state = ExecState.PENDING;
    }
   
   IvyLog.logD("DIEXECUTE","Start SEEDE execution " + getSessionId());
   
   vfac.register(this); 
   
   DicontrolMain diad = vfac.getDiadControl();
   long mxtime = diad.getProperty("Diad.max.seede.steps",MAX_SEEDE_STEPS);
   int mxdepth = diad.getProperty("Diad.max.seede.depth",MAX_SEEDE_DEPTH); 
   CommandArgs args = new CommandArgs("EXECID",session_id,
         "CONTINUOUS",false,"MAXTIME",mxtime,"MAXDEPTH",mxdepth);
   Element r1 = diad.sendSeedeMessage(session_id,"EXEC",args,null);
   if (!IvyXml.isElement(r1,"RESULT")) {
      IvyLog.logD("DIEXECUTE","Exec setup returned: " +
            IvyXml.convertXmlToString(r1));  
      exec_state = ExecState.READY;
      return;
    }
}



/********************************************************************************/
/*                                                                              */
/*      Update methods                                                          */
/*                                                                              */
/********************************************************************************/

synchronized void handleResult(Element xml)
{
   seede_result = new DiexecuteTrace(this,
         xml,for_context.getThread());  
   exec_state = ExecState.READY;
   notifyAll();
}


synchronized void handleReset()
{
   seede_result = null;
   exec_state = ExecState.PENDING;
}


synchronized String handleInput(String file)
{
   return null; 
}


synchronized String handleInitialValue(String what)
{
   return null;
}



/********************************************************************************/
/*                                                                              */
/*      Get result                                                              */
/*                                                                              */
/********************************************************************************/

DiexecuteTrace getSeedeResult()
{
   synchronized (this) {
      while (exec_state != ExecState.READY && exec_state != ExecState.INTERRUPT) {
         try {
            wait(3000);
          }
         catch (InterruptedException e) { 
            exec_state = ExecState.INTERRUPT;
            return null;
          }
       }
      IvyLog.logD("DIEXECUTE","Return SEEDE result " + exec_state);
      return seede_result;
    }
}



}       // end of class DiexecuteExecution




/* end of DiexecuteExecution.java */

