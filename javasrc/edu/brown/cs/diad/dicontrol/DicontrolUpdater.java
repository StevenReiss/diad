/********************************************************************************/
/*                                                                              */
/*              DicontrolUpdater.java                                           */
/*                                                                              */
/*      Handle sending status and update messages for a candidate               */
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

import edu.brown.cs.diad.dicore.DiadConstants;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXmlWriter;
import edu.brown.cs.diad.dicore.DiadCandidateCallback;

class DicontrolUpdater implements DiadConstants, DiadCandidateCallback
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolCandidate      for_candidate;
private DicontrolMain           diad_control;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolUpdater(DicontrolMain ctrl,DicontrolCandidate cand)
{
   diad_control = ctrl;
   for_candidate = cand;
}



/********************************************************************************/
/*                                                                              */
/*      Handle events                                                           */
/*                                                                              */
/********************************************************************************/

@Override public void stateChanged()
{
   CommandArgs args = new CommandArgs("ID",for_candidate.getId(),
         "STATE",for_candidate.getState());
   String cnts = null;
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      for_candidate.outputXml(xw);
      cnts = xw.toString();
    }
   
   diad_control.getMessageServer().sendDiadMessage("UPDATE",args,cnts);
}


}       // end of class DicontrolUpdater




/* end of DicontrolUpdater.java */

