package org.transmartproject.pipeline.util

class TransmartClinicalLoaderSQL {

	public static final insert_into_tmp_subject_info =""" insert into tm_cz.tmp_subject_info
	(usubjid,
     age_in_years_num,
     sex_cd,
     race_cd
    )
	select a.usubjid,
	      nvl(max(case when upper(a.data_label) = 'AGE'
					   then case when tm_cz.is_number(a.data_value) = 1 then 0 else to_number(a.data_value) end
		               when upper(a.data_label) like '%(AGE)' 
					   then case when tm_cz.is_number(a.data_value) = 1 then 0 else to_number(a.data_value) end
					   else null end),0) as age,
		  --nvl(max(decode(upper(a.data_label),'AGE',data_value,null)),0) as age,
		  nvl(max(case when upper(a.data_label) = 'SEX' then a.data_value
		           when upper(a.data_label) like '%(SEX)' then a.data_value
				   when upper(a.data_label) = 'GENDER' then a.data_value
				   else null end),'Unknown') as sex,
		  --max(decode(upper(a.data_label),'SEX',data_value,'GENDER',data_value,null)) as sex,
		  max(case when upper(a.data_label) = 'RACE' then a.data_value
		           when upper(a.data_label) like '%(RACE)' then a.data_value
				   else null end) as race
		  --max(decode(upper(a.data_label),'RACE',data_value,null)) as race
	from tm_wz.wrk_clinical_data a
	--where upper(a.data_label) in ('AGE','RACE','SEX','GENDER')
	group by a.usubjid"""


	public static String delete_patient_dimension="""delete tm_cz.patient_dimension_release
	where sourcesystem_cd in
		 (select distinct pd.sourcesystem_cd from tm_cz.patient_dimension_release pd
		  where pd.sourcesystem_cd like ? || ':%'
		  minus 
		  select distinct cd.usubjid from tm_wz.wrk_clinical_data cd)
	  and patient_num not in
		  (select distinct sm.patient_id from tm_cz.DE_SUBJ_SAMPLE_MAP_RELEASE sm)
		  """

	public static String update_patient_dimension ="""update tm_cz.patient_dimension_release pd
	set (sex_cd, age_in_years_num, race_cd, update_date) = 
		(select nvl(t.sex_cd,pd.sex_cd), t.age_in_years_num, nvl(t.race_cd,pd.race_cd), ?
		 from tm_cz.tmp_subject_info t
		 where t.usubjid = pd.sourcesystem_cd
		   and (coalesce(pd.sex_cd,'@') != t.sex_cd or
				pd.age_in_years_num != t.age_in_years_num or
				coalesce(pd.race_cd,'@') != t.race_cd)
		)
	where exists
		 (select 1 from tm_cz.tmp_subject_info x
		  where pd.sourcesystem_cd = x.usubjid
		    and (coalesce(pd.sex_cd,'@') != x.sex_cd or
				 pd.age_in_years_num != x.age_in_years_num or
				 coalesce(pd.race_cd,'@') != x.race_cd)
		 )
		  """
	public static String insert_into_patient_dimension="""insert into tm_cz.patient_dimension_release
    (patient_num,
     sex_cd,
     age_in_years_num,
     race_cd,
     update_date,
     download_date,
     import_date,
     sourcesystem_cd
    )
    select seq_patient_num.nextval,
		   t.sex_cd,
		   t.age_in_years_num,
		   t.race_cd,
		   ?,
		   ?,
		   ?,
		   t.usubjid
    from tm_cz.tmp_subject_info t
	where t.usubjid in 
		 (select distinct cd.usubjid from tm_cz.tmp_subject_info cd
		  minus
		  select distinct pd.sourcesystem_cd from tm_cz.patient_dimension_release pd
		  where pd.sourcesystem_cd like ? || ':%')
		  """


	public static String update_concept_dimension="""	update tm_cz.concept_dimension_release cd
		  set name_char=(select t.node_name from tm_wz.wt_trial_nodes t
						 where cd.concept_path = t.leaf_node
						   and cd.name_char != t.node_name)
		  where exists (select 1 from tm_wz.wt_trial_nodes x
						where cd.concept_path = x.leaf_node
						  and cd.name_char != x.node_name)"""



	public static String insert_into_concept_dimension=	"""insert into tm_cz.concept_dimension_release
    (concept_cd
	,concept_path
	,name_char
	,update_date
	,download_date
	,import_date
	,sourcesystem_cd
	,table_name
	)
    select concept_id.nextval
	     ,x.leaf_node
		 ,x.node_name
		 ,?
		 ,?
		 ,?
		 ,?
		 ,'CONCEPT_DIMENSION'
	from (select distinct c.leaf_node
				,to_char(c.node_name) as node_name
		  from tm_wz.wt_trial_nodes c
		  where not exists
			(select 1 from tm_cz.concept_dimension_release x
			where c.leaf_node = x.concept_path)
		 ) x"""


