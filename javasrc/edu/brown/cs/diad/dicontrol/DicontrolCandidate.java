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

import edu.brown.cs.diad.dicore.DiadThread;

class DicontrolCandidate implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiadThread      for_thread;
private CandidateState  candidate_state; 


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolCandidate(DiadThread thrd)
{
   for_thread = thrd;
   candidate_state = CandidateState.INITIAL;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DiadThread getThread()                          { return for_thread; }



/********************************************************************************/
/*                                                                              */
/*      Action methods                                                          */
/*                                                                              */
/********************************************************************************/

void start() 
{
   if (candidate_state == CandidateState.INITIAL) {
      // start processing after a delay
    }
}



void terminate()
{
   candidate_state = CandidateState.DEAD;
}


}       // end of class DicontrolCandidate




/* end of DicontrolCandidate.java */

