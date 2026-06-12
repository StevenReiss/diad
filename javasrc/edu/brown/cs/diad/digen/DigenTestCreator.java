/********************************************************************************/
/*                                                                              */
/*              DigenTestCreator.java                                           */
/*                                                                              */
/*      Thread to generate a test for a candidate                               */
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import edu.brown.cs.diad.dicore.DiadCandidate;
import edu.brown.cs.diad.dicore.DiadExecution;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceCall;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVarVal;
import edu.brown.cs.diad.diexecute.DiexecuteManager;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSymbol;
import edu.brown.cs.ivy.jcomp.JcompType;
import edu.brown.cs.ivy.jcomp.JcompTyper;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DigenTestCreator implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiadCandidate for_candidate;
private DigenManager digen_manager;
private IvyXmlWriter xml_writer;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenTestCreator(DigenManager dm,DiadCandidate dc,IvyXmlWriter xw)
{
   digen_manager = dm;
   for_candidate = dc;
   xml_writer = xw;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DiadCandidate getCandidate()                    { return for_candidate; }

DigenManager getManager()                       { return digen_manager; }



/********************************************************************************/
/*                                                                              */
/*      Main processing method                                                  */
/*                                                                              */
/********************************************************************************/

void process()
{
   if (for_candidate.getSymptom() == null) {
      xml_writer.field("STATUS","NOSYMPTOM");
    }
   else {
      DigenTestCase test = null; 
      try {
         test = createTestCase();
       }
      catch (Throwable t) {
         IvyLog.logE("DIGEN","Problem creating test case",t);
       }
      IvyLog.logD("PICOT","TEST CREATION RESULT: " + test);
      if (test == null) {
         xml_writer.field("STATUS","FAIL");
       }
      else {
         xml_writer.field("STATUS",test.getStatus());
         xml_writer.begin("TESTCASE");
         // output test case to xml_writer
         xml_writer.end("TESTCASE");
       }
    }
}


/********************************************************************************/
/*                                                                              */
/*      Creating a test case                                                    */
/*                                                                              */
/********************************************************************************/

DigenTestCase createTestCase()
{
   // First find an appropriate starting frame
   DigenStartFinder fndr = new DigenStartFinder(this); 
   DiadStackFrame frame = fndr.findStartingPoint(); 
   if (frame == null) return null;
   IvyLog.logD("DIGEN","Found starting frame " + frame.getMethodName());
   
   // Next create a SEEDE execution for that frame
   DiexecuteManager execmgr = digen_manager.getDiad().getExecuteManager();
   DiadExecution exec = execmgr.createBaseExecution(for_candidate.getSymptom(),
         for_candidate.getThread(),frame);
   if (exec == null) return null;
   DiadTrace trace = exec.getExecutionTrace();
   if (trace == null) return null;
   IvyLog.logD("DIGEN","Create execution " + trace.getSymptomTime());
   
   // Next get the starting information
   DigenTestCase rslt = buildCall(trace);
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Build initial call for test                                             */
/*                                                                              */
/********************************************************************************/

private DigenTestCase buildCall(DiadTrace trace)
{
   DiadTraceCall call = trace.getRootContext();
   IvyLog.logD("DIGEN","Start building " + call.getMethod());
   
   long start = call.getStartTime();
   
   MethodDeclaration md = getMethod(call);
   if (md == null) return null;
   
   JcompTyper typer = JcompAst.getTyper(md); 
   
   DigenValueBuilder dvb = new DigenValueBuilder(digen_manager,trace,start,typer);
   
   DigenValueContext runctx = buildCall(trace,call,dvb);
   
   return null;
}



private MethodDeclaration getMethod(DiadTraceCall rtc)
{
   File f = rtc.getFile();
   DiadTraceVarVal lns = rtc.getLineNumbers();
   int lno = lns.getLineValue(rtc.getStartTime());
   DisourceManager srcm = digen_manager.getDiad().getSourceManager();
   ASTNode n0 =srcm.getSourceNode(null,f,-1,lno,true,false);
   MethodDeclaration md = null;
   for (ASTNode p = n0; p != null; p = p.getParent()) {
      if (p instanceof MethodDeclaration) {
         md = (MethodDeclaration) p;
         break;
       }
    }
   
   return md;
}



/********************************************************************************/
/*                                                                              */
/*      Build code for a call                                                   */
/*                                                                              */
/********************************************************************************/

private DigenValueContext buildCall(DiadTrace trace,DiadTraceCall call,
      DigenValueBuilder builder) 
{
   MethodDeclaration md = getMethod(call);
   if (md == null) return null;
   JcompSymbol js = JcompAst.getDefinition(md);
   if (js == null) return null;
   
   DigenCodeFragment thisfrag = null;
   List<DigenCodeFragment> args  = new ArrayList<>();
   
   if (!js.isStatic()) {
      DiadTraceVarVal thisvar = call.getTraceVariables().get("this");
      builder.computeVarValue(thisvar);
      DiadTraceVarVal this0var = call.getTraceVariables().get("this$0");
      builder.computeVarValue(this0var);
    }
   // handle this$0 if needed
   for (Object o : md.parameters()) {
      SingleVariableDeclaration svd = (SingleVariableDeclaration) o;
      String nm = svd.getName().getIdentifier();
      DiadTraceVarVal pvar = call.getTraceVariables().get(nm);
      builder.computeValue(pvar);
    }
   Map<String,DiadTraceVarVal> glbls = trace.getGlobalVariables();
   for (String vnm : glbls.keySet()) {
      int idx = vnm.lastIndexOf(".");
      if (idx < 0) continue;
      String cnm = vnm.substring(0,idx);
      JcompType jty = builder.getJcompTyper().findType(cnm);
      if (jty == null) continue;
      if (jty.isCompiledType()) {
         builder.computeValue(glbls.get(vnm));
       }
    }
   
   DigenValueContext initctx = builder.getInitializationContext();
   if (initctx == null) return null;
   
   if (!js.isStatic()) {
      DiadTraceVarVal thisvar = call.getTraceVariables().get("this");
      thisfrag = builder.buildSimpleValue(thisvar);
      if (thisfrag == null) return null;
    }
   else {
      String cnm = js.getClassType().getName();
      thisfrag = new DigenCodeFragment(cnm);
    }
   // handle this$0 if needed
   for (Object o : md.parameters()) {
      SingleVariableDeclaration svd = (SingleVariableDeclaration) o;
      String nm = svd.getName().getIdentifier();
      DiadTraceVarVal pvar = call.getTraceVariables().get(nm);
      DigenCodeFragment arg = builder.buildSimpleValue(pvar);
      if (arg == null) return null;
      args.add(arg);
    }
   
// look for static fields accessed by code in the trace
   
// initctx = pvb.getInitializationContext();
   
// should clean up initcode by removing unneeded items
   
   String callcode = "";
   if (!js.getType().getBaseType().isVoidType()) {
      callcode = js.getType().getBaseType().getName() + " result = ";
    }
   if (thisfrag != null) callcode += thisfrag.getCode() + ".";
   callcode += md.getName() + "(";
   for (int i = 0; i < args.size(); ++i) {
      if (i > 0) callcode += ",";
      callcode += args.get(i).getCode();
    }
   callcode += ");\n";
   
   DigenCodeFragment callfrag = new DigenCodeFragment(callcode);
   DigenValueContext runctx = new DigenValueContext(initctx,callfrag); 
   
   return runctx;
}



}       // end of class DigenTestCreator




/* end of DigenTestCreator.java */

