/********************************************************************************/
/*                                                                              */
/*              DisourceFactory.java                                            */
/*                                                                              */
/*      Access to source files in AST form                                      */
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



package edu.brown.cs.diad.disource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public class DisourceManager implements DisourceConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private DisourceCompiler the_compiler;
private Map<File,String> project_map;
private String          default_project;
private String          workspace_name;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DisourceManager(DicontrolMain ctrl)
{
   diad_control = ctrl;
   project_map = new HashMap<>();
   default_project = null;
   workspace_name = null;
   the_compiler = new DisourceCompiler(ctrl,this);
   
   buildProjectMap();
}


/********************************************************************************/
/*                                                                              */
/*      Primary access methods                                                  */
/*                                                                              */
/********************************************************************************/

public ASTNode getSourceNode(String proj,File f,int offset,int line,
      boolean resolve,boolean stmt)
{
   if (proj == null && f != null) proj = getProjectForFile(f);
   
   ASTNode n = the_compiler.getSourceNode(proj,f,offset,line,resolve);
   
   if (stmt) n = DisourceCompiler.getStatementOfNode(n);
   
   return n;
}


public String getWorksapceShortName()
{
   String s = workspace_name;
   if (s == null) return "";
   int idx = s.lastIndexOf("/");
   if (idx > 0) s = s.substring(idx+1);
   return s;
}


public void getExpressionsInStatement(IvyXmlWriter xw,String proj,String file,int offset,int line)
{
   File f1 = null;
   if (file != null) f1 = new File(file);
   ASTNode src = getSourceNode(proj,f1,offset,line,false,true);
   DisourceExpressionQuery deq = new DisourceExpressionQuery(diad_control,src);
   deq.process(xw);  
}




public void noteFileEdited(File f)
{
   // this can wait until file is saved
}


public void noteFileSaved(File f)
{
   the_compiler.noteFileSaved(f);
}

 
/********************************************************************************/
/*                                                                              */
/*      Handle file-project associations                                        */
/*                                                                              */
/********************************************************************************/

public String getProjectForFile(File f)
{
   if (default_project == null) {
      buildProjectMap();
    }
   
   if (f == null) return default_project;
   
   String p = project_map.get(f);
   if (p == null) {
      File f1 = IvyFile.getCanonical(f);
      p = project_map.get(f1);
    }
   if (p == null) {
      p = default_project;
    }
   
   return p;
}


public File findProjectFile(String fnm)
{
   File f1 = new File(fnm);
   if (f1.isAbsolute()) return f1;
   
   if (default_project == null) {
      buildProjectMap();
    }
   
   f1 = null;
   for (File pf : project_map.keySet()) {
      String pnm = pf.getName();
      if (pnm.equals(fnm)) {
         f1 = pf;
         break;
       }
    }
   
   return f1;
}


