/********************************************************************************/
/*                                                                              */
/*              DiexecuteManager.java                                           */
/*                                                                              */
/*      Manager for execution access through SEEDE                              */
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadExecution;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

public class DiexecuteManager implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;;
private Map<String,DiexecuteExecution> exec_map;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DiexecuteManager(DicontrolMain ctrl)
{
   diad_control = ctrl;
   exec_map = new HashMap<>();
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DicontrolMain getDiadControl()                  { return diad_control; }


/********************************************************************************/
/*                                                                              */
/*      Find the starting frame for execution                                   */
/*                                                                              */
/********************************************************************************/

public DiadStackFrame getStartingFrame(DiadSymptom symp,DiadThread thrd,
      Collection<DiadLocation> faults)
{
   DiexecuteStartFinder fndr = new DiexecuteStartFinder(this,
         thrd,faults);  
   
   return fndr.findStartingFrame(); 
}



/********************************************************************************/
/*                                                                              */
/*      Create the base execution                                               */
/*                                                                              */
/********************************************************************************/

public DiadExecution createBaseExecution(DiadSymptom symp,DiadThread thrd,DiadStackFrame start)
{
   DiexecuteBaseExecution fndr = new DiexecuteBaseExecution(this,
         symp,thrd,start);
   DiadTrace trc = fndr.createBaseExecution();
   if (trc == null) return null;
  
   return fndr;
}



/********************************************************************************/
/*                                                                              */
/*      Handle seede messages                                                   */
/*                                                                              */
/********************************************************************************/

Element sendSeedeMessage(String sid,String cmd,CommandArgs args,String cnts)
{
   return diad_control.sendSeedeMessage(sid,cmd,args,cnts);
}

void register(DiexecuteExecution ve)
{
   exec_map.put(ve.getSessionId(),ve);
}


void unregister(String ssid)
{
   exec_map.remove(ssid);
}


public String handleSeedeMessage(String typ,String id,Element xml)
{
   String rslt = null;
   
   DiexecuteExecution ve = exec_map.get(id);
   if (ve != null) {
      switch (typ) {
         case "EXEC" :
            ve.handleResult(xml);
            IvyLog.logD("DIEXECUTE","Set up SEEDE Result");
            break;
         case "RESET" :
            ve.handleReset();
            break;
         case "INPUT" :
            rslt = ve.handleInput(IvyXml.getAttrString(xml,"FILE"));
            break;
         case "INITIALVALUE" :
            rslt = ve.handleInitialValue(IvyXml.getAttrString(xml,"WHAT"));
            break;
         default :
            IvyLog.logE("DIEXECUTE","Unknown seede command " + typ);
            break;
       }
    }
   else  {
      IvyLog.logI("DIEXECUTE","Seede message without handler");
    }
   
   return rslt;
}


}       // end of class DiexecuteManager




/* end of DiexecuteManager.java */

