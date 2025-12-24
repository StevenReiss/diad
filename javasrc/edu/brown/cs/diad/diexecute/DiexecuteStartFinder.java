/********************************************************************************/
/*                                                                              */
/*              DiexecuteStartFinder.java                                       */
/*                                                                              */
/*      Find appropriate starting frame for SEEDE execution                     */
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

import java.io.File;
import java.util.Collection;

import org.eclipse.jdt.core.dom.ASTNode;

import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.disource.DisourceManager;

class DiexecuteStartFinder implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiexecuteManager exec_manager;
private DiadThread      for_thread;
private Collection<DiadLocation> fault_points;
private int max_up;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteStartFinder(DiexecuteManager mgr,DiadThread thrd,
        Collection<DiadLocation> faults)
{
   exec_manager = mgr;
   for_thread = thrd;
   fault_points = faults;
   max_up = mgr.getDiadControl().getProperty("Diad.max,up",20);
}



/********************************************************************************/
/*                                                                              */
/*      Processing methods                                                      */
/*                                                                              */
/********************************************************************************/

DiadStackFrame findStartingFrame()
{
   DiadStackFrame startframe = for_thread.getStack().getUserFrame();
   
   if (fault_points != null) {
      for (DiadLocation loc : fault_points) {
         startframe = updateFrameForLocation(startframe,loc);
       }
    }
   
   startframe = findValidStart(startframe);
   
   return startframe;
}


/********************************************************************************/
/*                                                                              */
/*      Update frame for a possible fault location                              */
/*                                                                              */
/********************************************************************************/

private DiadStackFrame updateFrameForLocation(DiadStackFrame frm,DiadLocation loc)
{
   String m = loc.getMethod();
   boolean fnd = false;
   DiadStackFrame rslt = frm;
   for (DiadStackFrame nf : for_thread.getStack().getFrames()) {
      if (nf == frm) fnd = true;
      else if (fnd) {
         String m1 = nf.getClassName() + "." + nf.getMethodName();
         if (m1.equals(m)) {
            rslt = nf;
          }
       }
    }
   
   return rslt;
}


/********************************************************************************/
/*										*/
/*	Find a frame on stack for doing execution from				*/
/*										*/
/********************************************************************************/

private DiadStackFrame findValidStart(DiadStackFrame frm)
{
   boolean fnd = false;
   DiadStackFrame rslt = frm;
   DiadStackFrame prior = null;
   DisourceManager srcmgr = exec_manager.getDiadControl().getSourceManager();
   int ct = 0;
   for (DiadStackFrame bf : for_thread.getStack().getFrames()) {
      if (bf == frm) fnd = true;
      else if (fnd) {
	 File f = bf.getSourceFile();
	 if (f != null && f.exists() && f.canRead()) {
	    String proj = null;
	    if (f != null) proj = srcmgr.getProjectForFile(f);
	    ASTNode n = srcmgr.getSourceNode(proj,f,-1,
                  bf.getLineNumber(),true,false);
	    while (n != null) {
	       if (n.getNodeType() == ASTNode.METHOD_DECLARATION) break;
	       n = n.getParent();
	     }
	    // might want to check if method of n is not private
	    if (n != null) {
	       ++ct;
	       prior = bf;
	     }
	    else f = null;
	  }
	 else f = null;
	 if (f == null) {
	    break;
	  }
       }
    }
   
   if (prior != null && ct < max_up) {
      rslt = prior;
    }
   
   return rslt;
}


}       // end of class DiexecuteStartFinder




/* end of DiexecuteStartFinder.java */

