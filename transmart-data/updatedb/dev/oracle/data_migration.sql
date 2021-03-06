-- Add study blob column
alter table i2b2demodata.study add column study_blob clob;

-- Add reference to tag options in the tags table
ALTER TABLE "I2B2METADATA"."I2B2_TAGS" ADD COLUMN "TAG_OPTION_ID" NUMBER(18,0);

ALTER TABLE "I2B2METADATA"."I2B2_TAGS" ADD CONSTRAINT "I2B2_TAGS_OPTION_ID_FK" FOREIGN KEY ("TAG_OPTION_ID")
 REFERENCES "I2B2METADATA"."I2B2_TAG_OPTIONS" ("TAG_OPTION_ID") ON DELETE SET NULL ENABLE;
