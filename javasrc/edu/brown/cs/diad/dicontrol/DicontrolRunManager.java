/********************************************************************************/
/*                                                                              */
/*              DicontrolRunManager.java                                        */
/*                                                                              */
/*      Keep track of debugger executions                                       */
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;

class DicontrolRunManager implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Map<String,DicontrolProcess> process_map;
private Set<String> terminated_processes;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolRunManager()
{
   process_map = new HashMap<>(); 
   terminated_processes = new ConcurrentSkipListSet<>();
}


/********************************************************************************/
/*                                                                              */
/*      Handle run events                                                       */
/*                                                                              */
/********************************************************************************/

synchronized void handleRunEvent(Element xml,long when)
{
   RunEventType type = IvyXml.getAttrEnum(xml,"TYPE",RunEventType.NONE); 
   
   switch (type) {
      default :
      case NONE :
         return;
      case PROCESS :
         handleProcessEvent(xml,when);
         break;
      case THREAD :
         handleThreadEvent(xml,when);
         break;
      case TARGET :
         handleTargetEvent(xml,when);
         break;
    }
}


private void handleProcessEvent(Element xml,long when)
{
      RunEventKind kind = IvyXml.getAttrEnum(xml,"KIND",RunEventKind.NONE); 
      Element procxml = IvyXml.getChild(xml,"PROCESS");
      if (procxml == null) return;
      String id = IvyXml.getAttrString(procxml,"PID");
      DicontrolProcess proc = null;
      
      boolean term = IvyXml.getAttrBool(procxml,"TERMINATE");
      if (term) {
         if (kind == RunEventKind.CHANGE) kind = RunEventKind.TERMINATE;
       }
      
      switch (kind) {
         case TERMINATE :
            proc = process_map.get(id);
            if (proc != null) {
               proc.terminate(); 
               terminated_processes.add(id);
               process_map.remove(id);
             }
            break;
         case CREATE :
         case CHANGE :
            synchronized (process_map) {
               proc = process_map.get(id);
               if (proc == null && !term) {
                  proc = new DicontrolProcess(procxml); 
                  process_map.put(id,proc);
                }
               else {
                  proc.update(procxml); 
                }
             }
            break;
         default :
            break;
       }
}


private void handleThreadEvent(Element xml,long when)
{ 
   Element thrd = IvyXml.getChild(xml,"THREAD");
   if (thrd == null) return;
   String pid = IvyXml.getAttrString(thrd,"PID");
   DicontrolProcess proc = process_map.get(pid);
   if (proc == null || terminated_processes.contains(pid)) return;
   
   proc.updateThread(xml);
}


private void handleTargetEvent(Element xml,long when)
{
   RunEventKind kind = IvyXml.getAttrEnum(xml,"KIND",RunEventKind.NONE);
   Element tgt = IvyXml.getChild(xml,"TARGET");
   String pid = IvyXml.getAttrString(tgt,"PID");
   if (pid == null) pid = IvyXml.getAttrString(tgt,"PROCESS");
   if (pid == null) return;
   DicontrolProcess proc = process_map.get(pid);
   if (proc == null) return;
   
   switch (kind) {
      case SUSPEND :
         proc.suspend();
         break;
      case RESUME :
         proc.resume();
         break;
      case TERMINATE :
         proc.terminate();
         break;
    } 
}



}       // end of class DicontrolRunManager




/* end of DicontrolRunManager.java */

