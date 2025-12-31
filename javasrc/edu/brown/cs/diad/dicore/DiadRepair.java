/********************************************************************************/
/*                                                                              */
/*              DiadRepair.java                                                 */
/*                                                                              */
/*      Representation of a possible bug fixing repair                          */
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



package edu.brown.cs.diad.dicore;

import java.io.File;


public abstract class DiadRepair implements DiadConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/


/********************************************************************************/
/*                                                                              */
/*      Acce3ss methods                                                         */
/*                                                                              */
/********************************************************************************/

public DiadLocation getLocation()               { return null; }

public long getMappedLine(File f,long line)
{
   return line;
}

public double getPriority()                     { return 1.0; }
public double getFinderPriority()               { return 1.0; }

public void setCount(int ct)                           { }
public void setSeedeCount(long ct)                     { }



}       // end of class DiadRepair




/* end of DiadRepair.java */

