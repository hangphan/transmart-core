--
-- Type: TABLE; Owner: I2B2DEMODATA; Name: QUERY_GLOBAL_TEMP
--
 CREATE GLOBAL TEMPORARY TABLE "I2B2DEMODATA"."QUERY_GLOBAL_TEMP" 
  (	"ENCOUNTER_NUM" NUMBER(22,0), 
"PATIENT_NUM" NUMBER(22,0), 
"PANEL_COUNT" NUMBER(5,0), 
"FACT_COUNT" NUMBER(22,0), 
"FACT_PANELS" NUMBER(5,0), 
"INSTANCE_NUM" NUMBER(18,0), 
"CONCEPT_CD" VARCHAR2(50 BYTE), 
"START_DATE" DATE, 
"PROVIDER_ID" VARCHAR2(50 BYTE)
  ) ON COMMIT PRESERVE ROWS ;

