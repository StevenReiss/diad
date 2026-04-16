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
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.ivy.file.IvyFile;
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



/********************************************************************************/
/*                                                                              */
/*      Handle file-project associations                                        */
/*                                                                              */
/********************************************************************************/

public String getProjectForFile(File f)
{
   if (f == null) return default_project;
   
   String p = project_map.get(f);
   if (p == null) p = default_project;
   
   return p;
}


public File findProjectFile(String fnm)
{
   File f1 = new File(fnm);
   if (f1.isAbsolute()) return f1;
   
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
   
   Element xml = diad_control.sendBubblesMessage("PROJECTS",null,null);
   for (Element p : IvyXml.children(xml,"PROJECT")) {
      if (workspace_name == null) {
         workspace_name = IvyXml.getAttrString(p,"WORKSPACE");
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
            if (default_project == null) default_project = nm;
          }
       }
    }
}


}       // end of class DisourceFactory




/* end of DisourceFactory.java */

