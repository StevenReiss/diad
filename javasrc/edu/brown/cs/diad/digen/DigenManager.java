/********************************************************************************/
/*                                                                              */
/*              Digenmanager.java                                               */
/*                                                                              */
/*      Factory/manager for generating test case for a stopping point           */
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

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadCandidate;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public class DigenManager implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain diad_main;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DigenManager(DicontrolMain diad)
{
   diad_main = diad;
}


/********************************************************************************/
/*                                                                              */
/*      Create a test case                                                      */
/*                                                                              */
/********************************************************************************/

public void createTestCase(DiadCandidate cand,int frame,IvyXmlWriter xw)
{
   DigenTestCreator tc = new DigenTestCreator(this,cand,frame,xw);  
   tc.process();
} 


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DicontrolMain getDiad()                 { return diad_main; }



}       // end of class DigenManager




/* end of DigenFactory.java */

