/********************************************************************************/
/*                                                                              */
/*              DiexecuteChangedItems.java                                      */
/*                                                                              */
/*      Handle finding changed items for a stack frame                          */
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;

class DiexecuteChangedItems implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiexecuteManager exec_manager;
private DiadThread      for_thread;
private DiadStackFrame  start_frame;
private DiadSymptom     for_symptom;
private DiexecuteChangeData change_data;
 


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteChangedItems(DiexecuteManager mgr,DiadThread thrd,DiadStackFrame frm,
      DiadSymptom symp)
{
   exec_manager = mgr;
   for_thread = thrd;
   start_frame = frm;
   for_symptom = symp;
   
   change_data = getChangedVariables();
}


/********************************************************************************/
/*                                                                              */
/*      Find changed items                                                      */
/*                                                                              */
/********************************************************************************/

DiexecuteChangeData getChangedVariables()
{
   DiexecuteChangeFinder fndr = new DiexecuteChangeFinder(exec_manager); 
   return fndr.process(for_thread,for_symptom,start_frame); 
}


/********************************************************************************/
/*                                                                              */
/*      Processing methods -- all possible actions                              */
/*                                                                              */
/********************************************************************************/

List<DiexecuteAction> getResetActions(DiexecuteManager mgr,DiexecuteExecution ve)
{
   DiadStack bs = for_thread.getStack();
   DiadStackFrame startframe = null;
   for (DiadStackFrame bsf : bs.getFrames()) {
      if (bsf.getFrameId().equals(start_frame.getFrameId())) {
         startframe = bsf;
         break;
       }
    }
   
   DiexecuteTrace vt = ve.getSeedeResult();
   DiexecuteCall vc = vt.getRootContext();
   
   DiexecuteSetup vs = new DiexecuteSetup(mgr,change_data,startframe,vc);
   
   List<DiexecuteAction> rslt = vs.findResets();
   
   return rslt;
}


/********************************************************************************/
/*                                                                              */
/*      Processing methods: Simple parameters                                   */
/*                                                                              */
/********************************************************************************/

List<DiexecuteAction> getParameterActions()
{
   Collection<DiexecuteChangeVariable> items = change_data.getTopParameters();
   
   if (items == null) return null;
   
   List<DiexecuteAction> rslt = new ArrayList<>();
   
   Set<DiexecuteChangeVariable> params = new HashSet<>();
   for (DiexecuteChangeVariable tv : items) {
      if (tv.getVariableType() == DiexecuteVariableType.PARAMETER) {
         params.add(tv);
       }
    }
   if (!params.isEmpty()) {
      Map<String,DiadValue> pvals = for_thread.getParameterValues(start_frame);
      if (pvals != null) {
         for (DiexecuteChangeVariable tv : params) {
            DiadValue bv = pvals.get(tv.getName());
            if (bv != null) rslt.add(DiexecuteAction.createSetAction(tv.getName(),bv));
          }
       }
    }
   
   return rslt;
}


}       // end of class DiexecuteChangedItems




/* end of DiexecuteChangedItems.java */

