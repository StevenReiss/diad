/********************************************************************************/
/*                                                                              */
/*              DicontrolCandidate.java                                         */
/*                                                                              */
/*      Candidate for bug repair                                                */
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

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import edu.brown.cs.diad.dianalysis.DianalysisFactory;
import edu.brown.cs.diad.dicore.DiadCandidateCallback;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.swing.SwingEventListenerList;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DicontrolCandidate implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private DiadThread      for_thread;
private DiadStackFrame  for_frame;
private DiadCandidateState  candidate_state; 
private DiadSymptom     candidate_symptom;
private String          candidate_id;
private SwingEventListenerList<DiadCandidateCallback> candidate_listeners;
private CandidateThread candidate_processor;
private Set<File>       candidate_files;
private DiadAnalysisFileMode file_mode;

private static AtomicInteger candidate_counter = new AtomicInteger(0);



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolCandidate(DicontrolMain ctrl,DiadThread thrd)
{
   diad_control = ctrl;
   for_thread = thrd;
   candidate_state = DiadCandidateState.INITIAL; 
   candidate_symptom = null;
   candidate_listeners = new SwingEventListenerList<>(DiadCandidateCallback.class);
   candidate_processor = null;
   candidate_id = "DIAD_ " + candidate_counter.incrementAndGet();
   candidate_files = new HashSet<>();
   file_mode = diad_control.getProperty("Diad.file.mode",
         DiadAnalysisFileMode.FAIT_FILES);
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DiadThread getThread()                          { return for_thread; }
DiadCandidateState getState()                   { return candidate_state; }
DiadSymptom getSymptom()                        { return candidate_symptom; }
String getId()                                  { return candidate_id; }

void addCandidateListener(DiadCandidateCallback cb)
{
   candidate_listeners.add(cb);
}

void removeCandidateListener(DiadCandidateCallback cb)
{
   candidate_listeners.remove(cb);
}


/********************************************************************************/
/*                                                                              */
/*      State change methods                                                    */
/*                                                                              */
/********************************************************************************/

void setState(DiadCandidateState st)
{
   if (st == candidate_state) return;
   
   candidate_state = st;
   for (DiadCandidateCallback cb : candidate_listeners) {
      cb.stateChanged();
    }
}

/********************************************************************************/
/*                                                                              */
/*      Action methods                                                          */
/*                                                                              */
/********************************************************************************/

void start() 
{
   if (candidate_processor != null) {
      stopProcessing();
    }

   switch (candidate_state) {
      case INITIAL :
      case SYMPTOM_FOUND :
      case EXECUTION_DONE :
      case STARTING_FRAME_FOUND :
         candidate_processor = new CandidateThread();
         candidate_processor.start();
         break;
      case NO_SYMPTOM :   
         break;
      case NO_STACK :
      case DEAD :
         candidate_state = DiadCandidateState.DEAD;
         break;
    }
}


void setSymptom(DiadSymptom symp)
{
   // set symptom if possible and restart
}

void addFiles(Collection<File> files)
{
   // add to files, if any new files, restart
}

void setFileMode(DiadAnalysisFileMode mode)
{
   file_mode = mode;
   // restart analysis
}

void terminate()
{
   candidate_state = DiadCandidateState.DEAD;
   stopProcessing();
}


private synchronized void stopProcessing()
{
   while (candidate_processor.isAlive()) {
      candidate_processor.interrupt();
      try {
         wait(100);
       }
      catch (InterruptedException e) { }  
    }
   candidate_processor = null;
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw) 
{
   xw.begin("CANDIDATE");
   xw.field("ID",candidate_id);
   xw.field("STATE",candidate_state);
   for_thread.outputXml(xw);
   for_frame.outputXml(xw);
   xw.end("CANDIDATE");
}



/********************************************************************************/
/*                                                                              */
/*      Thread to process the candidate                                         */
/*                                                                              */
/********************************************************************************/

private final class CandidateThread extends Thread {
   
   CandidateThread() {
      super("CandidateProcessor_" + for_thread.getThreadName()); 
    }
   
   @Override public void run() {
      for ( ; ; ) {
         try {
            switch (candidate_state) {
               case INITIAL :
                  DiadStack stk = for_thread.getStack();
                  if (stk == null || for_thread.isInternal()) { 
                     setState(DiadCandidateState.NO_STACK);
                     return;
                   }
                  for_frame = stk.getUserFrame();
                  if (for_frame == null) {
                     setState(DiadCandidateState.NO_STACK);
                     return;
                   }
                  DicontrolSymptomFinder finder =
                     new DicontrolSymptomFinder(diad_control,for_thread,
                           stk,for_frame);
                  DiadSymptom sym = finder.findSymptom();
                  if (checkInterrupted()) break;
                  if (sym != null) {
                     candidate_symptom = sym;
                     setState(DiadCandidateState.SYMPTOM_FOUND);
                   }
                  else {
                     setState(DiadCandidateState.NO_SYMPTOM);
                   }
                  break;
               case DEAD :
               case NO_STACK :
               case NO_SYMPTOM :
               case NO_ANALYSIS :
               case NO_START_FRAME :
               case INTERUPTED : 
                  return;
               case SYMPTOM_FOUND :
                  DianalysisFactory anal = diad_control.getAnalysisManager();
                  if (checkInterrupted()) break;
                  anal.addFiles(file_mode,candidate_files,for_thread);  
                  if (checkInterrupted()) break;
                  Boolean fg = anal.waitForAnalysis(); 
                  if (fg == null || checkInterrupted()) break;
                  if (fg) {
                     setState(DiadCandidateState.ANALYSIS_DONE);
                   }
                  else {
                     setState(DiadCandidateState.NO_ANALYSIS);
                   }
                  break;
               case ANALYSIS_DONE :
                  // find initial locations
                  break; 
               case INITIAL_LOCATIONS :
                  // find starting frame *ROSE ValidateStartLocator)
                  break;
               case STARTING_FRAME_FOUND :
                  // start execution
                  break;
               case EXECUTION_DONE :
                  // candidate has been processed
                  return;
             }
          }
         catch (Throwable e) {
            if (isInterrupted()) {
               return;
             }
            IvyLog.logE("DICONTROL","Problem processing candidate",e);
            return;
          }
       }
    }
   
   private boolean checkInterrupted() {
      if (isInterrupted()) {
         setState(DiadCandidateState.INTERUPTED); 
         return true;
       }
      return false;
    }
}





}       // end of class DicontrolCandidate




/* end of DicontrolCandidate.java */

