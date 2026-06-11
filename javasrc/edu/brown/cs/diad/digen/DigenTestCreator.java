/********************************************************************************/
/*                                                                              */
/*              DigenTestCreator.java                                           */
/*                                                                              */
/*      Thread to generate a test for a candidate                               */
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



package edu.brown.cs.diad.digen;

import edu.brown.cs.diad.dicore.DiadCandidate;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DigenTestCreator extends Thread implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiadCandidate for_candidate;
private DigenManager digen_manager;
private IvyXmlWriter xml_writer;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenTestCreator(DigenManager dm,DiadCandidate dc,IvyXmlWriter xw)
{
   super("DIGEN_TEST_CREATOR_" + dc.getId());
   
   digen_manager = dm;
   for_candidate = dc;
   xml_writer = xw;
}


/********************************************************************************/
/*                                                                              */
/*      Main processing method                                                  */
/*                                                                              */
/********************************************************************************/

@Override public void run()
{
   if (for_candidate.getSymptom() == null) {
      xml_writer.field("STATUS","NOSYMPTOM");
    }
   else {
      // create test case
      // if none, set STATUS = FAIL
      // else output the test case
    }
}


}       // end of class DigenTestCreator




/* end of DigenTestCreator.java */

