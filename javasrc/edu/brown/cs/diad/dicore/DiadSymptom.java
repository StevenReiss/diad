/********************************************************************************/
/*                                                                              */
/*              DiadSymptom.java                                                */
/*                                                                              */
/*      Representation of a symptom                                                   */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2025 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 *  Permission to use, copy, modify, and distribute this software and its        *
 *  documentation for any purpose other than its incorporation into a            *
 *  commercial product is hereby granted without fee, provided that the          *
 *  above copyright notice appear in all copies and that both that               *
 *  copyright notice and this permission notice appear in supporting             *
 *  documentation, and that the name of Brown University not be used in          *
 *  advertising or publicity pertaining to distribution of the software          *
 *  without specific, written prior permission.                                  *
 *                                                                               *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS                *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND            *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY      *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY          *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,              *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS               *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE          *
 *  OF THIS SOFTWARE.                                                            *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.diad.dicore;

import java.util.ArrayList;
import java.util.List;

import edu.brown.cs.diad.dicore.DiadConstants.DiadSymptomType;
import edu.brown.cs.diad.dicore.DiadConstants.DiadValueOperator;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public interface DiadSymptom
{

DiadSymptomType getSymptomType();

String getSymptomItem();
String getOriginalValue();
String getTargetValue();
DiadValueOperator getSymptomOperator(); 
double getTargetPrecision();

void setOriginalValue(String val);
void setTargetValue(String val);

default List<String> ignorePatterns() {
   return new ArrayList<>();
}
default boolean ignoreMain() {
   return false;
}
default boolean ignoreTests() {
   return true;
}
default boolean ignoreDriver() {
   return true;
}

default DiadLocation getBugLocation() {
   return null;
}

void outputXml(IvyXmlWriter xw);  




}       // end of interface DiadSymptom




/* end of DiadSymptom.java */

