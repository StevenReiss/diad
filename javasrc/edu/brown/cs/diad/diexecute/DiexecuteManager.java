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

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;

public class DiexecuteManager implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DiexecuteManager(DicontrolMain ctrl)
{
   diad_control = ctrl;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DicontrolMain getDiadControl()                  { return diad_control; }


/********************************************************************************/
/*                                                                              */
/*      <comment here>                                                          */
/*                                                                              */
/********************************************************************************/

public DiadStackFrame getStartingFrame(DiadSymptom symp,DiadThread thrd,
      Collection<DiadLocation> faults)
{
   DiexecuteStartFinder fndr = new DiexecuteStartFinder(this,
         thrd,faults);  
   
   return fndr.findStartingFrame(); 
}


}       // end of class DiexecuteManager




/* end of DiexecuteManager.java */

