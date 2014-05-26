/*************************************************************************
* tranSMART - translational medicine data mart
*
* this code is implementation of procedure I2B2_LOAD_CLINICAL_DATA.
*
******************************************************************/
 

package org.transmartproject.pipeline.loader

import org.apache.log4j.Logger;

import java.util.Map;

import org.transmartproject.pipeline.loader.WorkClinicalData
import org.transmartproject.pipeline.loader.WtNumericDataTypes

import groovy.sql. Sql
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator
import org.apache.log4j.spi.RootCategory;
import org.transmartproject.pipeline.util.TransmartClinicalLoaderSQL;
import org.transmartproject.pipeline.util.Util
import groovy.util.*;

class I2B2ClinicalLoadData {
	private static final Logger log = Logger.getLogger(Loader)

	static Map addNodes = [:]
	static List delNodes,delUnusedLeaf
	static int stepCt=0,jobID,newJobFlag=0,root_level
	static String databaseName="TM_CZ",	procedureName="I2B2_LoadClinical_Data-GroovyImpl-org.transmartproject.pipeline.loader.I2B2ClinicalLoadData"
	static String highlight_study
	static String regPattern ="(\\\\){1,}"
	static Sql i2b2demodata,i2b2metadata,deapp,biomart
	static java.sql.Date etlDate
	static int  main(List args) {

		String secure_study,highlight_study,top_node="",trail_id,secureStudy,study_name,root_node,top_node_tmp,topNode,tPath,tText,errorMessage
		List c_hlevel;
		top_node_tmp=top_node;
		int topLevel,pExistsCount,pCount,currentJobID,returnCode
		
		def workClinicalDataSet
		List workClinicalData  = new ArrayList()
		Sql i2b2demodata,i2b2metadata,deapp,biomart
		try{
			if (args.size() >0){
				trail_id=args.get(0).toString()
				top_node=args.get(1).toString()
				secureStudy=args.get(2).toString()
				highlight_study=args.get(3).toString()
				Integer currentJobIDtmp=(args.get(4)).toString().toInteger()
				currentJobID =(int)currentJobIDtmp
				
			}else{
				log.error("Missing Parameters to start the processing :  ")
			}
			if (secureStudy==null)
				 secureStudy="N"
			if (highlight_study==null) 
				highlight_study="N"
			if (currentJobID==null) 	
				currentJobID=1
			log.info("I2B2ClinicalLoadData started with parameter trail ID: "+trail_id+" Top Node :  "+ top_node +"Curretn JobID : "+currentJobID)
			PropertyConfigurator.configure("conf/log4j.properties");
			log.info("Start loading property file for database connections")


			Properties props = Util.loadConfiguration("conf/Common.properties")

			i2b2demodata = Util.createSqlFromPropertyFile(props, "i2b2demodata")
			i2b2metadata = Util.createSqlFromPropertyFile(props, "i2b2metadata")
			deapp = Util.createSqlFromPropertyFile(props, "deapp")
			biomart = Util.createSqlFromPropertyFile(props, "biomart")
			log.info("Loaded Property file and database connections are OK...")

			databaseName="TM_CZ"
			procedureName="I2B2_LoadClinical_Data-GroovyImpl-org.transmartproject.pipeline.loader.I2B2ClinicalLoadData"

			trail_id= trail_id.toUpperCase()
			secureStudy=secureStudy.toUpperCase()
			log.info("Start i2b2_load_clinical_data for "+trail_id)
			if(!secureStudy.equals("Y")||!secureStudy.equals("N"))
				secureStudy="Y"
			etlDate=new java.sql.Date(new java.util.Date().getTime())
			jobID = currentJobID
			if (jobID==null || jobID<1){
				log.info("If Job ID does not exist, then this is a single method  run and we need to create it")
				log.info("calling start audit procedure ")
				newJobFlag=1;
				biomart.call("{call TM_CZ.cz_start_audit ($procedureName, $databaseName, $jobID)}")
				log.info("calling start audirt procedure : Done")
			}
			
			tText="Start i2b2_load_clinical_data for "+trail_id
			log.info(tText)
			stepCt=stepCt+1
			biomart.call("{call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,$tText,0,$stepCt,'Done')}")

			I2B2ClinicalLoadData clinicalLoadData = new I2B2ClinicalLoadData()

			log.info("setting addNodes delNodes and delUnusedLeaf..")
			addNodes= clinicalLoadData.getAddNodes(biomart)
			delNodes=clinicalLoadData.getDelNodes(biomart,top_node)
			delUnusedLeaf=clinicalLoadData.getDelUnusedLeaf(biomart,top_node,trail_id)
			log.info("Finished  addNodes delNodes and delUnusedLeaf settings..")
			//topLevel=top_node.replace("\\", "\\\\")
			topNode=("\\"+top_node+"\\").replaceAll(regPattern, '\\\\')
			topLevel=topNode.length()-(topNode.replace("\\", "")).length();
			log.info("topNode : $topNode  ,topLevel: $topLevel")
			if (topLevel<3){				
				log.error("Path specified in top_node must contain at least 2 nodes");
				throw new TransmartException("Path specified in top_node must contain at least 2 nodes");
			}
			//Get study name from topNode
			study_name=Util.ParseString(topNode, topLevel-1, "\\\\")
			log.info("study_name : $study_name")
			//Replace all underscores with spaces in topNode except those in study name^
			top_node_tmp=top_node
			topNode=(topNode.replace(study_name+"\\","").replace("_", " "))+study_name+"\\";
			log.info("Replace all underscores with spaces in topNode except those in study name topNode: $topNode ")
			//Get root node  from topNode
			root_node= Util.ParseString(topNode, 1, "\\\\")
				stepCt=stepCt+1
			//delete any existing data from lz_src_clinical_data and load new data
			log.info("delete any existing data from lz_src_clinical_data and load new data :starts...")

			clinicalLoadData.deleteExistingDatafromSrcClinical(biomart,trail_id)
			biomart.call("{call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,'Delete existing data from lz_src_clinical_data',0,$stepCt,'Done')}")
			log.info("delete any existing data from lz_src_clinical_data and load new data : Done")
			//insert data in to into lz_src_clinical_data

			log.info("insert data in to into lz_src_clinical_data :starts...")
			clinicalLoadData.insertDataToSrcLzClinical(biomart,etlDate,jobID)
			//biomart.call("{ call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,'Insert data into lz_src_clinical_data',100,$stepCt,'Done')}")
			log.info("insert data in to into lz_src_clinical_data : Done")

			pExistsCount=clinicalLoadData.getpExistsCount(biomart,root_node)
			pCount = clinicalLoadData.getpCount(biomart,root_node)
			if(pExistsCount==0 || pCount==0 ){
				clinicalLoadData.i2b2_add_root_node(currentJobID,root_node,biomart)

			}
			root_level = clinicalLoadData.getCLevel(biomart,root_node)
			//Add any upper level nodes as needed
			tPath=(top_node.replace(study_name,"")).replaceAll(regPattern, '\\\\')
			pCount=tPath.length()-(tPath.replace("\\", "")).length();
			log.info("tPath : $tPath  pCount: $pCount")
			if (pCount>2){
				stepCt = stepCt + 1;
				//cz_write_audit(jobId,databaseName,procedureName,'Adding upper-level nodes',0,stepCt,'Done');
				log.info("Adding upper-level nodes : Starts...")
				biomart.call("{ call TM_CZ.i2b2_fill_in_tree(null, $tPath, $jobID)}")
				//i2b2_fill_in_tree(null, tPath, jobID);
				biomart.call("{call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,'Adding upper-level nodes',0,$stepCt,'Done')}")
				log.info("Adding upper-level nodes : Done")

			}
			pExistsCount=clinicalLoadData.getpExistsCountI2B2(biomart,topNode)
			if (pExistsCount==0){
				biomart.call("{call TM_CZ.i2b2_add_node($trail_id,$topNode,$study_name,$jobID)}")

			}

			//load data into work clinical tables
			//workClinicalDataSet = biomart.dataSet("TM_LZ.lt_src_clinical_data")

			//audit table insertion via groovy code
			//biomart.call("{call TM_CZ.cz_write_audit($jobID,'databaseName - TM_CZ','procedureName-load clinical data groovy','processing the tables',100,100,'Done')}")
			log.info("Loading data into Src Clinical data : Starts ...")
			stepCt++;
			returnCode =clinicalLoadData.processWorkClinicalDataSet(biomart,root_node,trail_id,topNode,tPath,secureStudy,jobID)
			biomart.call("{call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,'Loading data in to Src work table fo prcessing ',0,$stepCt,'Done')}")

			//load data into different tables
			return returnCode;



		}catch(TransmartException exception){
		log.error(errorMessage)
		
			stepCt = stepCt + 1;
			errorMessage =exception.getMessage()
			log.error(errorMessage)
			log.error(exception.printStackTrace())
			biomart.call("{call  TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,$errorMessage,0,$stepCt,'Done')}")
			//biomart.call("{cz_error_handler ($jobID, $procedureName)}")
			biomart.call("{call TM_CZ.cz_end_audit ($jobID, 'FAIL')}")

			
			return 16

		}

	}




