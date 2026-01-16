/********************************************************************************/
/*                                                                              */
/*              DiexecuteVarHistory.java                                        */
/*                                                                              */
/*      Compute variable history from trace with help from SEEDE                */
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompSymbolKind;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteVarHistory implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiexecuteTrace  exec_trace;
private long            start_time; 
private VarNode         start_node;

enum VarNodeType {
   VALUE, SET, STATEMENT, PARAMETER, CALL
}


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteVarHistory(DiexecuteTrace trace,DiexecuteCall ctx,
      DiexecuteVarVal var,String name,long when)
{
   exec_trace = trace;
   start_time = when;
   
   DiexecuteVarVal val = var.getValueAtTime(exec_trace,when);
   
   start_node = new VarNode(VarNodeType.VALUE,ctx,start_time,
         name,val);
}



/********************************************************************************/
/*                                                                              */
/*      Processing methods                                                      */
/*                                                                              */
/********************************************************************************/

JSONObject process()
{
   addDependentNodes(start_node);
   
   // generate a JSON object for the start node, referencing all other nodes
   // nodes should only be included once
   
   return null;
}


/********************************************************************************/
/*										*/
/*	Methods to find dependencies						*/
/*										*/
/********************************************************************************/

private void addDependentNodes(VarNode vn)
{
   long now = vn.getTime();
   long prev = -1;
   for (Long when : vn.getValue().getTimeChanges()) {
      if (when <= now) prev = when;
      else if (when > now) break;
    }
   if (prev <= 0) {
      if (vn.isReturn()) prev = now-1;
      else return;
    }
   
   DiexecuteCall pctx = exec_trace.getContextForTime(prev+1); 
   IvyLog.logD("DIEXECUTE","DEPENDENT CONTEXT " + pctx);
   if (pctx == null) return;
   
   VarNode vn1 = new VarNode(VarNodeType.SET,pctx,prev,vn.getName(),vn.getValue());
   if (prev != vn.getTime()) {
      vn.addDependent(vn1);
      vn = vn1;
    }
   
   String vnm = vn.getName();
   int idx = vnm.lastIndexOf("?");
   if (idx > 0) vnm = vnm.substring(idx+1);
   
   int line = getLine(pctx,prev);
   IvyLog.logD("DIEXECUTE","DEPENDENT LINE " + line);
   if (line <= 0) return;
   
   Element dep = null;
   if (line == getLine(pctx,1)) {
      DiexecuteCall par = pctx.getParentCall();
      int nline = getLine(par,prev);
      // handle call node
      dep = getCallDependencies(vnm,pctx,par,line,nline,prev);
      pctx = par;
      line = nline;
    }
   else {
      dep = getVariableDependencies(vnm,pctx,line,prev);
    }
   
   List<VarNode> vns = findDependents(vn,dep,pctx,line,prev-1);
   if (vns == null) return;
   
   for (VarNode nvn : vns) {
      vn.addDependent(nvn);
      addDependentNodes(nvn);
    }
}


/********************************************************************************/
/*										*/
/*	Ask seede to find dependencies for a particular statement		*/
/*										*/
/********************************************************************************/

List<VarNode> findDependents(VarNode vnorig,Element dep,DiexecuteCall ctx,int lno,long when)
{
   if (dep == null) return null;
   
   VarNodeType typ = IvyXml.getAttrEnum(dep,"TYPE",VarNodeType.VALUE);
   String other = null;
   switch (typ) {
      case STATEMENT :
	 other = IvyXml.getTextElement(dep,"BODY");
	 break;
      case PARAMETER :
	 other = IvyXml.getTextElement(dep,"BODY");
	 int idx1 = other.indexOf("(");
	 if (idx1 > 0) other = other.substring(0,idx1);
	 break;
      case CALL :
	 other = IvyXml.getTextElement(dep,"INNERMETHOD");
	 break;
      case SET :
      case VALUE :
	 break;
    }
   vnorig.setNodeType(typ);
   if (other != null) vnorig.setOtherData(other);
   
   List<VarNode> deps = new ArrayList<>();
   Set<Element> done = new HashSet<>(); 
   boolean chng = true;
   List<DiexecuteVarVal> comps = new ArrayList<>();
   DiexecuteVarVal thisv = ctx.getTraceVariable("this");
   if (thisv != null) comps.add(thisv);
   for (int i = 1; i < 10; ++i) {
      DiexecuteVarVal thisnv = ctx.getTraceVariable("this$" + i);
      if (thisnv == null) break;
      comps.add(thisnv);
    }
   
   while (chng) {
      chng = false;
      for (Element var : IvyXml.children(dep,"VAR")) {
	 if (done.contains(var)) continue;
         
	 String vnm = IvyXml.getAttrString(var,"NAME");
	 String vty = IvyXml.getAttrString(var,"TYPE");
	 JcompSymbolKind knd = IvyXml.getAttrEnum(var,"KIND",JcompSymbolKind.NONE);
	 List<DiexecuteVarVal> bvs = new ArrayList<>();
	 switch (knd) {
	    case FIELD :
	       IvyLog.logD("DIEXECUTE","FIELD " + vnm + " " + vty);
	       for (DiexecuteVarVal compv : comps) {
                  DiexecuteVarVal val1 = compv.getChild(vnm,when); 
                  val1 = val1.dereference(exec_trace);
                  if (val1 != null) {
                     bvs.add(val1);
		     done.add(var);
                   }
		}
	       break;
               
	    case NONE :
	    case CLASS :
	    case INTERFACE :
	    case ENUM :
	    case METHOD :
	    case CONSTRUCTOR :
	    case PACKAGE :
	    case ANNOTATION :
	    case ANNOTATION_MEMBER :
	       done.add(var);
	       break;
               
	    case LOCAL :
	       done.add(var);
	       String lclnm = ctx.getVariableName(vnm,lno);
	       if (lclnm == null) break;
               DiexecuteVarVal varval = ctx.getTraceVariable(lclnm);
               DiexecuteVarVal val = varval.getValueAtTime(exec_trace,when);
	       if (val == null) break;
	       if (!val.hasChildren(when)) { 
		  bvs.add(varval);
		}
	       else {
		  comps.add(varval);
		  chng = true;
		}
	       break;
	  }
         
	 if (bvs.size() > 0) {
	    for (DiexecuteVarVal bv : bvs) {
               DiexecuteVarVal val = bv.getValueAtTime(exec_trace,when);
	       VarNode vn = new VarNode(VarNodeType.VALUE,ctx,
                     when,vnm,val);
	       deps.add(vn);
	     }
	  }
       }
    }
   
   
   return deps;
}



