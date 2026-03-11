/********************************************************************************/
/*                                                                              */
/*              DicontrolRepair.java                                            */
/*                                                                              */
/*      description of class                                                    */
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

import edu.brown.cs.diad.dicore.DiadEdits;
import edu.brown.cs.diad.dicore.DiadRepair;

class DicontrolRepair implements DiadRepair, DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiadEdits        base_edits;
private DicontrolLineMap line_map; 
private String           subsession_id;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolRepair(DicontrolMain diad,DiadEdits edits)
{
   base_edits = edits;
   line_map = new DicontrolLineMap(edits);   
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public int getMappedLine(File f,int line) 
{
   return line_map.getEditedLine(f,line);
}


}       // end of class DicontrolRepair




/* end of DicontrolRepair.java */

