--
-- Type: TABLE; Owner: BIOMART; Name: BIO_ASSAY_DATASET
--
 CREATE TABLE "BIOMART"."BIO_ASSAY_DATASET" 
  (	"BIO_ASSAY_DATASET_ID" NUMBER(18,0) NOT NULL ENABLE, 
"DATASET_NAME" NVARCHAR2(400), 
"DATASET_DESCRIPTION" NVARCHAR2(1000), 
"DATASET_CRITERIA" NVARCHAR2(1000), 
"CREATE_DATE" DATE, 
"BIO_EXPERIMENT_ID" NUMBER(18,0) NOT NULL ENABLE, 
"BIO_ASSAY_ID" NUMBER(18,0), 
"ETL_ID" NVARCHAR2(100), 
"ACCESSION" VARCHAR2(50 BYTE), 
 CONSTRAINT "BIO_DATASET_PK" PRIMARY KEY ("BIO_ASSAY_DATASET_ID")
 USING INDEX
 TABLESPACE "INDX"  ENABLE
  ) SEGMENT CREATION IMMEDIATE
 TABLESPACE "TRANSMART" ;

--
-- Type: REF_CONSTRAINT; Owner: BIOMART; Name: BIO_DATASET_EXPERIMENT_FK
--
ALTER TABLE "BIOMART"."BIO_ASSAY_DATASET" ADD CONSTRAINT "BIO_DATASET_EXPERIMENT_FK" FOREIGN KEY ("BIO_EXPERIMENT_ID")
 REFERENCES "BIOMART"."BIO_EXPERIMENT" ("BIO_EXPERIMENT_ID") ENABLE;

--
-- Type: TRIGGER; Owner: BIOMART; Name: TRG_BIO_ASSAY_DATASET_ID
--
  CREATE OR REPLACE TRIGGER "BIOMART"."TRG_BIO_ASSAY_DATASET_ID" before insert on "BIO_ASSAY_DATASET"    for each row begin     if inserting then       if :NEW."BIO_ASSAY_DATASET_ID" is null then          select SEQ_BIO_DATA_ID.nextval into :NEW."BIO_ASSAY_DATASET_ID" from dual;       end if;    end if; end;













/
ALTER TRIGGER "BIOMART"."TRG_BIO_ASSAY_DATASET_ID" ENABLE;
 
