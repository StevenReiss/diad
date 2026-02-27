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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
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
private List<VarNode>   all_nodes;

private static AtomicInteger node_counter = new AtomicInteger(0);

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
   all_nodes = new ArrayList<>();
   all_nodes.add(start_node);
}



/********************************************************************************/
/*                                                                              */
/*      Processing methods                                                      */
/*                                                                              */
/********************************************************************************/

JSONObject process()
{
   addDependentNodes(start_node);
   
   JSONObject rslt = generateJson();
   
   return rslt;
}


/********************************************************************************/
/*                                                                              */
/*      Methods to find dependencies                                            */
/*                                                                              */
/********************************************************************************/

private void addDependentNodes(VarNode vn)
{
   long now = vn.getTime();
   long prev = -1;
   if (vn.getValue().hasChildren(now)) {
      for (Long when : vn.getValue().getAllTimeChanges(exec_trace)) { 
         if (when < now) {
            addDependentNodes(vn,when);
          }
       }
    }
   else {
      for (Long when : vn.getValue().getTimeChanges()) { 
         if (when <= now) prev = when;
         else if (when > now) break;
       }
      if (prev < 0) {
         prev = now-1;
       }
      else if (prev == 0) prev = now;
      addDependentNodes(vn,prev);
    }
}


private void addDependentNodes(VarNode vn,long prev)
{
   DiexecuteCall pctx = exec_trace.getContextForTime(prev); 
   IvyLog.logD("DIEXECUTE","DEPENDENT CONTEXT " + pctx);
   if (pctx == null) return;
   
   VarNode vn1 = new VarNode(VarNodeType.SET,pctx,prev,vn.getName(),vn.getValue());
   if (prev != vn.getTime()) {
      if (canAddNode(vn1)) {
         vn.addDependent(vn1);
         vn = vn1;
       }
    }
   
   String vnm = vn.getName();
   int idx = vnm.lastIndexOf("?");
   if (idx > 0) vnm = vnm.substring(idx+1);
   
   if (prev < pctx.getStartTime()) prev = pctx.getStartTime();
   int line = getLine(pctx,prev);
   IvyLog.logD("DIEXECUTE","DEPENDENT LINE " + line + " @ " + prev);
   if (line <= 0) return;
   
   Element dep = null;
   if (line == getLine(pctx,pctx.getStartTime())) {
      DiexecuteCall par = pctx.getParentCall();
      if (par != null) {
         int nline = getLine(par,prev);
         // handle call node
         dep = getCallDependencies(vnm,pctx,par,line,nline,prev);
         pctx = par;
         line = nline;
       }
    }
   else {
      dep = getVariableDependencies(vnm,pctx,line,prev);
    }
   
   List<VarNode> vns = findDependents(vn,dep,pctx,line,prev-1);
   if (vns == null || vns.isEmpty()) return;
   
   for (VarNode nvn : vns) {
      if (canAddNode(nvn)) {
         IvyLog.logD("DIEXECUTE","Add DEPENDNCY " + nvn);
         vn.addDependent(nvn);
         addDependentNodes(nvn);
       }
      else {
         IvyLog.logD("DIEXECUTE","Duplicate node " + all_nodes.size() + " " + nvn);
       }
    }
}



private boolean canAddNode(VarNode vn)
{
   if (all_nodes.size() >= 50) {
      return false;
    }
   if (duplicateNode(vn,start_node)) {
      return false;
    }
   all_nodes.add(vn);
   return true;
}



private boolean duplicateNode(VarNode vn,VarNode at)
{
   if (at.matches(vn)) return true;
   if (at.getDependents() != null) {
      for (VarNode next : at.getDependents()) {
         if (duplicateNode(vn,next)) return true;
       }
    }
   return false;
}