private Element getVariableDependencies(String name,DiexecuteCall ctx,int lno,long when)
{
   if (when == 0) return null;
   
   CommandArgs args = new CommandArgs("FILE",ctx.getFile(),
	 "LINE",lno,
	 "TIME",when,
	 "CONTEXT",ctx.getContextId(),
	 "VARIABLE",name);
   Element rslt = exec_trace.getManager().sendSeedeMessage(exec_trace.getSessionId(),
         "VARHISTORY",args,null);
   if (rslt == null) return null;
   if (IvyXml.getChild(rslt,"ERROR") != null) return null;
   
   Element dep = IvyXml.getChild(rslt,"DEPEND");
   
   return dep;
}


private Element getCallDependencies(String name,DiexecuteCall ctx,DiexecuteCall cctx,
      int lno,int clno,long when)
{
   // need the file and line for the called context
   
   CommandArgs args = new CommandArgs("FILE",ctx.getFile(),
	 "LINE",lno,
	 "TIME",when,
	 "CONTEXT",ctx.getContextId(),
	 "CALLEDCONTEXT",cctx.getContextId(),
	 "CALLEDFILE",cctx.getFile(),
	 "CALLEDMETHOD",cctx.getMethod(),
	 "CALLEDLINE",clno,
	 "VARIABLE",name);
   
   Element rslt = exec_trace.getManager().sendSeedeMessage(exec_trace.getSessionId(),
         "VARHISTORY",args,null);
   if (rslt == null) return null;
   if (IvyXml.getChild(rslt,"ERROR") != null) return null;
   
   Element dep = IvyXml.getChild(rslt,"DEPEND");
   
   return dep;
}


private int getLine(DiexecuteCall ctx,long time)
{
   DiexecuteVarVal lins = ctx.getLineNumbers();
   if (lins == null) return 0;
   
   return lins.getLineValue(time);
}



/********************************************************************************/
/*										*/
/*	Variable History Node							*/
/*										*/
/********************************************************************************/

private static class VarNode {
   
   private DiexecuteCall in_context;
   private long at_time;
   private String var_name;
   private DiexecuteVarVal var_value;
   private List<VarNode> comes_from;
   private String other_data;
   private VarNodeType node_type;
   
   VarNode(VarNodeType typ,DiexecuteCall ctx,long at,String name,
         DiexecuteVarVal val) {
      node_type = typ;
      in_context = ctx;
      at_time = at;
      var_name = name;
      var_value = val;
      comes_from = null;
      other_data = null;
      IvyLog.logD("DIEXECUTE","Create DEPENDENCY " + ctx.getMethod() + " " + 
            var_name + " " + at + " " + val);
    }
   
   void addDependent(VarNode vn) {
      if (comes_from == null) comes_from = new ArrayList<>();
      if (!comes_from.contains(vn)) comes_from.add(vn);
    }
   
   void setOtherData(String data)	{ other_data = data; }
   void setNodeType(VarNodeType vnt)	{ node_type = vnt; }
   
   long getTime()			{ return at_time; }
   String getName()			{ return var_name; }
   DiexecuteVarVal getValue()		{ return var_value; }
   DiexecuteCall getContext()	        { return in_context; }
   String getOtherData()		{ return other_data; }
   VarNodeType getNodeType()		{ return node_type; }
   
   List<VarNode> getDependents()	{ return comes_from; }
   
   boolean isReturn() {
      return var_name != null && var_name.endsWith("*RETURNS*");
    }
   
}	// end of inner class VarNode


}       // end of class DiexecuteVarHistory




/* end of DiexecuteVarHistory.java */

