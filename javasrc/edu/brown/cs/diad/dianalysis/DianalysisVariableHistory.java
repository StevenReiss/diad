/********************************************************************************/
/*                                                                              */
/*              DianalysisVariableHistory.java                                  */
/*                                                                              */
/*      Find starting point for variable having the wrong value                 */
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

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisVariableHistory extends DianalysisHistory
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String variable_name;
private String current_value;
private String shouldbe_value;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisVariableHistory(DianalysisManager anal,DiadSymptom symp,DiadThread thrd)
{
   super(anal,symp,thrd);
   variable_name = symp.getSymptomItem();
   current_value = symp.getOriginalValue();
   shouldbe_value = symp.getTargetValue();
}


/********************************************************************************/
/*                                                                              */
/*      Process the query                                                       */
/*                                                                              */
/********************************************************************************/

@Override protected void process(IvyXmlWriter xw) throws DiadException
{
   Element qrslt = getVarData();
   if (qrslt == null) throw new DiadException("Can't find variable");
   
   Element hrslt = getHistoryData(qrslt);
   outputGraph(hrslt,xw);
}


/********************************************************************************/
/*                                                                              */
/*      Get variable and location data                                          */
/*                                                                              */
/********************************************************************************/

private Element getVarData()
{
   getAnalysis().waitForAnalysis();
   
   IvyLog.logD("DIANALYSIS","START VAR Query: " + variable_name + 
         " " + current_value + " " +
         shouldbe_value);
   DiadStackFrame frm = getThread().getStack().getUserFrame();
   String method = frm.getMethodName();
   CommandArgs args = new CommandArgs("FILE",
         frm.getSourceFile().getAbsolutePath(),
         "START",-1,
         "LINE",frm.getLineNumber(),
         "TOKEN",variable_name,
         "METHOD",method);
   Element rslt = getAnalysis().sendFaitMessage("VARQUERY",args,null);
   IvyLog.logD("DIANALYSIS","VAR Data: " + IvyXml.convertXmlToString(rslt));
   Element vset = IvyXml.getChild(rslt,"VALUESET");
   
// Element refelt = IvyXml.getChild(vset,"REFERENCE");
// Element refval = IvyXml.getChild(refelt,"VALUE");
   
   return vset;
}



/********************************************************************************/
/*                                                                              */
/*      Get history data                                                        */
/*                                                                              */
/********************************************************************************/

private Element getHistoryData(Element vdata) throws DiadException
{
   CommandArgs args = new CommandArgs("QTYPE","VARIABLE",
         "CURRENT",current_value,
         "TOKEN",variable_name);
   args = addCommandArgs(args);
   
   StringBuffer buf = new StringBuffer();
   Element reference = null;
   for (Element refval : IvyXml.children(vdata,"REFVALUE")) {
      Element loc = IvyXml.getChild(refval,"LOCATION");
      Element ref = IvyXml.getChild(refval,"REFERENCE");
      if (loc == null || ref == null) continue;
      if (reference == null) reference = IvyXml.getChild(ref,"VALUE");
      buf.append(IvyXml.convertXmlToString(loc));
      buf.append("\n");
    }
   if (reference == null) throw new DiadException("No reference for variable");
   buf.append(IvyXml.convertXmlToString(reference));
   String qxml = buf.toString();
   String sxml = getXmlForStack();
   if (sxml != null) qxml += sxml;
   Element rslt = getAnalysis().sendFaitMessage("FLOWQUERY",args,qxml);
   
   return rslt;
}


}       // end of class DianalysisVariableHistory




/* end of DianalysisVariableHistory.java */