private void buildProjectMap()
{
   project_map = new HashMap<>();
   default_project = null;
   
   IvyLog.logD("DISOURCE","Build project map");
   
   Element xml = diad_control.sendBubblesMessage("PROJECTS",null,null);
   for (Element p : IvyXml.children(xml,"PROJECT")) {
      if (workspace_name == null) {
         workspace_name = IvyXml.getAttrString(p,"WORKSPACE");
         if (workspace_name != null) {
            File wsf = new File(workspace_name);
            diad_control.setWorkspace(wsf);
          }
       }
      String nm = IvyXml.getAttrString(p,"NAME");
      CommandArgs args = new CommandArgs("PROJECT",nm,"FILES",true);
      Element pxml = diad_control.sendBubblesMessage("OPENPROJECT",args,null);
      Element rxml = IvyXml.getChild(pxml,"PROJECT");
      Element files = IvyXml.getChild(rxml,"FILES");
      for (Element fxml : IvyXml.children(files,"FILE")) {
         if (IvyXml.getAttrBool(fxml,"SOURCE")) {
            String fnm = IvyXml.getAttrString(fxml,"PATH");
            File f = new File(fnm);
            project_map.putIfAbsent(f,nm);
            File f1 = IvyFile.getCanonical(f);
            if (f1 != f) project_map.putIfAbsent(f,nm);
            if (default_project == null) {
               IvyLog.logD("DISOURCE","Set default project " + nm);
               default_project = nm;
             }
          }
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Handle queries                                                          */
/*                                                                              */
/********************************************************************************/

public JSONArray findReferences(String name)
{
   JSONArray rslt = new JSONArray();
   
   Element xml1 = findClass(name,true);
   Element xml2 = null;
   String typ = null;
   if (isMatch(xml1)) {
      typ = "TYPE";
      xml2 = findClass(name,false);
    }
   else {
      xml1 = findMethod(name,true);
      if (isMatch(xml1)) {
         typ = "METHOD";
         xml2 = findMethod(name,false);
       }
      else {
         xml1 = findField(name,true);
         if (isMatch(xml1)) {
            typ = "FIELD";
            xml2 = findField(name,false);
          }
       }
    }
   
   for (Element me: IvyXml.children(xml1,"MATCH")) {
     JSONObject jo = outputMatch(me,typ,true);
     if (jo != null) rslt.put(jo);
    }
   for (Element me: IvyXml.children(xml2,"MATCH")) {
      JSONObject jo = outputMatch(me,typ,false);
      if (jo != null) rslt.put(jo);
    }
   
   return rslt;
}
   
   
private JSONObject outputMatch(Element me,String typ,boolean def)
{
   Element mi = IvyXml.getChild(me,"ITEM");
   String intyp = IvyXml.getAttrString(mi,"TYPE");
   intyp = getReturnType(intyp);
   if (intyp == null) return null;
   String usrc = IvyXml.getAttrString(mi,"SOURCE");
   if (usrc == null || !usrc.equals("USERSOURCE")) return null;
   
   String fnm = IvyXml.getTextElement(me,"FILE");
   if (fnm == null) return null;;
   File fil = new File(fnm);
   int offset = IvyXml.getAttrInt(me,"STARTOFFSET");
   String pnm = IvyXml.getAttrString(me,"PROJECT");
   if (pnm == null) pnm = IvyXml.getAttrString(mi,"PROJECT");
   ASTNode ast = getSourceNode(pnm,fil,offset,-1,false,false);
   CompilationUnit cu = (CompilationUnit) ast.getRoot();
   int line = cu.getLineNumber(offset);
   String inside = IvyXml.getAttrString(mi,"QNAME");
   if (inside == null) inside = IvyXml.getAttrString(mi,"NAME");
   
   JSONObject jo = new JSONObject();
   jo.put("FILE",fnm);
   jo.put("LINE",line);
   jo.put("TYPE",typ);
   jo.put("INSIDE",inside);
   jo.put("INSIDETYPE",intyp);
   jo.put("DEFINITION",def);
   
   return jo;
}



private boolean isMatch(Element xml)
{
   if (xml == null) return false;
   if (IvyXml.getChild(xml,"MATCH") == null) return false;
   return true;
}



private Element findClass(String name,boolean def)
{
   CommandArgs args = new CommandArgs("PATTERN",name,
         "DEFS",def,"REFS",!def,"FOR","TYPE");
   Element pr = diad_control.sendBubblesMessage("PATTERNSEARCH",args,null);
   
   return pr;
}


public Element findMethod(String name0,boolean def)
{
   String name = name0;
   if (name == null) return null;
   
   // fix constructors
   String what = "METHOD";
   if (name.contains(".<init>")) {
      name = name.replace(".<init>","");
      what = "CONSTRUCTOR";
    }
   // check for X.X as constructor ???
   
   CommandArgs args = new CommandArgs("PATTERN",name,
         "DEFS",def,"REFS",!def,"FOR",what,"SYSTEM",false,"IMPLS",false);
   Element xml = diad_control.sendBubblesMessage("PATTERNSEARCH",args,null);
   
   return xml;
}


private Element findField(String name,boolean def)
{
   CommandArgs args = new CommandArgs("PATTERN",name,
         "DEFS",def,"REFS",!def,"FOR","FIELD","SYSTEM",false);
   Element xml = diad_control.sendBubblesMessage("PATTERNSEARCH",args,null);
   
   return xml;
}




private String getReturnType(String typ)
{
   if (typ == null) return null;
   
   String rslt = null;
   
   switch (typ) {
      case "Class" :
      case "Throwable" :
      case "Exception" :
      case "Interface" :
      case "Enum" :
         rslt = "TYPE";
         break;
      case "Function" :
      case "Method" :
      case "Constructor" :
      case "StaticInitializer" :
         rslt = "METHOD";
         break;
      case "Field" :
      case "EnumConstant" :
      case "Variable" :
         rslt = "FIELD";
         break;
    }
   
   return rslt;
}


}       // end of class DisourceFactory




/* end of DisourceFactory.java */

