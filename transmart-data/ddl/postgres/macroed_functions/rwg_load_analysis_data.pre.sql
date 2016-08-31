<?php
	$RETURN_METHOD = 'OUTVAR'; /* RETURN or OUTVAR */
	require __DIR__ . '/../_scripts/macros.php';
?>
-- Generated by Ora2Pg, the Oracle database Schema converter, version 11.4
-- Copyright 2000-2013 Gilles DAROLD. All rights reserved.
-- DATASOURCE: dbi:Oracle:host=mydb.mydom.fr;sid=SIDNAME


CREATE OR REPLACE FUNCTION tm_cz.rwg_load_analysis_data (
  trialID text
 ,currentJobID bigint DEFAULT null
 ,inPlatformID bigint DEFAULT null
 ,OUT rtn_code bigint
)
 RETURNS bigint AS $body$
DECLARE

/*************************************************************************
* Copyright 2008-2012 Janssen Research & Development, LLC.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************/
--Audit variables
	<?php standard_vars() ?>

	vWZcount      integer;
	vLZcount      integer;
	vPlatformID   integer;
	vExpID        integer;

	cDelete       CURSOR FOR
		SELECT distinct bio_assay_analysis_id
			FROM tm_lz.Rwg_Analysis
			-- From BIOMART.bio_analysis_cohort_xref
			WHERE upper(study_id) = upper(trialID);

    cDeleteRow    record; --type of cDelete rows

