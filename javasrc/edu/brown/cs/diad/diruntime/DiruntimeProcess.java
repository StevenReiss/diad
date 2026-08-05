/********************************************************************************/
/*                                                                              */
/*              DiruntimeProcess.java                                           */
/*                                                                              */
/*       Runtime model of a process being debugged                              */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2025 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 *  Permission to use, copy, modify, and distribute this software and its        *
 *  documentation for any purpose other than its incorporation into a            *
 *  commercial product is hereby granted without fee, provided that the          *
 *  above copyright notice appear in all copies and that both that               *
 *  copyright notice and this permission notice appear in supporting             *
 *  documentation, and that the name of Brown University not be used in          *
 *  advertising or publicity pertaining to distribution of the software          *
 *  without specific, written prior permission.                                  *
 *                                                                               *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS                *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND            *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY      *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY          *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,              *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS               *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE          *
 *  OF THIS SOFTWARE.                                                            *
 *                                                                               *
 ********************************************************************************/


package edu.brown.cs.diad.diruntime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadLocalVariable;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;

class DiruntimeProcess implements DiruntimeConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiruntimeManager run_manager;
private Map<String,DiruntimeThread> thread_map; 
private String process_id;
private boolean is_running;
private Map<String,DiruntimeType> type_map;
private Map<String,DiruntimeValueData> unique_values;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiruntimeProcess(DiruntimeManager mgr,Element xml) 
{ 
   run_manager = mgr;
   thread_map = new ConcurrentHashMap<>();
   process_id = IvyXml.getAttrString(xml,"PID");
   is_running = true;
   type_map = new HashMap<>();
   unique_values = new HashMap<>();
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

Iterable<DiruntimeThread> getThreads()
{
   List<DiruntimeThread> rslt = new ArrayList<>();
 
   for (DiruntimeThread dt : thread_map.values()) {
      if (process_id.equals(dt.getProcessId()) && 
            !dt.isInternal() && !dt.isTerminated()) {
         IvyLog.logD("DIRUNTIME","Use thread " + dt.getThreadId());
         rslt.add(dt);
       }
      else {
         IvyLog.logD("DIRUNTIME","Skip thread " + dt.getThreadId() + " " +
               process_id + " " + dt.getProcessId() + " " +
               dt.isInternal() + " " + dt.isTerminated());
       }
    }
   
   return rslt;
}

DiruntimeManager getManager()           { return run_manager; }

String getId()                          { return process_id; }

boolean isRunning()                     { return is_running; }



/********************************************************************************/
/*                                                                              */
/*      Local type access methods                                               */
/*                                                                              */
/********************************************************************************/

DiruntimeType findType(String typ)
{ 
   synchronized (type_map) {
      DiruntimeType bt = type_map.get(typ);
      if (bt != null) return bt;
    }
   
   DiruntimeType nbt = DiruntimeType.createNewType(this,typ); 
   
   synchronized (type_map) {
      DiruntimeType bt = type_map.putIfAbsent(typ,nbt);
      if (bt != null) return bt;
    }
   
   return nbt;
}


/********************************************************************************/
/*                                                                              */
/*      Manage unique values                                                    */
/*                                                                              */
/********************************************************************************/

DiruntimeValueData getUniqueValue(DiruntimeValueData bvd)
{
   if (bvd == null) return null;
   switch (bvd.getKind()) {
      case OBJECT :
      case ARRAY :
         String dnm = bvd.getValueString();
         if (dnm != null && dnm.length() > 0) {
            synchronized (unique_values) {
               DiruntimeValueData nsvd = unique_values.get(dnm);
               if (nsvd == bvd) ;
               else if (nsvd != null) {
                  nsvd.merge(bvd);
                  bvd = nsvd;
                }
               else {
                  unique_values.put(dnm,bvd);
                }
             }
          }
         break; 
      default :
         break;
    }
   
   return bvd;
}



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
   DiruntimeThread thrd = thread_map.get(id);
   
   if (thrd == null) {
      IvyLog.logD("DIRUNTIME","Creating new thread " + id + " for " + process_id + " " +
            hashCode() + " " + thread_map.size());
      thrd = new DiruntimeThread(this,thrdxml); 
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
         thrd.setThreadState(RunThreadState.RUNNING,dtl);
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


private boolean checkException(DiruntimeThread td,Element thrd) 
{
   boolean fnd = false;
   td.setException(null,null);
   
   String exc = IvyXml.getAttrString(thrd,"EXCEPTION");
   if (exc != null) {
      td.setException(exc,null);
      return true;
    }
   
   for (Element bpt : IvyXml.children(thrd,"BREAKPOINT")) {
      String btyp = IvyXml.getAttrString(bpt,"TYPE");
      if (btyp != null && btyp.equals("EXCEPTION")) {
         exc = IvyXml.getAttrString(bpt,"EXCEPTION");
         if (td.getStack() == null) continue;
         DiadStackFrame frm = td.getStack().getTopFrame();
         if (frm == null) continue;
         for (String vnm : frm.getLocals()) {
            DiadLocalVariable var = frm.getLocal(vnm);
            String typ = var.getType();
            if (typ.contains("Exception") || 
                  typ.contains("Error") ||
                  typ.contains("Throwable")) {
               exc = typ;
              // want to use the last one in the list that is an exception 
             }
            td.setException(exc,null);
            fnd = true; 
          }
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
{ 
   for (DiruntimeThread thrd : getThreads()) {
      if (thrd.getThreadState() == RunThreadState.RUNNING) {
         thrd.setThreadState(RunThreadState.STOPPED);
       }
    }
}


void resume() 
{ 
   for (DiruntimeThread thrd : getThreads()) {
      if (thrd.getThreadState() == RunThreadState.STOPPED) {
         thrd.setThreadState(RunThreadState.RUNNING);
       }
    }
}



}       // end of class DiruntimeProcess




/* end of DiruntimeProcess.java */

