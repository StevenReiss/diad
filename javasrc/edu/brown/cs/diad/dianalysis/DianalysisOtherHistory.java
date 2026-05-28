/********************************************************************************/
/*                                                                              */
/*              DianalysisOtherHistory.java                                     */
/*                                                                              */
/*      Handle history for user-defined problems                                */
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



package edu.brown.cs.diad.dianalysis;

import java.util.StringTokenizer;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisOtherHistory extends DianalysisHistory
{



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisOtherHistory(DianalysisManager fac,DiadSymptom symp,DiadThread thrd)
{
   super(fac,symp,thrd);
}


/********************************************************************************/
/*                                                                              */
/*      Process other history                                                   */
/*                                                                              */
/********************************************************************************/

@Override protected void process(IvyXmlWriter xw) throws DiadException
{
   DiadSymptom symp = getSymptom();
   
   IvyLog.logD("DIANALYSIS","Other history for " + symp.getOriginalExpression());
   
   String locxml = getSymptomLocation();
   if (locxml == null) {
      IvyLog.logE("STEM","No location for location query");
      throw new DiadException("Location undefined");
    }
   
   String vars = symp.getUserVariables();
   if (vars == null || vars.isEmpty()) {
      // extract vars from assertion
    }
   else {
      StringTokenizer tok = new StringTokenizer(vars,";");
      while (tok.hasMoreTokens()) {
         String var = tok.nextToken().trim();
         DiadStackFrame frm = getThread().getStack().getUserFrame();
         String method = frm.getClassName() + "." + frm.getMethodName();
         CommandArgs args = new CommandArgs("FILE",
               frm.getSourceFile().getAbsolutePath(),
               "START",-1,
               "LINE",frm.getLineNumber(),
               "TOKEN",var,
               "METHOD",method);
         Element rslt = getAnalysis().sendFaitMessage("VARQUERY",args,null);
         IvyLog.logD("DIANALYSIS","VAR Data: " + IvyXml.convertXmlToString(rslt));
         // still need to do flow query
       }
    }
   
   // if variables are set in symptom, add those to the output
   // otherwise if assertion is set, add all variables in that to the output
}



}       // end of class DianalysisOtherHistory




/* end of DianalysisOtherHistory.java */

