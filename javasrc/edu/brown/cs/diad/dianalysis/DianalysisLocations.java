/********************************************************************************/
/*                                                                              */
/*              DianalysisLocations.java                                        */
/*                                                                              */
/*      Handle fault localization using FAIT                                     */
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DianalysisLocations implements DianalysisConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DianalysisManager for_analysis;
private DiadSymptom for_symptom;
private DiadThread  for_thread;
 


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/
 
DianalysisLocations(DianalysisManager anal,DiadSymptom sym,DiadThread thrd)
{
   for_analysis = anal;
   for_symptom = sym;
   for_thread = thrd;
}



/********************************************************************************/
/*                                                                              */
/*      Find initial locations                                                  */
/*                                                                              */
/********************************************************************************/

Collection<DiadLocation> findInitialLocations()
{
   DianalysisHistory hq = setupHistory();
   List<DiadLocation> rslt = new ArrayList<>();
   
   if (hq == null) {
      IvyLog.logE("DIANALYSIS","No location history for " + for_symptom);
      return null;
    }
   
   Element xml = null;
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      hq.process(xw);
      xml = IvyXml.convertStringToXml(xw.toString());
    }
   catch (DiadException e) {
      IvyLog.logW("DIANALYSIS","Problem finding locations for problem: " + e);
      return null;
    }
   if (Thread.currentThread().isInterrupted()) return null;
   
   // USE DianalysisGrpah.getLocationResult
   DianalysisGraph dg = new DianalysisGraph(for_analysis);
   rslt = dg.getLocationResult(xml,for_symptom);
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Translate symptom into a location and variables                         */
/*                                                                              */
/********************************************************************************/

private DianalysisHistory setupHistory()
{
   DianalysisHistory hq = null;
   switch (for_symptom.getSymptomType()) {
      case ASSERTION :
         hq = new DianalysisAssertionHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case EXCEPTION :
      case LIBRARY_EXCEPTION :
         hq = new DianalysisExceptionHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case EXPRESSION :
         hq = new DianalysisExpressionHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case LOCATION :
         hq = new DianalysisLocationHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case NO_EXCEPTION :
         // need to create locations for exception not thrown
         break;
      case VARIABLE :
         hq = new DianalysisVariableHistory(for_analysis,
               for_symptom,for_thread);
         break;
      case OTHER :
         hq = new DianalysisOtherHistory(for_analysis,
               for_symptom,for_thread); 
         break;
      case NONE :
         break;
    }
  
   return hq;
}


}       // end of class DianalysisLocations




/* end of DianalysisLocations.java */

