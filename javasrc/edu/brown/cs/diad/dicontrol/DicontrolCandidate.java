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

import edu.brown.cs.diad.dicore.DiadCandidateCallback;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.swing.SwingEventListenerList;

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
private SwingEventListenerList<DiadCandidateCallback> candidate_listeners;
private CandidateThread candidate_processor;


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
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DiadThread getThread()                          { return for_thread; }
DiadCandidateState getState()                   { return candidate_state; }
DiadSymptom getSymptom()                        { return candidate_symptom; }

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
                  if (!isInterrupted()) {
                     if (sym != null) {
                        candidate_symptom = sym;
                        setState(DiadCandidateState.SYMPTOM_FOUND);
                      }
                     else {
                        setState(DiadCandidateState.NO_SYMPTOM);
                      }
                   }
                  break;
               case DEAD :
               case NO_STACK :
               case NO_SYMPTOM :
                  return;
               case SYMPTOM_FOUND :
                  System.exit(0);
                  // find starting frame
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
}


}       // end of class DicontrolCandidate




/* end of DicontrolCandidate.java */