	Map getAddNodes(Sql biomart){
		log.info("starting setting getAddNodes ...")
		Map delAddNodesTmp = [:]
		String qry = """ select DISTINCT  leaf_node, node_name   from  TM_WZ.wt_trial_nodes a """
		biomart.eachRow(qry) {
			delAddNodesTmp[it.leaf_node] = it.node_name
		}
		log.info("Finished  setting getAddNodes ...")
		//println(delAddNodesTmp.toMapString())
		return delAddNodesTmp

	}

	List getDelNodes(Sql biomart,String top_node){
		log.info("starting getDelNodes...")
		List  delNodesTmp =new ArrayList()
		String qry = " select distinct c_fullname 	from  i2b2	where c_fullname like '"+top_node +"%'"+" and substr(c_visualattributes,2,1) = 'H' "
		biomart.eachRow(qry) {
			delNodesTmp.add(it.c_fullname )
		}
		log.info("Finished  getDelNodes ...")
		//println(delNodesTmp.toListString()())
		return delNodesTmp

	}
	List getDelUnusedLeaf(Sql biomart,String top_node ,String trail_id){
		log.info("starting  getDelUnusedLeaf ...")
		List  delUnusedLeafTmp = new ArrayList()
		String qry = " select l.c_fullname	from i2b2 l	where l.c_visualattributes like 'L%'	  and l.c_fullname like '"+top_node+"%'"+
				"and l.c_fullname not in"+
				" (select t.leaf_node "+
				" from TM_WZ.wt_trial_nodes t"+
				" union"+
				" select m.c_fullname"+
				" from de_subject_sample_mapping sm ,i2b2 m"+
				" where sm.trial_name = '"+trail_id+"' and sm.concept_code = m.c_basecode"+
				" and m.c_visualattributes like 'L%')"
		biomart.eachRow(qry) {
			delUnusedLeafTmp.add(it.c_fullname )
		}
		log.info("Finished  getDelUnusedLeaf ...")
		//println(delUnusedLeafTmp.toListString()())
		return delUnusedLeafTmp

	}
	void  deleteExistingDatafromSrcClinical(Sql biomart,String TrialId){
		log.info("Starting deleting existing data from lz_src_clinical_data ")
		String qry
		boolean status
		qry = "delete from TM_LZ.lz_src_clinical_data 	where UPPER(study_id) ='"+ TrialId.toUpperCase()+"'"

		status=biomart.execute(qry)
		log.info("Complated deleting existing data from lz_src_clinical_data: Status"+status)
	}
	int getCLevel(Sql biomart,String root_node){
		log.info("starting getCLevel...")
		def   root_level_tmp
		String qry = " select c_hlevel from table_access where c_name = '"+ root_node+"'"
		root_level_tmp= biomart.eachRow(qry) {
			if(it.c_hlevel==null)root_level_tmp=0
			else root_level_tmp =(it.c_hlevel )
		}
		
		log.info("Finished  getCLevel ...")
		//println(root_level_tmp.toListString()())
		if(root_level_tmp==null) return 0
		
		return root_level_tmp

	}

