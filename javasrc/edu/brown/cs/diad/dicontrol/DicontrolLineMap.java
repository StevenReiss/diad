/********************************************************************************/
/*                                                                              */
/*              DicontrolLineMap.java                                           */
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
import java.util.Map;

import edu.brown.cs.diad.dicore.DiadEdits;

class DicontrolLineMap implements DicontrolConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private File            for_file;
private Map<Integer,Integer> known_lines;
private int             max_line;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolLineMap(DiadEdits edits)
{
}


/********************************************************************************/
/*                                                                              */
/*      Get edited line nubmer                                                  */
/*                                                                              */
/********************************************************************************/

int getEditedLine(File f,int orig)
{
   if (!f.equals(for_file)) return orig;
   Integer v = known_lines.get(orig);
   if (v != null) return v;
   if (orig > max_line && max_line > 0) {
      Integer dv = known_lines.get(max_line);
      return orig + (dv-max_line);
    }
   return orig;
}



}       // end of class DicontrolLineMap




/* end of DicontrolLineMap.java */

