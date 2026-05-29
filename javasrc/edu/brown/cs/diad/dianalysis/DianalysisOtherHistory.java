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
      StringBuffer buf = new StringBuffer();
      StringTokenizer tok = new StringTokenizer(vars,";");
      while (tok.hasMoreTokens()) {
         String var = tok.nextToken().trim();
         CommandArgs args = new CommandArgs("START",-1,
               "TOKEN",var);
         args = addCommandArgs(args);
         Element rslt = getAnalysis().sendFaitMessage("VARQUERY",args,null);
         IvyLog.logD("DIANALYSIS","VAR Data: " + IvyXml.convertXmlToString(rslt));
         for (Element vdata : IvyXml.children(rslt,"VALUESET")) {
            args = new CommandArgs("QTYPE","VARIABLE",
                  "TOKEN",var);
            args = addCommandArgs(args);
            Element reference = null;
            for (Element refval : IvyXml.children(vdata,"REFVALUE")) {
               Element loc = IvyXml.getChild(refval,"LOCATION");
               Element ref = IvyXml.getChild(refval,"REFERENCE");
               if (loc == null && ref == null) continue;
               if (reference == null) reference = IvyXml.getChild(ref,"VALUE");
               if (loc != null) {
                  buf.append(IvyXml.convertXmlToString(loc));
                  buf.append("\n");
                }
               if (reference != null) {
                  buf.append(IvyXml.convertXmlToString(reference));
                  buf.append("\n");
                }
             }   
          }
       }
      String qxml = buf.toString();
      String sxml = getXmlForStack();
      if (sxml != null) qxml += "\n" + sxml;
      CommandArgs args1 = new CommandArgs("QTYPE","VARIABLE",
            "CLEAN",false);
      args1 = addCommandArgs(args1);
      Element rslt = getAnalysis().sendFaitMessage("FLOWQUERY",args1,qxml); 
      outputGraph(rslt,xw);
    }
   
   // if variables are set in symptom, add those to the output
   // otherwise if assertion is set, add all variables in that to the output
}



}       // end of class DianalysisOtherHistory




/* end of DianalysisOtherHistory.java */

