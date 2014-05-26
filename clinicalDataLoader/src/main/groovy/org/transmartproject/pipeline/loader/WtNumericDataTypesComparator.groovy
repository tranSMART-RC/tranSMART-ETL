/*************************************************************************
* tranSMART - translational medicine data mart
*
******************************************************************/
 
package org.transmartproject.pipeline.loader

import java.util.Comparator;

class WtNumericDataTypesComparator implements Comparator{

	/*int compare(Object o1, Object o2) {
		
		WtNumericDataTypes p1 = (WtNumericDataTypes) o1
		WtNumericDataTypes p2 = (WtNumericDataTypes) o2
		if (p1.site_id != p2.site_id)
			return p1.site_id.compareTo(p2.site_id)
		else if (p1.subject_id != p2.subject_id)
			return p1.subject_id.compareTo(p2.subject_id)
		else if (p1.visit_name != p2.visit_name)
			return p1.visit_name.compareTo(p2.visit_name)
		else if (p1.data_label != p2.data_label)
			return p1.data_label.compareTo(p2.data_label)
		else (p1.category_cd != p2.category_cd)
			return p1.category_cd.compareTo(p2.category_cd)
	}*/
	
	int compare(Object o1, Object o2) {
		
		WtNumericDataTypes p1 = (WtNumericDataTypes) o1
		WtNumericDataTypes p2 = (WtNumericDataTypes) o2
		if (p1.site_id != p2.site_id)
			return p1.site_id.compareTo(p2.site_id)
		else if (p1.subject_id != p2.subject_id)
			return p1.subject_id.compareTo(p2.subject_id)
		else if (p1.visit_name != p2.visit_name)
			return p1.visit_name.compareTo(p2.visit_name)
		else if (p1.data_label != p2.data_label)
			return p1.data_label.compareTo(p2.data_label)
		else (p1.category_cd != p2.category_cd)
			return p1.category_cd.compareTo(p2.category_cd)
	}

	boolean equals(Object obj) {
		return this.equals(obj)
	}
}
