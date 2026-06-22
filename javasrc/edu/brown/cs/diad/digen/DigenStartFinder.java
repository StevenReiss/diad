/********************************************************************************/
/*                                                                              */
/*              DigenStartFinder.java                                           */
/*                                                                              */
/*      Logic to identify starting frame for test generation                    */
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

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadCandidate;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.diexecute.DiexecuteManager;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSymbol;
import edu.brown.cs.ivy.jcomp.JcompType;
import edu.brown.cs.ivy.jcomp.JcompTyper;

class DigenStartFinder implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DigenTestCreator        test_creator;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenStartFinder(DigenTestCreator dtc)
{
   test_creator = dtc;
}


/********************************************************************************/
/*                                                                              */
/*      Find the starting point                                                 */
/*                                                                              */
/********************************************************************************/

DiadStackFrame findStartingPoint(String fid)
{
   DiadCandidate cand = test_creator.getCandidate(); 
   DicontrolMain ctrl = test_creator.getManager().getDiad();
   
   // first find the frame containing potential errors
   // the actual starting point should be this or above this
   DiexecuteManager exec = ctrl.getExecuteManager(); 
   DiadStackFrame frame0 = exec.getStartingFrame(cand.getSymptom(),
         cand.getThread(),cand.getLocations());
   
   DiadThread thrd = cand.getThread();
   DiadStack stk = thrd.getStack();
   DisourceManager srcm = ctrl.getSourceManager();
   
   if (fid != null) {
      for (DiadStackFrame frm : stk.getFrames()) {
         if (frm.getFrameId().equals(fid)) return frm;
       }
    }
   
   boolean fnd = true;          // set to false to only use frames above DIAD's
   Map<DiadStackFrame,Double> scores = new HashMap<>();
   double score4 = 1.0;
   double depth = 1.0/stk.getFrames().size();
   for (DiadStackFrame bsf : stk.getFrames()) {
      if (bsf.getFrameId().equals(frame0.getFrameId())) fnd = true;
      if (fnd) {
         if (!bsf.isUserFrame()) continue;
         File f = bsf.getSourceFile();
         ASTNode stmt = srcm.getSourceNode(null,f,-1,
               bsf.getLineNumber(),true,true);
         ASTNode mthd = null;
         for (ASTNode p = stmt; p != null; p = p.getParent()) {
            if (p instanceof MethodDeclaration) {
               mthd = p;
               break;
             }
          }
         if (mthd != null) {
            JcompSymbol js = JcompAst.getDefinition(mthd);
            JcompTyper typer = JcompAst.getTyper(mthd);
            double score = 0;
            double score1 = 0;
            score = getProtection(js);
            if (js.isStatic()) score1 = 1.0;
            else score1 = getConstructorProtection(typer,js);
            int narg = js.getType().getComponents().size();
            double score2 = 1.0 - narg/10;
            if (score2 < 0) score2 = 0;
            double score3 = 1.0;
            for (JcompType ajt : js.getType().getComponents()) {
               score3 = Math.min(score3,getConstructorProtection(typer,ajt));
             }
            double sco = score * score1 * score2 * score3 * score4;
            if (sco > 0) scores.put(bsf,sco);
          }
       }
      score4 -= depth;
    }
   
   handleFaultLocations(scores);
   
   DiadStackFrame best = null;
   double bscore = 0;
   for (Map.Entry<DiadStackFrame,Double> ent : scores.entrySet()) {
      if (ent.getValue() > bscore) {
         best = ent.getKey();
         bscore = ent.getValue();
       }
    }
   
   return best;
}



private double getProtection(JcompSymbol js)
{
   if (js == null) return 0;
   
   double score = 0;
   if (js != null) {
      if (js.isPublic()) score = 1.0;
      else if (js.isPrivate()) score = 0.1;
      else if (js.isProtected()) score = 0.3;
      else score = 0.8;
    } 
   
   return score;
}



private double getConstructorProtection(JcompTyper typer,JcompSymbol js)
{
   if (js.isConstructorSymbol()) return getProtection(js);
   JcompType jt = js.getClassType();
   double s = getConstructorProtection(typer,jt);
   
   return s;
}


private double getConstructorProtection(JcompTyper typer,JcompType jt)
{
   double score = 0;
   JcompSymbol cjs = jt.getDefinition();
   if (cjs == null) return 1.0;
   
   double score1 = getProtection(cjs);
   int ct = 0;
   for (JcompSymbol js1 : jt.getDefinedMethods(typer)) {
      if (js1.isConstructorSymbol()) {
         ++ct;
         double s = getProtection(js1);
         if (s > score) score = s;
       }
    }
   if (ct == 0) score = 1.0;
   score = Math.min(score,score1);
   
   if (jt.getOuterType() != null && !cjs.isStatic()) {
      double s1 = getConstructorProtection(typer,jt.getOuterType());
      s1 = s1 * 0.5;            // handle nested classes by trying to avoid
      score = Math.min(score,s1);
    }
   
   return score;
}



private void handleFaultLocations(Map<DiadStackFrame,Double> scores)
{
   DiadCandidate dc = test_creator.getCandidate();
   DiadThread dt = dc.getThread();
   
   // first find all frames used by locations
   Set<DiadStackFrame> frames = new HashSet<>();
   for (DiadLocation dl : dc.getLocations()) {
      String m = dl.getMethod();
      for (DiadStackFrame nf : dt.getStack().getFrames()) {
         String m1 = nf.getClassName() + "." + nf.getMethodName();
         if (m1.equals(m)) {
            frames.add(nf);
            break;
          }
       }
    }
   
   // next find frame that includes all locations possible
   DiadStackFrame top = null;
   for (DiadStackFrame sf : dt.getStack().getFrames()) {
      if (frames.contains(sf)) top = sf;
    }
   if (top == null) return;
   
   // finally, decrement likelihood of frames that don't include locations
   boolean fnd = false;
   for (DiadStackFrame sf : dt.getStack().getFrames()) {
      if (sf == top) fnd = true;
      if (!fnd) {
         Double dv = scores.get(sf);
         if (dv != null) scores.put(sf,dv * 0.25);
       }
    }
}



}       // end of class DigenStartFinder




/* end of DigenStartFinder.java */

