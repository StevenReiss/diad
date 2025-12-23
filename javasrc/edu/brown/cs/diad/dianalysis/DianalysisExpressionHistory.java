/********************************************************************************/
/*                                                                              */
/*              DianalysisExpressionHistory.java                                */
/*                                                                              */
/*      Find starting points for expression having wrong value                  */
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
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisExpressionHistory extends DianalysisHistory
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String expression_name;
private String current_value;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DianalysisExpressionHistory(DianalysisFactory fac,DiadSymptom symp,DiadThread thrd)
{ 
   super(fac,symp,thrd);
   expression_name = symp.getSymptomItem();
   current_value = symp.getOriginalValue();
//    shouldbe_value = prob.getTargetValue();
}


/********************************************************************************/
/*                                                                              */
/*      Process expression query                                                */
/*                                                                              */
/********************************************************************************/

@Override protected void process(IvyXmlWriter xw) throws DiadException 
{
   Element hrslt = getHistoryData();
   outputGraph(hrslt,xw);
}



/********************************************************************************/
/*                                                                              */
/*      Set up appropriate query                                                */
/*                                                                              */
/********************************************************************************/

private Element getHistoryData()
{
   getAnalysis().waitForAnalysis();
   
   CommandArgs args = new CommandArgs("QTYPE","EXPRESSION",
         "CURRENT",current_value,
         "TOKEN",expression_name);
   args = addCommandArgs(args);
   
   String qxml = null;
   if (getNodeContext() != null) { 
      IvyXmlWriter xw = new IvyXmlWriter();
      getNodeContext().outputXml("EXPRESSION",xw);
      qxml = xw.toString();
      xw.close();
    }
   String sxml = getXmlForStack();
   if (qxml == null) qxml = sxml;
   else if (sxml != null) qxml += sxml; 
   
   Element rslt = getAnalysis().sendFaitMessage("FLOWQUERY",args,qxml);
   
   return rslt;
}




}       // end of class DianalysisExpressionHistory




/* end of DianalysisExpressionHistory.java */

