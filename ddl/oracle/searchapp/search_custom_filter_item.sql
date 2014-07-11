--
-- Type: TABLE; Owner: SEARCHAPP; Name: SEARCH_CUSTOM_FILTER_ITEM
--
 CREATE TABLE "SEARCHAPP"."SEARCH_CUSTOM_FILTER_ITEM" 
  (	"SEARCH_CUSTOM_FILTER_ITEM_ID" NUMBER(18,0) NOT NULL ENABLE, 
"SEARCH_CUSTOM_FILTER_ID" NUMBER(18,0) NOT NULL ENABLE, 
"UNIQUE_ID" VARCHAR2(200 CHAR) NOT NULL ENABLE, 
"BIO_DATA_TYPE" VARCHAR2(100 CHAR) NOT NULL ENABLE, 
 CONSTRAINT "SEARCH_CUST_FIL_ITEM_PK" PRIMARY KEY ("SEARCH_CUSTOM_FILTER_ITEM_ID")
 USING INDEX
 TABLESPACE "TRANSMART"  ENABLE
  ) SEGMENT CREATION IMMEDIATE
 TABLESPACE "TRANSMART" ;

--
-- Type: TRIGGER; Owner: SEARCHAPP; Name: TRG_SEARCH_CUST_FIL_ITEM_ID
--
  CREATE OR REPLACE TRIGGER "SEARCHAPP"."TRG_SEARCH_CUST_FIL_ITEM_ID" 
  before insert on "SEARCH_CUSTOM_FILTER_ITEM" for each row
begin 
    if inserting then if :NEW."SEARCH_CUSTOM_FILTER_ITEM_ID" is null then select SEQ_SEARCH_DATA_ID.nextval into :NEW."SEARCH_CUSTOM_FILTER_ITEM_ID" from dual; end if; end if;
end;










/
ALTER TRIGGER "SEARCHAPP"."TRG_SEARCH_CUST_FIL_ITEM_ID" ENABLE;
 
