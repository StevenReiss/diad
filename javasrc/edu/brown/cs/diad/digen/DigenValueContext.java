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
import java.util.concurrent.atomic.AtomicInteger;

import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVarVal;
import edu.brown.cs.ivy.file.IvyLog;

class DigenValueContext implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Map<DiadTraceVarVal,DigenCodeFragment> computed_code;
private DigenCodeFragment initial_set;

private static AtomicInteger var_counter = new AtomicInteger(0);



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenValueContext()
{
   computed_code = new HashMap<>();
   initial_set = new DigenCodeFragment("");
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DigenCodeFragment getInitializations()
{
   return initial_set;
}


String getNextVariable()
{
   String var = "var" + var_counter.incrementAndGet();
   
   return var;
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


DigenCodeFragment saveComputedValue(DiadTraceVarVal val,DigenCodeFragment code)
{
   String typ = val.getDataType(CURRENT);
   String var = getNextVariable();
   String decl = typ + " " + var + " = " + code + ";";
   addInitialization(decl);
   DigenCodeFragment rslt = new DigenCodeFragment(var);
   
   noteComputed(val,rslt);
   
   return rslt;
}


void addInitialization(DigenCodeFragment code)
{
   IvyLog.logD("DIGEN","Add code initialization " + code);
   initial_set = initial_set.append(code,true);
}

void addInitialization(String code)
{
   IvyLog.logD("DIGEN","Add string initialization " + code);
   initial_set = initial_set.append(code,true);
}


void noteComputed(DiadTraceVarVal var,DigenCodeFragment code)
{
   IvyLog.logD("DIGEN","Value for " + var.getName() + " = " + code);
   
   if (code == null) computed_code.remove(var);
   else computed_code.put(var,code);
}


}       // end of class DigenValueContext




/* end of DigenValueContext.java */

