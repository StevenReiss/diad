/********************************************************************************/
/*                                                                              */
/*              DicontrolEdits.java                                             */
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

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadEdits;

class DicontrolEdits implements DiadEdits
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Element         base_edits;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolEdits(Element edits)
{
   base_edits = edits;
}

}       // end of class DicontrolEdits




/* end of DicontrolEdits.java */

