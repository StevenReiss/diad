/********************************************************************************/
/*                                                                              */
/*              DigenCodeFragment.java                                          */
/*                                                                              */
/*      Code fragment for a possible test case                                  */
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



class DigenCodeFragment implements DigenConstants, Comparable<DigenCodeFragment>
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String          code_string;
private double          code_priority;

private static final double DEFAULT_PRIORITY = 10;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/


DigenCodeFragment(String code)
{
   code_string = code;
   code_priority = DEFAULT_PRIORITY;
}


DigenCodeFragment(String code,double p)
{
   code_string = code;
   code_priority = p;
}




/********************************************************************************/
/*                                                                              */
/*        Access methods                                                        */
/*                                                                              */
/********************************************************************************/

String getCode()
{
   return code_string;
}



/********************************************************************************/
/*                                                                              */
/*      Construction methods                                                    */
/*                                                                              */
/********************************************************************************/

static DigenCodeFragment append(DigenCodeFragment... frags)
{
   DigenCodeFragment pcf = null;
   
   for (DigenCodeFragment f : frags) {
      if (f != null) {
         if (pcf == null) pcf = f;
         else pcf = pcf.append(f,true);
       }
    }
   
   return pcf;
}



DigenCodeFragment append(DigenCodeFragment pcf,boolean line)
{
   return append(pcf.code_string,line);
}


DigenCodeFragment append(String addcode,boolean line)
{
   String code = code_string;
   if (line && !code.endsWith("\n")) code += "\n";
   code += addcode;
   return new DigenCodeFragment(code);
}

DigenCodeFragment append(String code1,String... code2)
{
   String code = code_string;
   if (code1 != null) code += code1;
   for (int i = 0; i < code2.length; ++i) {
      if (code2[i] != null) code += code2[i];
    }
   
   return new DigenCodeFragment(code);
}



/********************************************************************************/
/*                                                                              */
/*      Comparison methods                                                      */
/*                                                                              */
/********************************************************************************/

@Override public int compareTo(DigenCodeFragment pcf)
{
   return Double.compare(pcf.code_priority,code_priority);
}


@Override public boolean equals(Object o)
{
   if (o instanceof DigenCodeFragment) {
      DigenCodeFragment pcf = (DigenCodeFragment) o;
      return getCode().equals(pcf.getCode());
    }
   
   return false;
}



@Override public int hashCode()
{
   return getCode().hashCode();
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String toString()
{
   return code_string;
}



}       // end of class DigenCodeFragment




/* end of DigenCodeFragment.java */