BEGIN
	<?php func_start('RWG_LOAD_ANALYSIS_DATA') ?>

	FOR cDeleteRow IN cDelete
	LOOP
		<?php step_begin() ?>
		DELETE FROM BIOMART.bio_assay_analysis_data
		WHERE bio_assay_analysis_id = cDeleteRow.bio_assay_analysis_id;
		<?php step_end("'Delete records from BIOMART.bio_assay_analysis_data for analysis:  ' || cDeleteRow.bio_assay_analysis_id") ?>

		<?php step_begin() ?>
		DELETE FROM tm_lz.rwg_analysis_data
		WHERE bio_assay_analysis_id = cDeleteRow.bio_assay_analysis_id;
		<?php step_end("'Delete records from BIOMART.bio_assay_analysis_data for analysis:  ' || cDeleteRow.bio_assay_analysis_id") ?>
	END LOOP;

	<?php step_begin() ?>
	TRUNCATE TABLE tm_wz.bio_assay_analysis_data_new;
	<?php step_end('Truncate tm_wz.bio_assay_analysis_data_new') ?>

	<?php step_begin() ?>
	TRUNCATE TABLE tm_wz.tmp_assay_analysis_metrics;
	<?php step_end('Truncate tm_wz.tmp_assay_analysis_metrics') ?>

	-- not used ???
	--delete from tm_lz.RWG_BAAD_ID where upper(study_id) =upper(trialID);
	--Cz_Write_Audit(Jobid,Databasename,Procedurename,'Delete existing records from tm_lz.RWG_BAAD_ID',rowCt,Stepct,'Done');
	--stepCt := stepCt + 1;
	IF (coalesce(inPlatformID::text, '') = '')
		THEN

		<?php step_begin() ?>
		SELECT max(bap.bio_assay_platform_id) INTO vPlatformID
		FROM DEAPP.de_subject_sample_mapping ssm, DEAPP.de_gpl_info gpl, BIOMART.bio_assay_platform bap
		WHERE upper(ssm.gpl_id) = upper(gpl.platform)
			AND upper(ssm.trial_name) = upper(trialID)
			AND ssm.platform = 'MRNA_AFFYMETRIX'
			AND upper(bap.platform_accession) LIKE '%'|| upper(gpl.platform) || '%';
		<?php step_end("'Get bio_assay_platform_ID: ' || vPlatformID") ?>
	ELSE
		vPlatformID := inPlatformID;
	END IF;

	<?php step_begin() ?>
	SELECT exp.bio_experiment_id INTO vExpID
	FROM BIOMART.bio_experiment exp
	WHERE upper(accession) = upper(trialID);
	<?php step_end("'Get bio_experiment_id: ' || vExpID") ?>

	<?php step_begin() ?>
	SELECT count(*) INTO vLZcount FROM TM_LZ.RWG_ANALYSIS_DATA_EXT;
	<?php step_end("'Count for TM_LZ.RWG_ANALYSIS_DATA_EXT = ' || vLZcount", 0) ?>

	--	count number of data records with non-numeric data in preferred_pvalue or fold_change and log
	<?php step_begin() ?>
	SELECT COUNT (*)
	INTO vLZcount
	FROM tm_lz.rwg_analysis_data_ext
	WHERE
		is_numeric(preferred_pvalue) = 1
		OR is_numeric(fold_change) = 1;
	<?php step_end('Data records dropped for non-numeric preferred_pvalue or fold_change', 'vLZcount') ?>

	--	insert data into rwg_analysis_data, skip records with non-numeric data in preferred_pvalue or fold_change
	--	change all other non-numeric data to null
	<?php step_begin() ?>
	INSERT INTO tm_lz.rwg_analysis_data (
		study_id,
		probeset,
		fold_change,
		pvalue,
		raw_pvalue,
		adjusted_pvalue,
		min_lsmean,
		max_lsmean,
		analysis_cd,
		bio_assay_analysis_id )
	SELECT
		rwg.study_id,
		ext.probeset,
		ext.fold_change::double precision,
		ext.preferred_pvalue::double precision,
		CASE
			WHEN is_numeric ( ext.raw_pvalue ) = 1 THEN NULL
			ELSE ext.raw_pvalue
		END::double precision
		,
		CASE
			WHEN is_numeric ( ext.adjusted_pvalue ) = 1 THEN NULL
			ELSE ext.adjusted_pvalue
		END::double precision
		,
		CASE
			WHEN is_numeric ( ext.lsmean_1 ) = 1 OR is_numeric ( ext.lsmean_1 ) = 1 THEN NULL
			WHEN ext.lsmean_1 > ext.lsmean_2 THEN ext.lsmean_2
			ELSE ext.lsmean_1
		END::double precision --min
		,
		CASE
			WHEN is_numeric ( ext.lsmean_1 ) = 1
			OR is_numeric ( ext.lsmean_1 ) = 1 THEN NULL
			WHEN ext.lsmean_1 > ext.lsmean_2 THEN ext.lsmean_1
			ELSE ext.lsmean_2
		END::double precision --max
		,
		ext.analysis_id,
		rwg.bio_assay_analysis_id
	FROM
		TM_LZ.RWG_ANALYSIS_DATA_EXT ext,
		tm_lz.rwg_analysis rwg
	WHERE
		TRIM ( UPPER ( ext.analysis_id ) ) = TRIM ( UPPER ( rwg.analysis_id ) )
		AND UPPER ( rwg.study_id ) = UPPER ( trialID )
		AND is_numeric ( ext.preferred_pvalue ) = 0
		AND is_numeric ( ext.fold_change ) = 0;
	<?php step_end('Insert records into rwg_analysis_data') ?>

	<?php step_begin() ?>
	SELECT COUNT(*) INTO vWZcount FROM tm_lz.rwg_analysis_data
	WHERE study_id = upper(trialID);
	<?php step_end("'Count for tm_lz.rwg_analysis_data = ' || vWZcount", 0) ?>


	<?php step_begin() ?>
	INSERT INTO tm_wz.BIO_ASSAY_ANALYSIS_DATA_NEW (
		fold_change_ratio,
		raw_pvalue,
		adjusted_pvalue,
		bio_assay_analysis_id,
		feature_group_name,
		bio_experiment_id,
		bio_assay_platform_id,
		etl_id,
		preferred_pvalue,
		lsmean1,
		lsmean2,
		bio_assay_feature_group_id )
	SELECT
		rad.fold_change,
		rad.raw_pvalue,
		rad.adjusted_pvalue,
		rad.bio_assay_analysis_id,
		rad.probeset,
		vExpID,
		vPlatformID,
		rad.study_id || ':RWG',
		rad.pvalue,
		rad.min_lsmean,
		rad.max_lsmean,
		bafg.bio_assay_feature_group_id
	FROM
		tm_lz.rwg_analysis_data rad
		LEFT JOIN BIOMART.bio_assay_feature_group bafg ON ( rad.probeset = bafg.feature_group_name )
	WHERE
		rad.study_id = UPPER ( trialID ); -- 20121212 JEA
	<?php step_end('Insert records into BIO_ASSAY_ANALYSIS_DATA_NEW') ?>

	/*Calculate TEA Values */
	<?php step_begin() ?>
	INSERT INTO tm_wz.tmp_assay_analysis_metrics
	SELECT
		ad.bio_assay_analysis_id,
		COUNT ( * )
		data_ct,
		AVG ( ad.fold_change_ratio )
		fc_mean,
		Stddev ( Ad.Fold_Change_Ratio )
		Fc_Stddev
	FROM
		tm_wz.BIO_ASSAY_ANALYSIS_DATA_NEW ad
		JOIN biomart.bio_assay_analysis a ON ad.bio_assay_analysis_id = a.bio_assay_analysis_id
	WHERE ( ad.fold_change_ratio IS NOT NULL
		AND ad.fold_change_ratio::text <> '' )
		AND a.bio_assay_data_type <> 'RBM'
	GROUP BY
		ad.bio_assay_analysis_id
	ORDER BY
		data_ct;
	<?php step_end('Insert records into tm_wz.tmp_assay_analysis_metrics ') ?>

	<?php step_begin() ?>
	UPDATE tm_wz.BIO_ASSAY_ANALYSIS_DATA_NEW AS d
	SET
		tea_normalized_pvalue = TEA_NPV_PRECOMPUTE(d.fold_change_ratio, m.fc_mean, m.fc_stddev )
	FROM
		tm_wz.tmp_assay_analysis_metrics m
	WHERE d.bio_assay_analysis_id = m.bio_assay_analysis_id
		AND ( d.fold_change_ratio IS NOT NULL
			AND d.fold_change_ratio ::text <> '');
	<?php step_end('Update TEA records in tm_lz.BIO_ASSAY_ANALYSIS_DATA_NEW') ?>

	/* Final Insert */
	SET search_path = "$user", public, biomart;
	FOR cDeleterow IN cdElete LOOP
		<?php step_begin() ?>
		INSERT INTO biomart.bio_assay_analysis_data(
			FOLD_CHANGE_RATIO, RAW_PVALUE,ADJUSTED_PVALUE,BIO_ASSAY_ANALYSIS_ID,
			FEATURE_GROUP_NAME,BIO_EXPERIMENT_ID,BIO_ASSAY_PLATFORM_ID ,
			ETL_ID,PREFERRED_PVALUE,TEA_NORMALIZED_PVALUE,BIO_ASSAY_FEATURE_GROUP_ID,
			LSMEAN1,LSMEAN2 )
		SELECT
		FOLD_CHANGE_RATIO,RAW_PVALUE,ADJUSTED_PVALUE,BIO_ASSAY_ANALYSIS_ID,
		FEATURE_GROUP_NAME,BIO_EXPERIMENT_ID,BIO_ASSAY_PLATFORM_ID ,
		ETL_ID,PREFERRED_PVALUE,TEA_NORMALIZED_PVALUE,BIO_ASSAY_FEATURE_GROUP_ID,
		LSMEAN1,LSMEAN2
		FROM tm_wz.BIO_ASSAY_ANALYSIS_DATA_NEW
		WHERE bio_assay_analysis_id = cDeleteRow.bio_assay_analysis_id;

		<?php step_end("'Insert records into biomart.bio_assay_analysis_data for analysis:  ' || cDeleteRow.bio_assay_analysis_id") ?>
	END LOOP;

	PERFORM cz_write_audit(jobId,databaseName,procedureName,'FUNCTION Complete',0,stepCt,'Done');

	---Cleanup OVERALL JOB if this proc is being run standalone
	IF newJobFlag = 1
		THEN
		PERFORM cz_end_audit (jobID, 'SUCCESS');
	END IF;
EXCEPTION
	WHEN OTHERS THEN
	<?php error_handle_body(); ?>
END;

$body$
LANGUAGE PLPGSQL;