	void  insertDataToSrcLzClinical(Sql biomart,java.sql.Date etlDate,int jobId){

		log.info("Starting inserting   data to lz_src_clinical_data ")

		String qry = """ 	insert into tm_lz.lz_src_clinical_data
							(study_id
							,site_id
							,subject_id
							,visit_name
							,data_label
							,data_value
							,category_cd
							,etl_job_id
							,etl_date
							,ctrl_vocab_code
							,visit_date)
							select study_id
								  ,site_id
								  ,subject_id
								  ,visit_name
								  ,data_label
								  ,data_value
								  ,category_cd, """+jobId+"""  ,sysdate
								
								  ,ctrl_vocab_code
								  ,visit_date
							from tm_lz.lt_src_clinical_data
							""";


		biomart.execute(qry)
		stepCt=stepCt+1
		writeAudit(biomart,jobID,databaseName,procedureName,'Insert data into lz_src_clinical_data',biomart.updateCount,stepCt,'Done');
		log.info("inserting   data to lz_src_clinical_data: Status"+biomart.updateCount)
	}

	int getpExistsCount(Sql biomart,String root_node){


		String qry = "select count(*) as pExists	from table_access	where c_name = '" + root_node+"'"
		def pExistsCount = biomart.firstRow(qry).pExists
		return pExistsCount
	}
	int getpExistsCountI2B2(Sql biomart,String topnode){


		String qry = "select count(*) as pExists	from i2b2	where c_fullname = '" + topnode+"'"
		def pExistsCount = biomart.firstRow(qry).pExists
		return pExistsCount
	}
	int getpCount(Sql biomart,String root_node){


		String qry = "select count(*) as pCount from i2b2	where c_name = '" + root_node+"'"
		def pCount = biomart.firstRow(qry).pCount
		return pCount
	}
	void  i2b2_add_root_node(int currentJobID,String root_node, Sql biomart){
		log.info(" adding root node in I2b2   ")
		biomart.call("{call TM_CZ.i2b2_add_root_node($root_node, $currentJobID)}")
		log.info(" adding root node in I2b2  :Done ")
		return
		String rootNode,rootPath,qry
		int     stepCt,newJobFlag,jobID
		rootNode=root_node
		newJobFlag = 0;
		jobID = currentJobID;
		rootPath = '\\'+root_node+"\\"
		stepCt = 0;
		if(jobID ==null |  jobID < 1){
			newJobFlag = 1
			//cz_start_audit (procedureName, databaseName, jobID);
		}

		stepCt = stepCt + 1;
		//cz_write_audit(jobId,databaseName,procedureName,'Start ' || procedureName,0,stepCt,'Done');


		qry = " insert into table_access "

		"select "+rootNode +"as c_table_cd "+
				",'i2b2' as c_table_name"+
				",'N' as protected_access,"+ " ,0 as c_hlevel '"+ rootPath+"' as c_fullname, '"+rootNode+"' as c_name "+
				",'N' as c_synonym_cd "+
				",'CA' as c_visualattributes ,"+
				null+" as c_totalnum,"+
				null+" as c_basecode,"+
				null+" as c_metadataxml "+
				" ,'concept_cd' as c_facttablecolumn"+
				" ,'concept_dimension' as c_dimtablename"+
				" ,'concept_path' as c_columnname"+
				" ,'T' as c_columndatatype"+
				" ,'LIKE' as c_operator ,'"+
				rootPath +"' as c_dimcode"+
				","+null+" as c_comment "+
				",'"+rootPath+"' as c_tooltip "+
				",sysdate  as c_entry_date "+
				","+null+" as c_change_date"+
				","+null +" as c_status_cd"+
				","+null+" as valuetype_cd"+
				"from dual	where not exists (select 1 from table_access x"+
				" where x.c_table_cd ="+ rootNode +")"

		biomart.execute(qry)

		stepCt = stepCt + 1;
		//cz_write_audit(jobId,databaseName,procedureName,'Insert root_node ' || rootNode || ' to i2b2',SQL%ROWCOUNT,stepCt,'Done');
		stepCt = stepCt + 1;
		// cz_write_audit(jobId,databaseName,procedureName,'End ' || procedureName,0,stepCt,'Done');

		//qry=sql.execute('insert into i2b2 (c_hlevel	,c_fullname	,c_name	,c_synonym_cd	,c_visualattributes	,c_totalnum	,c_basecode	,c_metadataxml '+
		//',c_facttablecolumn	,c_tablename	,c_columnname	,c_columndatatype	,c_operator	,c_dimcode	,c_comment	,c_tooltip	,update_date	,download_date	,import_date,sourcesystem_cd '+
		//' ,valuetype_cd	,i2b2_id ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,I2B2_ID_SEQ.nextval)', [0, rootPath,rootNode,'N','CA',null,null,null,'concept_cd','concept_dimension','concept_path','T','LIKE',rootPath,null,rootPath,new Date(),null,new Date(),null,null])
		qry = " insert into i2b2 "+
				"(c_hlevel	,c_fullname	,c_name	,c_synonym_cd	,c_visualattributes		,c_totalnum		,c_basecode	,c_metadataxml	,c_facttablecolumn "+
				"	,c_tablename	,c_columnname	,c_columndatatype	,c_operator			,c_dimcode	,c_comment,c_tooltip	,update_date "+
				",download_date			,import_date			,sourcesystem_cd			,valuetype_cd			,i2b2_id			)"+
				"select "+
				"0 as c_hlevel "+ rootPath+" as c_fullname, "+rootNode+" as c_name "+
				",'N' as c_synonym_cd "+
				",'CA' as c_visualattributes ,"+
				null+" as c_totalnum,"+
				null+" as c_basecode,"+
				null+" as c_metadataxml "+
				" ,'concept_cd' as c_facttablecolumn"+
				" ,'concept_dimension' as c_dimtablename"+
				" ,'concept_path' as c_columnname"+
				" ,'T' as c_columndatatype"+
				" ,'LIKE' as c_operator ,"+
				rootPath +"as c_dimcode"+
				","+null+" as c_comment "+
				","+rootPath+"as c_tooltip "+
				",sysdate  as update_date "+
				","+null+" as download_date"+
				", sysdate as import_date"+
				","+null+" as sourcesystem_cd"+
				","+null+" as valuetype_cd, I2B2_ID_SEQ.nextval as i2b2_id"+
				"from dual	where not exists (select 1 from i2b2 x"+
				" where x.c_name="+ rootNode +")"

		biomart.execute(qry)
		//	writeAudit(biomart,jobId,databaseName,procedureName,'Insert root_node ' || rootNode || ' to i2b2',SQL%ROWCOUNT,stepCt,'Done');



	}

