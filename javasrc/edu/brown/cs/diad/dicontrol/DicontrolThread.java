/********************************************************************************/
/*                                                                              */
/*              DicontrolThread.java                                            */
/*                                                                              */
/*      Representation of an execution thread                                   */
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

import org.w3c.dom.Element;

import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;

class DicontrolThread implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolProcess for_process;
private String thread_id;
private String thread_name;
private RunThreadType thread_type;  
private RunThreadState thread_state;
private RunThreadStateDetail thread_detail; 
private String exception_type;
private int num_frames;

private static final Map<String,RunThreadType> known_threads;

static {
   known_threads = new HashMap<>();
   known_threads.put("AWT-Shutdown",RunThreadType.JAVA);
   known_threads.put("AWT-XAWT",RunThreadType.JAVA);
   known_threads.put("AWT-EventQueue-0",RunThreadType.UI);
   known_threads.put("AWT-EventQueue-1",RunThreadType.UI);
   known_threads.put("AWT-EventQueue-2",RunThreadType.UI);
   known_threads.put("AWT-EventQueue-3",RunThreadType.UI);
   known_threads.put("AWT-AppKit",RunThreadType.UI);
   known_threads.put("Image Fetcher 0",RunThreadType.UI);
   known_threads.put("Image Fetcher 1",RunThreadType.UI);
   known_threads.put("Image Fetcher 2",RunThreadType.UI);
   known_threads.put("Image Fetcher 3",RunThreadType.UI);
   known_threads.put("Image Fetcher 4",RunThreadType.UI);
   known_threads.put("Image Fetcher 5",RunThreadType.UI);
   known_threads.put("Image Fetcher 6",RunThreadType.UI);
   known_threads.put("Image Fetcher 7",RunThreadType.UI);
   known_threads.put("Image Fetcher 8",RunThreadType.UI);
   known_threads.put("Image Fetcher 9",RunThreadType.UI);
   known_threads.put("Basic L&F File Loading Thread",RunThreadType.UI);
   known_threads.put("DestroyJavaVM",RunThreadType.SYSTEM);
   known_threads.put("process reaper",RunThreadType.SYSTEM);
   known_threads.put("Reference Handler",RunThreadType.SYSTEM);
   known_threads.put("Finalizer",RunThreadType.SYSTEM);
   known_threads.put("Signal Dispatcher",RunThreadType.SYSTEM);
   known_threads.put("(VM Periodic Task)",RunThreadType.SYSTEM);
   known_threads.put("(Signal Handler)",RunThreadType.SYSTEM);
   known_threads.put("(Sensor Event Thread)",RunThreadType.SYSTEM);
   known_threads.put("(OC Main Thread)",RunThreadType.SYSTEM);
   known_threads.put("(Code Optimization Thread 1)",RunThreadType.SYSTEM);
   known_threads.put("(Code Optimization Thread 2)",RunThreadType.SYSTEM);
   known_threads.put("(Code Optimization Thread 3)",RunThreadType.SYSTEM);
   known_threads.put("(Code Optimization Thread 4)",RunThreadType.SYSTEM);
   known_threads.put("(Code Generation Thread 1)",RunThreadType.SYSTEM);
   known_threads.put("(Code Generation Thread 2)",RunThreadType.SYSTEM);
   known_threads.put("(Code Generation Thread 3)",RunThreadType.SYSTEM);
   known_threads.put("(Code Generation Thread 4)",RunThreadType.SYSTEM);
   known_threads.put("(Attach Listener)",RunThreadType.SYSTEM);
   known_threads.put("VM JFR Buffer Thread",RunThreadType.SYSTEM);
   known_threads.put("HandshakeCompletedNotify-Thread",RunThreadType.SYSTEM);
   known_threads.put("BandaidMonitorThread",RunThreadType.SYSTEM);
}


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolThread(DicontrolProcess proc,Element xml)
{ 
   for_process = proc;
   thread_id = IvyXml.getAttrString(xml,"ID");
   thread_state = RunThreadState.NONE;
   thread_detail = RunThreadStateDetail.NONE;
   thread_type = RunThreadType.UNKNOWN;
   
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
      if (thread_name != null) btt = known_threads.get(thread_name);
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

String getThreadId()
{
   return thread_id;
}

String getThreadName()
{
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

String getExceptionType()
{
   return exception_type;
}

boolean hasStack()
{
   return num_frames > 0;
}

DicontrolProcess getProcess()
{
   return for_process;
}

boolean isInternal()
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

boolean isTerminated()
{
   return thread_state == RunThreadState.DEAD;
}


void setThreadState(RunThreadState state)
{ 
   setThreadState(state,RunThreadStateDetail.NONE);
}


void setThreadState(RunThreadState state,RunThreadStateDetail detail)
{
    IvyLog.logD("DICONTROL","Set state of thread " + thread_name + " TO " + state);
    
    thread_state = state;
    thread_detail = detail;
    // stack_data = null;
}


void setException(String exc) { 
   exception_type = exc;
}

boolean isStopped() 
{
   switch (thread_state) {
      case DEAD :
      case STOPPED :
      case EXCEPTION :
         return true;
      default :
         break;
    }
   
   return false;
}




}       // end of class DicontrolThread




/* end of DicontrolThread.java */