	public static String update_i2b2="""update i2b2metadata.i2b2 b
	set (c_name, c_columndatatype, c_metadataxml)=
		(select t.node_name, 'T'		--  temp fix until i2b2 respects c_columndatatype   t.data_type
		 ,case when t.data_type = 'T'
		       then null
			   else '<?xml version="1.0"?><ValueMetadata><Version>3.02</Version><CreationDateTime>08/14/2008 01:22:59</CreationDateTime><TestID></TestID><TestName></TestName><DataType>PosFloat</DataType><CodeType></CodeType><Loinc></Loinc><Flagstouse></Flagstouse><Oktousevalues>Y</Oktousevalues><MaxStringLength></MaxStringLength><LowofLowValue>0</LowofLowValue><HighofLowValue>0</HighofLowValue><LowofHighValue>100</LowofHighValue>100<HighofHighValue>100</HighofHighValue><LowofToxicValue></LowofToxicValue><HighofToxicValue></HighofToxicValue><EnumValues></EnumValues><CommentsDeterminingExclusion><Com></Com></CommentsDeterminingExclusion><UnitValues><NormalUnits>ratio</NormalUnits><EqualUnits></EqualUnits><ExcludingUnits></ExcludingUnits><ConvertingUnits><Units></Units><MultiplyingFactor></MultiplyingFactor></ConvertingUnits></UnitValues><Analysis><Enums /><Counts /><New /></Analysis></ValueMetadata>'
		  end
		 from tm_wz.wt_trial_nodes t
		 where b.c_fullname = t.leaf_node
		   and (b.c_name != t.node_name or b.c_columndatatype != 'T'))   --t.data_type))
	where exists
		(select 1 from tm_wz.wt_trial_nodes x
		 where b.c_fullname = x.leaf_node
		   and (b.c_name != x.node_name or b.c_columndatatype != 'T' ))   -- x.data_type))"""


	public static String	insert_into_i2b2 = """insert into i2b2metadata.i2b2
    (c_hlevel
	,c_fullname
	,c_name
	,c_visualattributes
	,c_synonym_cd
	,c_facttablecolumn
	,c_tablename
	,c_columnname
	,c_dimcode
	,c_tooltip
	,update_date
	,download_date
	,import_date
	,sourcesystem_cd
	,c_basecode
	,c_operator
	,c_columndatatype
	,c_comment
	,i2b2_id
	,c_metadataxml
	)
    select (length(c.concept_path) - nvl(length(replace(c.concept_path, '\')),0)) / length('\') - 2 + ? 
		  ,c.concept_path
		  ,c.name_char
		  ,'LA'
		  ,'N'
		  ,'CONCEPT_CD'
		  ,'CONCEPT_DIMENSION'
		  ,'CONCEPT_PATH'
		  ,c.concept_path
		  ,c.concept_path
		  ,?
		  ,?
		  ,?
		  ,c.sourcesystem_cd
		  ,c.concept_cd
		  ,'LIKE'
		  ,'T'
		  ,'trial:' || ? 
		  ,i2b2_id_seq.nextval
		  ,case when t.data_type = 'T' then null
		   else '<?xml version="1.0"?><ValueMetadata><Version>3.02</Version><CreationDateTime>08/14/2008 01:22:59</CreationDateTime><TestID></TestID><TestName></TestName><DataType>PosFloat</DataType><CodeType></CodeType><Loinc></Loinc><Flagstouse></Flagstouse><Oktousevalues>Y</Oktousevalues><MaxStringLength></MaxStringLength><LowofLowValue>0</LowofLowValue><HighofLowValue>0</HighofLowValue><LowofHighValue>100</LowofHighValue>100<HighofHighValue>100</HighofHighValue><LowofToxicValue></LowofToxicValue><HighofToxicValue></HighofToxicValue><EnumValues></EnumValues><CommentsDeterminingExclusion><Com></Com></CommentsDeterminingExclusion><UnitValues><NormalUnits>ratio</NormalUnits><EqualUnits></EqualUnits><ExcludingUnits></ExcludingUnits><ConvertingUnits><Units></Units><MultiplyingFactor></MultiplyingFactor></ConvertingUnits></UnitValues><Analysis><Enums /><Counts /><New /></Analysis></ValueMetadata>'
		   end
    from tm_cz.concept_dimension_release c
		,tm_wz.wt_trial_nodes t
    where c.concept_path = t.leaf_node
	  and not exists
		 (select 1 from i2b2metadata.i2b2 x
		  where c.concept_path = x.c_fullname)"""
	
		  
		  
