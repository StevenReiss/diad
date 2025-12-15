/********************************************************************************/
/*                                                                              */
/*              DiruntimeManager.java                                           */
/*                                                                              */
/*      description of class                                                    */
/*                                                                              */
/********************************************************************************/
/*	Copyright 2025 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 *  Permission to use, copy, modify, and distribute this software and its	 *
 *  documentation for any purpose other than its incorporation into a		 *
 *  commercial product is hereby granted without fee, provided that the 	 *
 *  above copyright notice appear in all copies and that both that		 *
 *  copyright notice and this permission notice appear in supporting		 *
 *  documentation, and that the name of Brown University not be used in 	 *
 *  advertising or publicity pertaining to distribution of the software 	 *
 *  without specific, written prior permission. 				 *
 *										 *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS		 *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND		 *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY	 *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY 	 *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,		 *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS		 *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE 	 *
 *  OF THIS SOFTWARE.								 *
 *										 *
 ********************************************************************************/


package edu.brown.cs.diad.diruntime;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadRuntimeCallback;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.swing.SwingEventListenerList;
import edu.brown.cs.ivy.xml.IvyXml;

public class DiruntimeManager implements DiruntimeConstants 
{ 


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private Map<String,DiruntimeProcess> process_map;
private Set<String> terminated_processes;
private SwingEventListenerList<DiadRuntimeCallback> runtime_listeners;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DiruntimeManager(DicontrolMain ctrl)
{
   diad_control = ctrl;
   process_map = new HashMap<>(); 
   terminated_processes = new ConcurrentSkipListSet<>();
   runtime_listeners = new SwingEventListenerList<>(DiadRuntimeCallback.class);
}


/********************************************************************************/
/*                                                                              */
/*      Callback methods                                                        */
/*                                                                              */
/********************************************************************************/

public void addRuntimeListener(DiadRuntimeCallback cb)
{
   runtime_listeners.add(cb);
}


public void removeRuntimeListener(DiadRuntimeCallback cb)
{
   runtime_listeners.remove(cb);
}



/********************************************************************************/
/*                                                                              */
/*      Helper methods                                                          */
/*                                                                              */
/********************************************************************************/

Element sendBubblesMessage(String cmd,CommandArgs args,String xml)
{
   return diad_control.sendBubblesMessage(cmd,args,xml);
}


/********************************************************************************/
/*                                                                              */
/*      Handle run events                                                       */
/*                                                                              */
/********************************************************************************/

public synchronized void handleRunEvent(Element xml)
{
   RunEventType type = IvyXml.getAttrEnum(xml,"TYPE",RunEventType.NONE); 
   
   switch (type) {
      default :
      case NONE :
         return;
      case PROCESS :
         handleProcessEvent(xml);
         break;
      case THREAD :
         handleThreadEvent(xml);
         break;
      case TARGET :
         handleTargetEvent(xml);
         break;
    }
}


private void handleProcessEvent(Element xml)
{
   RunEventKind kind = IvyXml.getAttrEnum(xml,"KIND",RunEventKind.NONE); 
   Element procxml = IvyXml.getChild(xml,"PROCESS");
   if (procxml == null) return;
   String id = IvyXml.getAttrString(procxml,"PID");
   DiruntimeProcess proc = null;
   
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
               proc = new DiruntimeProcess(this,procxml); 
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


private void handleThreadEvent(Element xml)
{ 
   Element thrd = IvyXml.getChild(xml,"THREAD");
   if (thrd == null) return;
   String pid = IvyXml.getAttrString(thrd,"PID");
   DiruntimeProcess proc = process_map.get(pid);
   if (proc == null || terminated_processes.contains(pid)) return;
   
   proc.updateThread(xml);
}


private void handleTargetEvent(Element xml)
{
   RunEventKind kind = IvyXml.getAttrEnum(xml,"KIND",RunEventKind.NONE);
   Element tgt = IvyXml.getChild(xml,"TARGET");
   String pid = IvyXml.getAttrString(tgt,"PID");
   if (pid == null) pid = IvyXml.getAttrString(tgt,"PROCESS");
   if (pid == null) return;
   DiruntimeProcess proc = process_map.get(pid);
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



/********************************************************************************/
/*                                                                              */
/*      Handle file updates                                                     */
/*                                                                              */
/********************************************************************************/

public void noteFileEdited(File f)
{ }


private void noteFileSaved(File f)
{ }


public void handleResourceChange(Element res)
{
   String k = IvyXml.getAttrString(res,"KIND");
   Element re = IvyXml.getChild(res,"RESOURCE");
   String rtyp = IvyXml.getAttrString(res,"TYPE");
   if (rtyp != null && rtyp.equals("FILE")) {
      String fp = IvyXml.getAttrString(re,"LOCATION");
      String proj = IvyXml.getAttrString(re,"PROJECT");
      switch (k) {
	 case "ADDED" :
	 case "ADDED_PHANTOM" :
	    break;
	 case "REMOVED" :
	 case "REMOVED_PHANTOM" :
	    break;
	 default :
	    IvyLog.logI("DIRUNTIME","CHANGE FILE " + fp + " IN " + proj);
            File f = new File(fp);
            noteFileSaved(f);
	    break;
       }
    }
}

}       // end of class DiruntimeManager




/* end of DiruntimeManager.java */

