--------------------------------------------------------
--  File created - Thursday-October-25-2012   
--------------------------------------------------------
--------------------------------------------------------
--  DDL for Table LT_SRC_STUDY_METADATA
--------------------------------------------------------

  CREATE TABLE "TM_LZ"."LT_SRC_STUDY_METADATA" 
   (	"STUDY_ID" VARCHAR2(100 BYTE), 
	"TITLE" VARCHAR2(1000 BYTE), 
	"DESCRIPTION" VARCHAR2(2000 BYTE), 
	"DESIGN" VARCHAR2(2000 BYTE), 
	"START_DATE" VARCHAR2(50 BYTE), 
	"COMPLETION_DATE" VARCHAR2(50 BYTE), 
	"PRIMARY_INVESTIGATOR" VARCHAR2(400 BYTE), 
	"CONTACT_FIELD" VARCHAR2(400 BYTE), 
	"STATUS" VARCHAR2(100 BYTE), 
	"OVERALL_DESIGN" VARCHAR2(2000 BYTE), 
	"INSTITUTION" VARCHAR2(100 BYTE), 
	"COUNTRY" VARCHAR2(50 BYTE), 
	"BIOMARKER_TYPE" VARCHAR2(255 BYTE), 
	"TARGET" VARCHAR2(255 BYTE), 
	"ACCESS_TYPE" VARCHAR2(100 BYTE), 
	"STUDY_OWNER" VARCHAR2(510 BYTE), 
	"STUDY_PHASE" VARCHAR2(100 BYTE), 
	"BLINDING_PROCEDURE" VARCHAR2(1000 BYTE), 
	"STUDYTYPE" VARCHAR2(510 BYTE), 
	"DURATION_OF_STUDY_WEEKS" VARCHAR2(200 BYTE), 
	"NUMBER_OF_PATIENTS" VARCHAR2(200 BYTE), 
	"NUMBER_OF_SITES" VARCHAR2(200 BYTE), 
	"ROUTE_OF_ADMINISTRATION" VARCHAR2(510 BYTE), 
	"DOSING_REGIMEN" VARCHAR2(3500 BYTE), 
	"GROUP_ASSIGNMENT" VARCHAR2(510 BYTE), 
	"TYPE_OF_CONTROL" VARCHAR2(510 BYTE), 
	"PRIMARY_END_POINTS" VARCHAR2(2000 BYTE), 
	"SECONDARY_END_POINTS" VARCHAR2(3500 BYTE), 
	"INCLUSION_CRITERIA" VARCHAR2(4000 BYTE), 
	"EXCLUSION_CRITERIA" VARCHAR2(4000 BYTE), 
	"SUBJECTS" VARCHAR2(2000 BYTE), 
	"GENDER_RESTRICTION_MFB" VARCHAR2(510 BYTE), 
	"MIN_AGE" VARCHAR2(100 BYTE), 
	"MAX_AGE" VARCHAR2(100 BYTE), 
	"SECONDARY_IDS" VARCHAR2(510 BYTE), 
	"DEVELOPMENT_PARTNER" VARCHAR2(100 BYTE), 
	"GEO_PLATFORM" VARCHAR2(100 BYTE), 
	"MAIN_FINDINGS" VARCHAR2(2000 BYTE), 
	"SEARCH_AREA" VARCHAR2(100 BYTE), 
	"COMPOUND" VARCHAR2(1000 BYTE), 
	"DISEASE" VARCHAR2(1000 BYTE), 
	"PUBMED_IDS" VARCHAR2(1000 BYTE), 
	"ORGANISM" VARCHAR2(200 BYTE)
   ) SEGMENT CREATION IMMEDIATE 
  PCTFREE 10 PCTUSED 40 INITRANS 1 MAXTRANS 255 NOCOMPRESS NOLOGGING
  STORAGE(INITIAL 65536 NEXT 1048576 MINEXTENTS 1 MAXEXTENTS 2147483645
  PCTINCREASE 0 FREELISTS 1 FREELIST GROUPS 1 BUFFER_POOL DEFAULT FLASH_CACHE DEFAULT CELL_FLASH_CACHE DEFAULT)
  TABLESPACE "TRANSMART" ;


