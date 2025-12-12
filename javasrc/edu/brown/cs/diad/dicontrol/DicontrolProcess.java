/********************************************************************************/
/*                                                                              */
/*              DicontrolProcess.java                                           */
/*                                                                              */
/*      Represents a user debug process                                         */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;

class DicontrolProcess implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Map<String,DicontrolThread> thread_map; 
private String process_id;
private boolean is_running;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolProcess(Element xml) 
{ 
   thread_map = new ConcurrentHashMap<>();
   process_id = IvyXml.getAttrString(xml,"PID");
   is_running = true;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

Iterable<DicontrolThread> getThreads()
{
   List<DicontrolThread> rslt = new ArrayList<>();
   for (DicontrolThread dt : thread_map.values()) {
      if (dt.getProcess() == this && !dt.isInternal() && !dt.isTerminated()) {
         rslt.add(dt);
       }
    }
   
   return rslt;
}


String getId()                          { return process_id; }

boolean isRunning()                     { return is_running; }



/********************************************************************************/
/*                                                                              */
/*      Update methods                                                          */
/*                                                                              */
/********************************************************************************/

void update(Element xml)
{
   if (is_running && IvyXml.getAttrBool(xml,"TERMINATED")) {
      is_running = false;
    }
}


/********************************************************************************/
/*                                                                              */
/*      Thread event processing                                                 */
/*                                                                              */
/********************************************************************************/

void updateThread(Element xml)
{
   RunEventKind kind = IvyXml.getAttrEnum(xml,"KIND",RunEventKind.NONE);
   RunThreadStateDetail dtl = IvyXml.getAttrEnum(xml,"DETAIL",RunThreadStateDetail.NONE);
   boolean iseval = IvyXml.getAttrBool(xml,"EVAL");
   
   Element thrdxml = IvyXml.getChild(xml,"THREAD");
   if (thrdxml == null) return;
   String id = IvyXml.getAttrString(thrdxml,"ID");
   DicontrolThread thrd = thread_map.get(id);
   
   if (thrd == null) {
      thrd = new DicontrolThread(this,thrdxml); 
      thrd.update(thrdxml);
      thread_map.put(id,thrd);
    }
   else {
      thrd.update(thrdxml);
    }
   
   RunThreadState ost = thrd.getThreadState();
   
   switch (kind) {
      case CREATE :
         switch (ost) {
            case NONE :
            case NEW :
               thrd.setThreadState(RunThreadState.RUNNING);
               break;
            default :
               break;
          }
         break;
      case CHANGE :
         break;
      case RESUME :
         if (dtl == RunThreadStateDetail.EVALUATION_IMPLICIT) return;
         thrd.setThreadState(ost,dtl);
         break;
      case SUSPEND :
         if (dtl == RunThreadStateDetail.EVALUATION_IMPLICIT && iseval) return;
         else if (checkException(thrd,thrdxml)) {
            thrd.setThreadState(RunThreadState.EXCEPTION,dtl);
          }
         else if (!thrd.isStopped()) {
            thrd.setThreadState(ost,dtl);
          }
         else if (dtl == RunThreadStateDetail.BREAKPOINT) {
            thrd.setThreadState(RunThreadState.STOPPED,dtl);
          }
         else if (dtl == RunThreadStateDetail.EVALUATION_IMPLICIT) return;
         else if (thrd.isStopped()) {
            if (dtl != null) thrd.setThreadState(RunThreadState.STOPPED,dtl); 
          }
         break;
      case TERMINATE :
         thrd.setThreadState(RunThreadState.DEAD);
         thread_map.remove(id);
         break;
    }
}


private boolean checkException(DicontrolThread td,Element thrd)
{
   boolean fnd = false;
   td.setException(null);
   
   String exc = IvyXml.getAttrString(thrd,"EXCEPTION");
   if (exc != null) {
      td.setException(exc);
      return true;
    }
   
   for (Element bpt : IvyXml.children(thrd,"BREAKPOINT")) {
      String btyp = IvyXml.getAttrString(bpt,"TYPE");
      if (btyp != null && btyp.equals("EXCEPTION")) {
	 td.setException(IvyXml.getAttrString(bpt,"EXCEPTION"));
	 fnd = true; 
       }
    }
   
   return fnd;
}

/********************************************************************************/
/*                                                                              */
/*      State commands                                                          */
/*                                                                              */
/********************************************************************************/

void terminate()
{ }


void suspend()
{ }


void resume() 
{ }




}       // end of class DicontrolProcess




/* end of DicontrolProcess.java */

