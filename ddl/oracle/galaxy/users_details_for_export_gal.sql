--
-- Type: TABLE; Owner: GALAXY; Name: USERS_DETAILS_FOR_EXPORT_GAL
--
 CREATE TABLE "GALAXY"."USERS_DETAILS_FOR_EXPORT_GAL" 
  (	"ID" NUMBER(*,0) NOT NULL ENABLE, 
"GALAXY_KEY" VARCHAR2(100 BYTE) NOT NULL ENABLE, 
"MAIL_ADDRESS" VARCHAR2(200 BYTE) NOT NULL ENABLE, 
"USERNAME" VARCHAR2(200 BYTE) NOT NULL ENABLE, 
 CONSTRAINT "USERS_DETAILS_FOR_EXPORT_G_PK" PRIMARY KEY ("ID")
 USING INDEX
 TABLESPACE "TRANSMART"  ENABLE
  ) SEGMENT CREATION IMMEDIATE
 TABLESPACE "TRANSMART" ;