	public static String delete_from_observation_fact ="""delete from tm_cz.observation_fact_release f
	where (f.modifier_cd = ? or f.sourcesystem_cd = ?)
	  and f.concept_cd not in
		 (select distinct concept_code as concept_cd from tm_cz.DE_SUBJ_SAMPLE_MAP_RELEASE
		  where trial_name = ?
		    and concept_code is not null
		  union
		  select distinct platform_cd as concept_cd from tm_cz.DE_SUBJ_SAMPLE_MAP_RELEASE
		  where trial_name = ?
		    and platform_cd is not null
		  union
		  select distinct sample_type_cd as concept_cd from tm_cz.DE_SUBJ_SAMPLE_MAP_RELEASE
		  where trial_name = ?
		    and sample_type_cd is not null
		  union
		  select distinct tissue_type_cd as concept_cd from tm_cz.DE_SUBJ_SAMPLE_MAP_RELEASE
		  where trial_name = ?
		    and tissue_type_cd is not null
		  union
		  select distinct timepoint_cd as concept_cd from tm_cz.DE_SUBJ_SAMPLE_MAP_RELEASE
		  where trial_name = ?
		    and timepoint_cd is not null
		  union
		  select distinct concept_cd as concept_cd from tm_cz.DE_SUBJ_SAMPLE_MAP_RELEASE
		  where trial_name = ?
		    and concept_cd is not null)"""	
	
			
		public static String insert_into_observation_fact = """insert into tm_cz.observation_fact_release 
	(patient_num
    ,concept_cd
    ,modifier_cd
    ,valtype_cd
    ,tval_char
    ,nval_num
    ,sourcesystem_cd
    ,import_date
    ,valueflag_cd
    ,provider_id
    ,location_cd
	,instance_num_tmp
	,start_date
	,end_date
	)
	select distinct c.patient_num
		  ,i.c_basecode
		  ,'@'
		  ,a.data_type
		  ,case when a.data_type = 'T' then a.data_value
				else 'E'  
				end
		  ,case when a.data_type = 'N' then a.data_value
				else null 
				end
		  ,?
		  ,?
		  ,'@'
		  ,'@'
		  ,'@'
		  ,row_number() over (partition by i.c_basecode, c.patient_num order by a.visit_date) as instance_num_tmp
		  ,to_date(a.visit_date,'YYYY/MM/DD HH24:mi') 
		  ,case when enr.enroll_date is null
			    then null
				else to_date(enr.enroll_date,'YYYY/MM/DD HH24:mi') end
	from tm_wz.wrk_clinical_data a
		 inner join TM_CZ.patient_dimension_release c
             on  a.usubjid = c.sourcesystem_cd
		 inner join tm_wz.wt_trial_nodes t
             on   nvl(a.category_cd,'@') = nvl(t.category_cd,'@')
             and nvl(a.data_label,'**NULL**') = nvl(t.data_label,'**NULL**')
             and nvl(a.visit_name,'**NULL**') = nvl(t.visit_name,'**NULL**')
             and decode(a.data_type,'T',a.data_value,'**NULL**') = nvl(t.data_value,'**NULL**')
		 inner join i2b2metadata.i2b2 i
             on t.leaf_node = i.c_fullname
		 left outer join tm_lz.lt_src_subj_enroll_date enr
			 on  coalesce(a.site_id,'@') = coalesce(enr.site_id,'@')
             and a.subject_id = enr.subject_id
			 and ? = enr.study_id
	where a.data_value is not null
	  and not exists		
		 (select 1 from tm_wz.wt_trial_nodes x
		  where x.leaf_node like t.leaf_node || '%_') """		
		
		  
		  
	public static String	delete_from_de_obs_enroll_days = """delete from deapp.de_obs_enroll_days
	where study_id =? """
	
	
public static String insert_into_de_obs_enroll_days = """insert into deapp.de_obs_enroll_days
	(encounter_num
	,days_since_enroll
	,study_id)
	select enc.encounter_num
		  ,round(enc.start_date-enc.end_date,3) "Tdy" 
		  ,enc.sourcesystem_cd
	from i2b2demodata.observation_fact  enc
	where enc.sourcesystem_cd = ?
	  and enc.start_date is not null
	  and enc.end_date is not null
	  and enc.encounter_num is not null"""

	  
public static String	update_i2b2_c_visualattributes= """update i2b2metadata.i2b2 a
	set c_visualattributes=(
		with upd as (select p.c_fullname, count(*) as nbr_children 
				 from i2b2metadata.i2b2 p
					 ,i2b2metadata.i2b2 c
				 where p.c_fullname like ? || '%'
				   and c.c_fullname like p.c_fullname || '%'
				 group by p.c_fullname)
		select case when u.nbr_children = 1 
					then 'L' || substr(a.c_visualattributes,2,2)
	                else 'F' || substr(a.c_visualattributes,2,1) ||
						 case when u.c_fullname = ?
						      then case when ? = 'Y' then 'J' else 'S' end  
						 else substr(a.c_visualattributes,3,1) end
			   end
		from upd u
		where a.c_fullname = u.c_fullname)
	where a.c_fullname in
		(select x.c_fullname from i2b2metadata.i2b2 x
		 where x.c_fullname like ? || '%')
"""  

public static String update_i2b2_for_upper_level_nodes="""update i2b2metadata.i2b2 b
	set sourcesystem_cd=null,c_comment=null
	where b.sourcesystem_cd = ?
	  and length(b.c_fullname) < length(?)"""

	  
public static String update_i2b2_c_visualattributes_for_P = """ update i2b2metadata.i2b2 b set c_visualattributes=substr(b.c_visualattributes,1,2) || 'P' where c_fullname = ?"""  

}