	void  writeAudit(Sql db,int jobId,String databaseName,String procedureName,String step_Desc,int rowManupulated,int stepCt,String status){

		db.call("{call TM_CZ.cz_write_audit($jobId,$databaseName,$procedureName,$step_Desc,$rowManupulated,$stepCt,$status)}")
	}


	int  processWorkClinicalDataSet(Sql biomart,String root_node,String trail_id,String top_node,String tPath,String secureStudy,int jobID){

		int count=0,rowCount=0
		String sql="", node_name
		List workClinicalData  = new ArrayList()
		List wtNumericDataTypesList=new ArrayList(),wtNumericDataTypesListTmp  = new ArrayList(),leafNodeList=new ArrayList()
		List singleVisitMap = new ArrayList()
		Sql workClinicalDb =biomart
		java.sql.Date etlDtaetmp
		/*		String sql= "select study_id as study_id, site_id, subject_id,visit_name, data_label, data_value,category_cd, ctrl_vocab_code, visit_date,\'T\' as data_type"+
		 " ,replace(replace(category_cd,'_',' '),'+','\') as category_path, REGEXP_REPLACE('"+trail_id+" :' || site_id || ':' || subject_id,'(::){1,}', ':')  as usubjid  "+
		 " from TM_LZ.lt_src_clinical_data_old where data_value is not null"
		 //writeAudit(biomart, count, trail_id, trail_id, trail_id, count, count, trail_id)*/

		log.info("")

		sql="select count(*) as pExists	from (select count(*)  from tm_wz.wrk_clinical_data   where visit_date is not null  group by "+
				" site_id ,subject_id ,visit_name ,data_label  ,data_value ,category_cd	  having count(*) != count(distinct visit_date))"
		biomart.eachRow(sql){
			if (it.pExists>0)
				throw new TransmartException("Multiple records with same visit_date found")

		}
		sql="select count(*) as pExists from     tm_lz.lt_src_subj_enroll_date  where enroll_date is not null and tm_cz.is_date(enroll_date,'YYYY/MM/DD HH24:mi') = 1"
		biomart.eachRow(sql){
			if (it.pExists>0)
				throw new TransmartException("Check for invalid enroll_date- Invalid enroll_date in tm_lz.lt_src_subj_enroll_date")

		}

		log.info("set visit_name to null when there's only a single visit_name for the catgory")
		//writeAudit(biomart,jobId,databaseName,procedureName,'Insert root_node ' || rootNode || ' to i2b2',SQL%ROWCOUNT,stepCt,'Done');
		sql="select UPPER(x.category_cd) as category_cd  from TM_LZ.lt_src_clinical_data x   group by x.category_cd   having count(distinct upper(x.visit_name)) = 1"
		biomart.eachRow(sql){
			singleVisitMap.add(it.category_cd)}
		log.info("reading data from source tables :Starts")
		sql= "select study_id as study_id, site_id, subject_id,visit_name, data_label, data_value,category_cd, ctrl_vocab_code, visit_date,\'T\' as data_type"+
				" ,replace(replace(category_cd,'_',' '),'+','\') as category_path, REGEXP_REPLACE('"+trail_id+" :' || site_id || ':' || subject_id,'(::){1,}', ':')  as usubjid , "+
				" (upper(substr((replace(replace(category_cd,'_',' '),'+','\')),instr((replace(replace(category_cd,'_',' '),'+','\')),  "+
				"	'\',-1)+1,length((replace(replace(category_cd,'_',' '),'+','\')))-instr((replace(replace(category_cd,'_',' '),'+','\')),'\',-1)))) as category_path_str "+
				" ,  (substr((replace(replace(category_cd,'_',' '),'+','\')),1,instr((replace(replace(category_cd,'_',' '),'+','\')),'\',-2)-1)) as category_path_tmp, "+
				" (substr(category_cd,1,instr(category_cd,'+',-2)-1)) as category_cd_str from TM_LZ.lt_src_clinical_data where data_value is not null"

		biomart.eachRow(sql){

			workClinicalData.add(new WorkClinicalData( it.study_id,it.site_id,it.subject_id,it.visit_name,it.data_label,it.data_value,it.category_cd,it.ctrl_vocab_code,it.visit_date,it.data_type,it.category_path,it.usubjid,it.category_path_str,it.category_path_tmp,it.category_cd_str,singleVisitMap ))
			//checking for numeric value:
			if (isInteger(it.data_value)==0 &&it.visit_date==null )
			{
				wtNumericDataTypesList.add(new WtNumericDataTypes(it.site_id,it.subject_id,it.study_id,it.visit_name,it.data_label,it.category_cd) )

			}
		}
		log.info("reading data from source tables :Done" +workClinicalData.size())
		log.info("Check if any duplicate records of key columns (site_id, subject_id, visit_name, data_label, category_cd) for numeric data")

		wtNumericDataTypesListTmp =wtNumericDataTypesList.unique(new WtNumericDataTypesComparator());
		if (wtNumericDataTypesListTmp.size()!=wtNumericDataTypesList.size())
			throw new TransmartException("Duplicate values found in key columns")
		log.info("Checked if any duplicate records of key columns (site_id, subject_id, visit_name, data_label, category_cd) for numeric data")

		log.info("Check if Multiple visit_names exist for category/label/value")
		sql="select max(case when x.null_ct > 0 and x.non_null_ct > 0 "+
				" then 1 else 0 end) as pCount "+
				"  from (select category_cd, data_label, data_value "+
				"  ,sum(decode(visit_name,null,1,0)) as null_ct "+
				"	 ,sum(decode(visit_name,null,0,1)) as non_null_ct "+
				"	from tm_lz.lt_src_clinical_data "+
				"	where (category_cd like '%VISITNAME%' or "+
				"   category_cd not like '%DATALABEL%') "+
				"group by category_cd, data_label, data_value) x"

		biomart.eachRow(sql){
			if (it.pCount>0)
				throw new TransmartException("Multiple visit_names exist for category/label/value") }
		log.info("Checked if Multiple visit_names exist for category/label/value")
		biomart.call("{call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,'Processed Work Clinical data obkects',0,$stepCt,'Done')}")
		;
		def workClinicalDataToLoad=workClinicalData
		log.info("Insert data in to the wt_trial_nodes and work clinical tables")
		sql="truncate table tm_wz.wt_trial_nodes"
		biomart.execute(sql)
		def wt_trial_nodes = biomart.dataSet("tm_wz.wt_trial_nodes")
		String sqlWrkTmp="truncate table tm_wz.wrk_clinical_data"
		workClinicalDb.execute(sqlWrkTmp)
		
		def workClinicalTable= workClinicalDb.dataSet("tm_wz.WRK_CLINICAL_DATA")
		rowCount=workClinicalData.size()

		workClinicalDataToLoad.each {
			String category_path_st=it.category_path;
			String leafNode
			if (it.data_type.equals("T")){
				if(it.category_path.contains("DATALABEL")||it.category_path.equals("VISITNAME")){
					leafNode= top_node+(category_path_st.replace("DATALABEL", it.data_label).replace("VISITNAME", it.visit_name))+"\\"+it.data_value+"\\"

				}else
					leafNode=top_node+it.category_path+"\\"+it.data_label+"\\"+it.data_value+"\\"+it.visit_name+"\\"
			}else{

				if(it.category_path.contains("DATALABEL")||it.category_path.equals("VISITNAME")){
					leafNode= top_node+(category_path_st.replace("DATALABEL", it.data_label).replace("VISITNAME", it.visit_name))+"\\"

				}else
					leafNode=top_node+it.category_path+"\\"+it.data_label+"\\"+it.visit_name+"\\"
					
			}
			leafNode=(leafNode.replace("null","")).replaceAll(regPattern,"\\\\")
			log.info("leafNode : $leafNode")
			node_name=Util.ParseString(leafNode, leafNode.length()-leafNode.replace("\\","").length()-1, "\\\\")
			log.info("node_name : $node_name")
			 etlDtaetmp = new java.sql.Date(System.currentTimeMillis())

			//workClinicalTable.add(STUDY_ID:"$it.study_id",SITE_ID:"$it.site_id",SUBJECT_ID:"$it.subject_id",VISIT_NAME:"$it.visit_name",DATA_LABEL:"$it.data_label",DATA_VALUE:"$it.data_value",CATEGORY_CD:"$it.category_cd",ETL_JOB_ID:"$jobID",ETL_DATE:'$etlDtaetmp',USUBJID:"$it.usubjid",CATEGORY_PATH:"$it.data_type",DATA_TYPE:"$it.category_path",CTRL_VOCAB_CODE:"$it.ctrl_vocab_code",VISIT_DATE:"$it.visit_date")
			workClinicalTable.add(STUDY_ID:it.study_id,SITE_ID:it.site_id,SUBJECT_ID:it.subject_id,VISIT_NAME:it.visit_name,DATA_LABEL:it.data_label,DATA_VALUE:it.data_value,CATEGORY_CD:it.category_cd,ETL_JOB_ID:new Integer(jobID),ETL_DATE:etlDtaetmp,USUBJID:it.usubjid,CATEGORY_PATH:it.data_type,DATA_TYPE:it.category_path,CTRL_VOCAB_CODE:it.ctrl_vocab_code,VISIT_DATE:it.visit_date)
			wt_trial_nodes.add(leaf_node:leafNode,category_cd:it.category_cd, visit_name:it.visit_name,data_label:it.data_label,node_name:node_name,data_value:it.data_value,data_type:it.data_type)
			/*leafNodeList.add(leaf_node);
			 if(leafNodeList.contains(leaf_node)){
			 int leafindex=leafNodeList.indexOf(leaf_node)
			 leafNodeList.get(leafindex).equals(leaf_node)
			 }*/

		}
		biomart.call("{call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,'Create leaf nodes for trial ',$rowCount,$stepCt,'Done')}")
		log.info("checking if any node is a parent of another, all nodes must be children :  ")
		sql="select count(*) as pExists	from tm_wz.wt_trial_nodes p	,tm_wz.wt_trial_nodes c	where c.leaf_node like p.leaf_node || '%'	  and c.leaf_node != p.leaf_node"
		biomart.eachRow(sql) {
			if(it.pExists > 0)
			{
				biomart.call("{call TM_CZ.cz_write_audit($jobID,$databaseName,$procedureName,'Check if node is parent of another node ',$it.pExists,$stepCt,'Done')}")
				log.info("found onr  node is a parent of another, all nodes must be children :  ")
				throw new TransmartException("found ne  node is a parent of another, all nodes must be children ")
			}
		}
		//biomart.call("{cz_write_audit($jobID,$databaseName,$procedureName,'Check if node is parent of another node ',$pExists,$stepCt,'Done')}")

		log.info("insert subjects into patient_dimension if needed Starts....")

		sql="truncate table tm_cz.tmp_subject_info"
		biomart.execute(sql)
		sql=TransmartClinicalLoaderSQL.insert_into_tmp_subject_info;
		biomart.execute(sql)
		log.info("insert subjects into patient_dimension if needed : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Insert subject information into temp table',biomart.updateCount,stepCt,'Done');

		//deleting the patient dimension
		log.info("Delete dropped subjects from patient_dimension if they do not exist in de_subject_sample_mapping Starts....")
		sql=TransmartClinicalLoaderSQL.delete_patient_dimension;
		biomart.execute(sql,[trail_id])
		log.info("Delete dropped subjects from patient_dimension_release if they do not exist in de_subject_sample_mapping : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Delete dropped subjects from patient_dimension_release',biomart.updateCount,stepCt,'Done');


		log.info("Delete dropped subjects from patient_dimension_release if they do not exist in de_subject_sample_mapping Starts....")
		sql=TransmartClinicalLoaderSQL.delete_patient_dimension;
		biomart.execute(sql,trail_id)
		log.info("Delete dropped subjects from patient_dimension if they do not exist in de_subject_sample_mapping : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Delete dropped subjects from patient_dimension_release',biomart.updateCount,stepCt,'Done');

		log.info("Dupdate patients with changed information Starts....")
		sql=TransmartClinicalLoaderSQL.update_patient_dimension;
		biomart.execute(sql,[etlDtaetmp])
		log.info("update patients with changed information : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Update subjects with changed demographics in patient_dimension',biomart.updateCount,stepCt,'Done');


		log.info("insert new subjects into patient_dimension Starts....")
		sql=TransmartClinicalLoaderSQL.insert_into_patient_dimension;
		biomart.execute(sql,[etlDtaetmp,etlDtaetmp,etlDtaetmp,trail_id])
		log.info("insert new subjects into patient_dimension : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'insert new subjects into patient_dimension',biomart.updateCount,stepCt,'Done');


		//delete leaf nodes that will not be reused, if any
		log.info("Deleted unused node: Starts....")
		if(delUnusedLeaf!=null && delUnusedLeaf.size()>0)  {
			for (unUsedLeaf in delUnusedLeaf){
				biomart.call("{call tm_cz.i2b2_delete_1_node($unUsedLeaf,$jobID)}")
				log.info("Deleted unused node: "+unUsedLeaf)
				stepCt = stepCt + 1;
				writeAudit(biomart,jobID,databaseName,procedureName,'Deleted unused node:'+unUsedLeaf,biomart.updateCount,stepCt,'Done');

			}

		}
		log.info("Deleted unused node: Done")


		log.info("Update name_char in concept_dimension for changed names Starts....")
		sql=TransmartClinicalLoaderSQL.update_concept_dimension;
		biomart.execute(sql)
		log.info("Update name_char in concept_dimension for changed names : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Update name_char in concept_dimension for changed names',biomart.updateCount,stepCt,'Done');

		log.info("Inserte new leaf nodes into I2B2DEMODATA concept_dimension Starts....")
		sql=TransmartClinicalLoaderSQL.insert_into_concept_dimension;
		biomart.execute(sql,[etlDtaetmp,etlDtaetmp,etlDtaetmp,trail_id])
		log.info("Inserted new leaf nodes into I2B2DEMODATA concept_dimension : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Inserted new leaf nodes into I2B2DEMODATA concept_dimension',biomart.updateCount,stepCt,'Done');

		log.info("update i2b2 to pick up change in name, data_type for leaf nodes Starts....")
		sql=TransmartClinicalLoaderSQL.update_i2b2;
		biomart.execute(sql)
		log.info("update i2b2 to pick up change in name, data_type for leaf nodes : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Updated name and data type in i2b2 if changed',biomart.updateCount,stepCt,'Done');

//commented the code
		log.info("update i2b2 to pick up change in name, data_type for leaf nodes Starts....")
		sql=TransmartClinicalLoaderSQL.update_i2b2;
		biomart.execute(sql)
		log.info("update i2b2 to pick up change in name, data_type for leaf nodes : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Updated name and data type in i2b2 if changed',biomart.updateCount,stepCt,'Done');

		log.info("Inserte leaf nodes into I2B2METADATA i2b2 Starts....")
		sql=TransmartClinicalLoaderSQL.insert_into_i2b2;
		biomart.execute(sql,[root_level,etlDtaetmp,etlDtaetmp,etlDtaetmp,trail_id])
		log.info("Inserted leaf nodes into I2B2METADATA i2b2 : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Inserted leaf nodes into I2B2METADATA i2b2',biomart.updateCount,stepCt,'Done');


		log.info("Delete clinical data for study from observation_fact Starts....")
		sql=TransmartClinicalLoaderSQL.delete_from_observation_fact;
		biomart.execute(sql,[trail_id,trail_id,trail_id,trail_id,trail_id,trail_id,trail_id,trail_id],)
		log.info("Delete clinical data for study from observation_fact : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Delete clinical data for study from observation_fact',biomart.updateCount,stepCt,'Done');

		log.info("Insert trial into I2B2DEMODATA observation_fact Starts....")
		sql=TransmartClinicalLoaderSQL.insert_into_observation_fact;
		//biomart.execute(sql,[trail_id,etlDtaetmp,trail_id])
		log.info("Insert trial into I2B2DEMODATA observation_fact : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Insert trial into I2B2DEMODATA observation_fact',biomart.updateCount,stepCt,'Done');


		log.info("Delete existing data from deapp.de_obs_enroll_days Starts....")
		sql=TransmartClinicalLoaderSQL.delete_from_de_obs_enroll_days;
		biomart.execute(sql,[trail_id])
		log.info("Delete existing data from deapp.de_obs_enroll_days : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Delete existing data from deapp.de_obs_enroll_days',biomart.updateCount,stepCt,'Done');

		log.info("Insert data in deapp.de_obs_enroll_days Starts....")
		sql=TransmartClinicalLoaderSQL.insert_into_de_obs_enroll_days
		biomart.execute(sql,[trail_id])
		log.info("Insert data in deapp.de_obs_enroll_days : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Insert data in deapp.de_obs_enroll_days',biomart.updateCount,stepCt,'Done');


		log.info("Update c_visualattributes for study Starts....")
		sql=TransmartClinicalLoaderSQL.update_i2b2_c_visualattributes
		biomart.execute(sql,[highlight_study,top_node,top_node,top_node])
		log.info("Update c_visualattributes for study : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Update c_visualattributes for study',biomart.updateCount,stepCt,'Done');
		
		//biomart.call("{i2b2_fill_in_tree($trail_id, $top_node, $jobID)}")
		biomart.call("{ call TM_CZ.i2b2_fill_in_tree(null, $tPath, $jobID)}")
		
		log.info("Set sourcesystem_cd to null for added upper-level nodes Starts....")
		sql=TransmartClinicalLoaderSQL.update_i2b2_for_upper_level_nodes
		biomart.execute(sql,[trail_id,top_node])
		log.info("Set sourcesystem_cd to null for added upper-level nodes : Done")
		stepCt++
		writeAudit(biomart,jobID,databaseName,procedureName,'Set sourcesystem_cd to null for added upper-level nodes',biomart.updateCount,stepCt,'Done');


		root_node = '\\';
		tPath=tPath.replaceAll(regPattern, '\\\\')
		log.info(" tPath : $tPath")
		count = tPath.length()-tPath.replace("\\", "").length();
		String levelName

		for (int i=1;i<count;i++){

			levelName= Util.ParseString(tPath, i, "\\\\")
			root_node=root_node+levelName+"\\"
			log.info("Set P visualattribute for parent node: root_node : $root_node levelName : $levelName Starts....")
			sql=TransmartClinicalLoaderSQL.update_i2b2_c_visualattributes_for_P
			biomart.execute(sql,[root_node])
			log.info("Set P visualattribute for parent node: $root_node : Done")
			stepCt++
			writeAudit(biomart,jobID,databaseName,procedureName,'Set P visualattribute for parent node: $root_node',biomart.updateCount,stepCt,'Done');


		}
		biomart.call("{call tm_cz.i2b2_create_concept_counts($top_node, $jobID)}")

		log.info("Delete  node: Starts....")
		if(delNodes!=null && delNodes.size()>0)  {
			for (delNode in delNodes){
				biomart.call("{call tm_cz.i2b2_delete_1_node($delNode,$jobID)}")
				log.info("Deleted  node: "+delNode)
				stepCt = stepCt + 1;
				writeAudit(biomart,jobID,databaseName,procedureName,'Deleted unused node:'+delNode,biomart.updateCount,stepCt,'Done');

			}

		}
//TO DO - remove comments - commented since it is throwing error now
		biomart.call("{call  tm_cz.i2b2_create_security_for_trial($trail_id, $secureStudy, $jobID)}")
		biomart.call("{call tm_cz.i2b2_load_security_data($jobID)}")
		stepCt = stepCt + 1;
		writeAudit(biomart,jobID,databaseName,procedureName,'End i2b2_load_clinical_data :',0,stepCt,'Done');
		log.info("End i2b2_load_clinical_data ")
		if(newJobFlag==1){
			biomart.call("{call tm_cz.cz_end_audit ($jobID, 'SUCCESS')}")
			
		}
		return 0
		
		
	}



	String formatValue(String dataValue){
		if(dataValue.indexOf("|", 0)==0)startPos=1
		if(dataValue.substring(endPos-2,endPos-1).equals("|"))endPos=endPos-2

		return  dataValue.substring(startPos,endPos)
	}
	public int  isInteger(String str) {
		try {
			if(str==null) return 1;
			Integer.parseInt(str);
			return 0;
		} catch (NumberFormatException nfe) {}
		return 1;
	}

}
