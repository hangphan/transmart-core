migrate:
	$(PGSQL_BIN)/psql -f import_xnat_configuration.sql
	$(PGSQL_BIN)/psql -f import_xnat_variable.sql
	$(PGSQL_BIN)/psql -f xnat_subject.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_load_clinical_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_load_rbm_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_load_study_metadata.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_process_acgh_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_process_metabolomic_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_process_mrna_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_process_proteomics_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_process_qpcr_mirna_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_process_rna_seq_data.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_proteomics_zscore_calc.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_rename_node.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_rna_seq_zscore_calc.sql
	$(PGSQL_BIN)/psql -f functions/i2b2_secure_study.sql
	$(PGSQL_BIN)/psql -f functions/rdc_reload_mrna_data.sql
	$(PGSQL_BIN)/psql -f functions/upgrade_mrna_data.sql
	$(PGSQL_BIN)/psql -f alter.sql
