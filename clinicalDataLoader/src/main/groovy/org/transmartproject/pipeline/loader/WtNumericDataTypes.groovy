/**************************************************************************
* tranSMART - translational medicine data mart
*
******************************************************************/
 

package org.transmartproject.pipeline.loader

class WtNumericDataTypes {
	

	String site_id
	String subject_id
	String visit_name
	String data_label
	String category_cd
	String study_id
	public WtNumericDataTypes(String site_id, String subject_id,String study_id,
		String visit_name, String data_label, String category_cd) {
	super();
	this.site_id = site_id;
	this.subject_id = subject_id;
	this.visit_name = visit_name;
	this.data_label = data_label;
	this.category_cd = category_cd;
	this.study_id=study_id
}

}
