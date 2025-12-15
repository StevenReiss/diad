/********************************************************************************/
/*                                                                              */
/*              DiruntimeThread.java                                            */
/*                                                                              */
/*      Representation of a runtime thread                                      */
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

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

class DiruntimeThread implements DiadThread, DiruntimeConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiruntimeProcess for_process;
private String thread_id;
private String thread_name;
private RunThreadType thread_type;  
private RunThreadState thread_state;
private RunThreadStateDetail thread_detail; 
private String exception_type;
private int num_frames;
private DiruntimeStack call_stack;

private static final Map<String,RunThreadType> KNOWN_THREADS;

static {
   KNOWN_THREADS = new HashMap<>();
   KNOWN_THREADS.put("AWT-Shutdown",RunThreadType.JAVA);
   KNOWN_THREADS.put("AWT-XAWT",RunThreadType.JAVA);
   KNOWN_THREADS.put("AWT-EventQueue-0",RunThreadType.UI);
   KNOWN_THREADS.put("AWT-EventQueue-1",RunThreadType.UI);
   KNOWN_THREADS.put("AWT-EventQueue-2",RunThreadType.UI);
   KNOWN_THREADS.put("AWT-EventQueue-3",RunThreadType.UI);
   KNOWN_THREADS.put("AWT-AppKit",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 0",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 1",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 2",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 3",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 4",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 5",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 6",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 7",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 8",RunThreadType.UI);
   KNOWN_THREADS.put("Image Fetcher 9",RunThreadType.UI);
   KNOWN_THREADS.put("Basic L&F File Loading Thread",RunThreadType.UI);
   KNOWN_THREADS.put("DestroyJavaVM",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("process reaper",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("Reference Handler",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("Finalizer",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("Signal Dispatcher",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(VM Periodic Task)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Signal Handler)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Sensor Event Thread)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(OC Main Thread)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Optimization Thread 1)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Optimization Thread 2)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Optimization Thread 3)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Optimization Thread 4)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Generation Thread 1)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Generation Thread 2)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Generation Thread 3)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Code Generation Thread 4)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("(Attach Listener)",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("VM JFR Buffer Thread",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("HandshakeCompletedNotify-Thread",RunThreadType.SYSTEM);
   KNOWN_THREADS.put("BandaidMonitorThread",RunThreadType.SYSTEM);
}


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiruntimeThread(DiruntimeProcess proc,Element xml)
{ 
   for_process = proc;
   thread_id = IvyXml.getAttrString(xml,"ID");
   thread_state = RunThreadState.NONE;
   thread_detail = RunThreadStateDetail.NONE;
   thread_type = RunThreadType.UNKNOWN;
   call_stack = null;
}


/********************************************************************************/
/*                                                                              */
/*      Update thread information from run event                                */
/*                                                                              */
/********************************************************************************/

void update(Element xml) 
{ 
   if (!IvyXml.isElement(xml,"THREAD")) {
      xml = IvyXml.getChild(xml,"THREAD");
    }
   
   thread_name = IvyXml.getAttrString(xml,"NAME",thread_name);
   
   if (IvyXml.getAttrBool(xml,"SYSTEM")) {
      thread_type = RunThreadType.SYSTEM;
    }
   else {
      RunThreadType btt = null;
      if (thread_name != null) btt = KNOWN_THREADS.get(thread_name);
      if (btt == null) btt = RunThreadType.USER;
      thread_type = btt;		
    }
   
   if (IvyXml.getAttrBool(xml,"STACK")) num_frames = IvyXml.getAttrInt(xml,"FRAMES",1);
   else num_frames = -1;
   if (IvyXml.getAttrBool(xml,"TERMINATED")) {
      thread_state = RunThreadState.DEAD;
    }
   else if (IvyXml.getAttrBool(xml,"SUSPENDED")) {
      thread_state = RunThreadState.STOPPED;
    }
   
   exception_type = null;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DiruntimeManager getManager()
{ 
   return for_process.getManager();
}


String getThreadId()
{
   return thread_id;
}

@Override public String getThreadName()
{
   if (thread_name == null) return thread_id;
   
   return thread_name; 
}


RunThreadState getThreadState()
{
   return thread_state;
}

RunThreadStateDetail getThreadStateDetail()
{
   return thread_detail;
}

@Override public String getExceptionType() 
{
   return exception_type;
}

boolean hasStack()
{
   return num_frames > 0;
}

DiruntimeProcess getProcess()
{
   return for_process;
}

@Override public boolean isInternal()
{
   switch (thread_type) {
      case JAVA :
      case SYSTEM :
         return true;
      default :
         break;
    }
   
   return false;
}


@Override public boolean isStopped() 
{
   switch (thread_state) {
      case STOPPED :
      case EXCEPTION :
         return true;
      default :
         break;
    }
   
   return false;
}


@Override public boolean isRunning()
{
   switch (thread_state) {
      case RUNNING :
      case WAITING :
      case TIMED_WAITING :
      case BLOCKED :
      case DEADLOCKED :
      case IDLE :
         return true;
      default :
         break;
    }
   
   return false;
}



@Override public boolean isTerminated()
{
   return thread_state == RunThreadState.DEAD;
}


void setThreadState(RunThreadState state)
{ 
   setThreadState(state,RunThreadStateDetail.NONE);
}


void setThreadState(RunThreadState state,RunThreadStateDetail detail)
{
   IvyLog.logD("DIRUNTIME","Set state of thread " + thread_name + " TO " + state);
   
   thread_state = state;
   thread_detail = detail;
   // stack_data = null;
}


void setException(String exc) { 
   exception_type = exc;
}


/********************************************************************************/
/*                                                                              */
/*      Stack methods                                                           */
/*                                                                              */
/********************************************************************************/

@Override public DiruntimeStack getStack()
{
   if (call_stack == null) {
      CommandArgs args = new CommandArgs("THREAD",thread_id);
      Element rply = getManager().sendBubblesMessage("GETSTACKFRAMES",args,null); 
      Element stack = IvyXml.getChild(rply,"STACKFRAMES");
      for (Element telt : IvyXml.children(stack,"THREAD")) {
         String teid = IvyXml.getAttrString(telt,"ID");
         if (teid.equals(thread_id)) {
            call_stack = new DiruntimeStack(telt);
            break;
          }
       }
    }
   
   return call_stack;
}




}       // end of class DiruntimeThread




/* end of DiruntimeThread.java */

