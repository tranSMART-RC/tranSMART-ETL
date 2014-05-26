
/*************************************************************************
* tranSMART - translational medicine data mart
*
* */
 

package org.transmartproject.pipeline.loader
import groovy.util.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


class WorkClinicalData {
	String study_id;
	String site_id;
	String subject_id;
	String visit_name;
	String data_label;
	String data_value;
	String category_cd;
	String ctrl_vocab_code;
	String visit_date;
	String data_type;
	String category_path;
	String usubjid
	String leaf_node
	Map visitCount
	public WorkClinicalData(String study_id, String site_id, String subject_id,
	String visit_name, String data_label, String data_value,
	String category_cd, String ctrl_vocab_code, String visit_date,
	String data_type, String category_path, String usubjid,String category_path_str,String category_path_tmp,String category_cd_str,List singleVisitMap) throws TransmartException {

		super();

		this.data_value = formatDataValue(data_value)
		this.study_id = study_id;
		this.site_id = site_id;
		this.subject_id = subject_id;
		this.data_label = formatDataLabel(data_label);
		if(visit_name.equals(this.data_label)||visit_name.equals(this.data_value)||(category_cd.contains("DATALABEL") &!category_cd.contains("VISITNAME") ))
			this.visit_name = null;
		else
			this.visit_name = visit_name;
		if(this.visit_name!=null){
			if(singleVisitMap.size()>0 & singleVisitMap.contains(this.visit_name.toUpperCase())){
				this.visit_name=null;
			}
		}
		this.data_value = data_value;
		this.category_cd = category_cd;
		this.ctrl_vocab_code = ctrl_vocab_code;
		this.visit_date = visit_date;
		this.data_type = data_type;
		this.category_path = category_path;
		this.usubjid = usubjid;
		if(category_path_str!=null & this.data_label!=null){
			if(category_path_str.toUpperCase().equals(this.data_label.toString().toUpperCase()))
				this.category_path=category_path_tmp
			this.category_cd=category_cd_str
		}
		formatWorkClinicalData()
		if(isInteger(this.data_value)==0)this.data_type="N"
		if(this.visit_date!=null & isDateValid(this.visit_date, 'YYYY/MM/DD HH24:mi')){
			throw new TransmartException("Invalid visit_date in tm_lz.lt_src_clinical_data")
		}
		//setLeafNode()
		//should be removed testing purpose
		/*if(this.visit_name==  null )
		//	this.visit_name=""
		if(this.visit_date==  null )
			//this.visit_date="2013/10/23 12:30"*/
	}


	String formatDataValue(String dataValue){

		String dataValTmp
		if (dataValue==null) return dataValue
		int startPos=0,endPos=dataValue.length()

		if(dataValue.indexOf("|", 0)==0)startPos=1
		if(dataValue.substring(endPos-1).equals("|")){
			endPos=endPos-1
		}
		dataValTmp=(dataValue.substring(startPos,endPos)).toString().replace("|","-")
		if(dataValTmp.contains("()") || dataValTmp.contains("( )") || (dataValTmp.contains("(")& !dataValTmp.contains(")")) )
			dataValTmp=dataValTmp.replace("(","")
		if(dataValTmp.contains("()") || dataValTmp.contains("( )") || (dataValTmp.contains(")")& !dataValTmp.contains("(")) )
			dataValTmp=dataValTmp.replace(")","")

		return  dataValTmp
	}
	String formatDataLabel(String dataLabel){

		String datalabelTmp
		if (dataLabel==null) return dataValue
		if(dataLabel.contains("|")  )
			datalabelTmp =dataValTmp.replace("|",",")

		return datalabelTmp
	}
	String removeTrailingComma(String dataValue){

		String dataTmp
		if (dataValue==null) return dataValue
		int startPos=0,endPos=dataValue.length()

		if(dataValue.substring(endPos-1).equals(",")){
			endPos=endPos-1
		}
		dataTmp=(dataValue.substring(startPos,endPos)).toString().trim()
		return dataTmp;
	}
	String formatWorkClinicalData(){

		String data_labelTmp;
		String data_valueTmp;
		String category_cdTmp;
		String category_pathTmp;

		if(this.data_label!=null)
			this.data_label=this.data_label.replace("%", "Pct").replace("&", " and ").replace("+", " and ").replace("_", " ")
		if(this.data_value!=null)
			this.data_value=this.data_value.replace("%", "Pct").replace("&", " and ").replace("+", " and ")
		if(this.category_cd!=null)
			this.category_cd=this.category_cd.replace("%", "Pct").replace("&", " and ")
		if(this.category_path!=null)
			this.category_path=this.category_path.replace("%", "Pct").replace("&", " and ")

		if(this.data_label!=null)
			this.data_label= removeTrailingComma(this.data_label.replace("  ", " ").replace(" ,", ","))
		if(this.data_value!=null)
			this.data_value= removeTrailingComma(this.data_value.replace("  ", " ").replace(" ,", ","))
		if(this.visit_name!=null)
			this.visit_name=removeTrailingComma(this.visit_name.replace("  ", " ").replace(" ,", ","))
	}
	public boolean isDateValid(String dateToValidate, String dateFromat){

		if(dateToValidate == null){
			return false;
		}

		SimpleDateFormat sdf = new SimpleDateFormat(dateFromat);
		sdf.setLenient(false);

		try {

			//if not valid, it will throw ParseException
			Date date = sdf.parse(dateToValidate);
			System.out.println(date);

		} catch (ParseException e) {

			e.printStackTrace();
			return false;
		}

		return true;
	}
	public int  isInteger(String str) {
		try {
			if(str==null) return 1;
			Integer.parseInt(str);
			return 0;
		} catch (NumberFormatException nfe) {

		}
		return 1;
	}
	
	public int  setLeafNode() {
		String category_path_st=this.category_path;
		 
		if (this.data_type.equals("T")){
				if(this.category_path.concat("DATALABEL")||this.category_path.equals("VISITNAME")){
					this.leaf_node=(category_path.replace("DATALABEL", this.data_label).replace("VISITNAME", this.visit_name))+"\\"+this.data_value+"\\"
					
				}
			
		}
	}
}