/********************************************************************************/
/*                                                                              */
/*      Ask seede to find dependencies for a particular statement               */
/*                                                                              */
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
   List<DiexecuteVarVal> comps = new ArrayList<>();
   DiexecuteVarVal thisv = ctx.getTraceVariable("this");
   if (thisv != null) comps.add(thisv);
   for (int i = 1; i < 10; ++i) {
      DiexecuteVarVal thisnv = ctx.getTraceVariable("this$" + i);
      if (thisnv == null) break;
      comps.add(thisnv);
    }
   
   boolean chng = true;
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
                  if (val1 != null) {
                     val1 = val1.dereference(exec_trace);
                     bvs.add(val1);
                     done.add(var);
                   }
                }
               break;
               
            case NONE :
            case CLASS :
            case INTERFACE :
            case ENUM :
            case CONSTRUCTOR :
            case PACKAGE :
            case ANNOTATION :
            case ANNOTATION_MEMBER :
               done.add(var);
               break;
               
            case METHOD :
               done.add(var);
               break;
               
            case LOCAL :
               done.add(var);
               IvyLog.logD("DIEXECUTE","LOCAL " + vnm + " " + vty);
               String lclnm = ctx.getVariableName(vnm,lno);
               if (lclnm == null) break;
               DiexecuteVarVal varval = ctx.getTraceVariable(lclnm);
               DiexecuteVarVal val = varval.getValueAtTime(exec_trace,when);
               if (val == null) break;
               boolean systyp = IvyXml.getAttrBool(var,"BINARY");
               if (!val.hasChildren(when) || systyp) { 
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
   if (ctx == null) return 0;
   
   DiexecuteVarVal lins = ctx.getLineNumbers();
   if (lins == null) return 0;
   
   return lins.getLineValue(time);
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

private JSONObject generateJson()
{
   Map<String,VarNode> nodemap = new TreeMap<>();
   
   findNodes(start_node,nodemap);
   
   JSONObject rslt = new JSONObject();
   JSONArray nodes = new JSONArray();
   for (VarNode vn : nodemap.values()) {
      JSONObject jo = vn.toJson();
      nodes.put(jo);
    }
   rslt.put("NODES",nodes);
   
   JSONArray edges = new JSONArray();
   for (VarNode vn : nodemap.values()) {
      if (vn.hasDependents()) {
         for (VarNode dep : vn.getDependents()) {
            JSONObject edge = new JSONObject();
            edge.put("SOURCE",vn.getId());
            edge.put("TARGET",dep.getId());
            edges.put(edge);
          }
       }
    }
   rslt.put("EDGES",edges);
   
   return rslt;
}


private void findNodes(VarNode vn,Map<String,VarNode> nodemap)
{
   if (nodemap.containsKey(vn.getId())) return;
   nodemap.put(vn.getId(),vn);
   if (vn.hasDependents()) {
      for (VarNode dep : vn.getDependents()) {
         findNodes(dep,nodemap);
       }
    }
}


/********************************************************************************/
/*                                                                              */
/*      Variable History Node                                                   */
/*                                                                              */
/********************************************************************************/

private static class VarNode {
   
   private DiexecuteCall in_context;
   private long at_time;
   private String var_name;
   private DiexecuteVarVal var_value;
   private List<VarNode> comes_from;
   private String other_data;
   private VarNodeType node_type;
   private String node_id;
   
   VarNode(VarNodeType typ,DiexecuteCall ctx,long at,String name,
         DiexecuteVarVal val) {
      node_type = typ;
      in_context = ctx;
      at_time = at;
      var_name = name;
      var_value = val;
      comes_from = null;
      other_data = null;
      node_id = "NODE_" + node_counter.incrementAndGet();
      IvyLog.logD("DIEXECUTE","Create DEPENDENCY " + ctx.getMethod() + " " + 
            var_name + " " + at + " " + val);
    }
   
   void addDependent(VarNode vn) {
      if (comes_from == null) comes_from = new ArrayList<>();
      if (!comes_from.contains(vn)) comes_from.add(vn);
    }
   
   void setOtherData(String data)       { other_data = data; }
   void setNodeType(VarNodeType vnt)    { node_type = vnt; }
   
   long getTime()                       { return at_time; }
   String getName()                     { return var_name; }
   DiexecuteVarVal getValue()           { return var_value; }
   String getId()                       { return node_id; }
   
   boolean hasDependents()              { return comes_from != null; }
   List<VarNode> getDependents()        { return comes_from; }
   
   boolean isReturn() {
      return var_name != null && var_name.endsWith("*RETURNS*");
    }
   
   boolean matches(VarNode vn) {
      if (var_name != null && !var_name.equals(vn.var_name)) return false;
      else if (var_name == null && vn.var_name != null) return false;
      if (at_time != vn.at_time) return false;
      if (in_context != vn.in_context) return false;
      if (node_type != vn.node_type) return false;
   // if (other_data != null && !other_data.equals(vn.other_data)) return false;
   // else if (other_data == null && vn.other_data != null) return false;
      return true;
    }
   
   JSONObject toJson() {
      JSONObject rslt = new JSONObject();
      
      rslt.put("ID",node_id);
      rslt.put("METHOD",in_context.getMethod());
      rslt.put("TIME",at_time);
      rslt.put("VARIABLE",var_name);
      rslt.put("VALUE",var_value.getStringValue(at_time));
      rslt.put("NODE_TYPE",node_type);
      if (isReturn()) rslt.put("IS_RETURN",true);
      if (other_data != null) {
         if (node_type == VarNodeType.STATEMENT) {
            rslt.put("STATEMENT",other_data);
          }
         else {
            rslt.put("OTHER",other_data);
          }
       }
      
      return rslt;
    }
   
   @Override public String toString() {
      return node_id + "(" + in_context.getMethod() + "@" + at_time + " " +
         var_name + " " + node_type + ")";
    }
   
}       // end of inner class VarNode


}       // end of class DiexecuteVarHistory




/* end of DiexecuteVarHistory.java */

