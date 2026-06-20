/********************************************************************************/
/*                                                                              */
/*              DigenTestCase.java                                              */
/*                                                                              */
/*      Representation of a test case                                           */
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

import edu.brown.cs.diad.dicore.DiadCandidate;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DigenTestCase implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DigenCodeFragment test_code;
private String test_name;
private String test_assertion;
private DiadStackFrame test_frame;
private DiadStackFrame start_frame;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenTestCase(String name,DigenCodeFragment code,String assertion,
      DiadCandidate cand,DiadStackFrame start)
{
   test_name = name;
   test_code = code;
   test_assertion = assertion;
   test_frame = cand.getThread().getStack().getUserFrame();
   start_frame = start;
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw)
{
   xw.begin("TESTCASE");
   xw.field("NAME",test_name);
   xw.field("TESTCLASS",test_frame.getClassName());
   xw.field("TESTMETHOD",test_frame.getMethodName());
   xw.field("STARTCLASS",start_frame.getClassName());
   xw.field("STARTMETHOD",start_frame.getMethodName());
   xw.field("STARTFRAME",start_frame.getFrameId());
   xw.cdataElement("BODY",test_code);
   if (test_assertion != null) {
      xw.cdataElement("ASSERTION",test_assertion);
    }
   xw.end("TESTCASE");       
}


}       // end of class DigenTestCase




/* end of DigenTestCase.java */

