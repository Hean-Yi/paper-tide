SET DEFINE OFF;
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

DECLARE
  PROCEDURE add_column_if_missing(
    p_table_name IN VARCHAR2,
    p_column_name IN VARCHAR2,
    p_column_ddl IN VARCHAR2
  ) IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = UPPER(p_table_name)
       AND COLUMN_NAME = UPPER(p_column_name);

    IF v_count = 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table_name || ' ADD (' || p_column_ddl || ')';
    END IF;
  END;

  PROCEDURE create_index_if_missing(
    p_index_name IN VARCHAR2,
    p_index_ddl IN VARCHAR2
  ) IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM USER_INDEXES
     WHERE INDEX_NAME = UPPER(p_index_name);

    IF v_count = 0 THEN
      EXECUTE IMMEDIATE p_index_ddl;
    END IF;
  END;
BEGIN
  add_column_if_missing('EXECUTION_JOB', 'LAST_ERROR_CATEGORY', 'LAST_ERROR_CATEGORY VARCHAR2(80)');
  add_column_if_missing('EXECUTION_JOB', 'LAST_ATTEMPT_AT', 'LAST_ATTEMPT_AT TIMESTAMP');
  add_column_if_missing('EXECUTION_JOB', 'COMPLETED_AT', 'COMPLETED_AT TIMESTAMP');

  create_index_if_missing(
    'IDX_EXECUTION_JOB_ERROR_UPDATED',
    'CREATE INDEX IDX_EXECUTION_JOB_ERROR_UPDATED ON EXECUTION_JOB (LAST_ERROR_CATEGORY, UPDATED_AT)'
  );
  create_index_if_missing(
    'IDX_EXECUTION_JOB_ATTEMPT_AT',
    'CREATE INDEX IDX_EXECUTION_JOB_ATTEMPT_AT ON EXECUTION_JOB (LAST_ATTEMPT_AT, EXECUTION_STATE)'
  );
END;
/
