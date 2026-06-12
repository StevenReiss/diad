/********************************************************************************/
/*                                                                              */
/*              DigenValueContext.java                                          */
/*                                                                              */
/*      Context holding initializations for a test                              */
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

import java.util.HashMap;
import java.util.Map;

import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVarVal;

class DigenValueContext implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Map<String,String>      base_value_map;
private Map<DiadTraceVarVal,DigenCodeFragment> computed_code;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenValueContext(DigenTestCreator tc)
{
   base_value_map = new HashMap<>();
   computed_code = new HashMap<>();
}


DigenValueContext(DigenValueContext base,DigenCodeFragment code)
{
   
}


/********************************************************************************/
/*                                                                              */
/*      Keep track of what is computed                                          */
/*                                                                              */
/********************************************************************************/

DigenCodeFragment getComputedValue(DiadTraceVarVal var)
{
   return computed_code.get(var);
}


void noteComputed(DiadTraceVarVal var,DigenCodeFragment code)
{
   if (code == null) computed_code.remove(var);
   else computed_code.put(var,code);
}


}       // end of class DigenValueContext




/* end of DigenValueContext.java */

