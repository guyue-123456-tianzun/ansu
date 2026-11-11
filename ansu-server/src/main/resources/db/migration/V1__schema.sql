-- 安宿 · 全量版 V1 数据库结构
-- 23 表：核心 16 + RAG 6 + 认证 1
-- MySQL 8.0.16+；库内时间 UTC；接口输出 +08:00。

-- ============================================================
-- 一、主数据（3）
-- ============================================================

CREATE TABLE building (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  school_id           BIGINT UNSIGNED NOT NULL DEFAULT 1,
  code                VARCHAR(16) NOT NULL,
  name                VARCHAR(64) NOT NULL,
  free_kwh_per_person DECIMAL(4,1) NOT NULL DEFAULT 8.0,
  price_per_kwh       DECIMAL(5,3) NOT NULL DEFAULT 0.570,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_building_code (school_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dorm (
  id                BIGINT UNSIGNED NOT NULL,
  school_id         BIGINT UNSIGNED NOT NULL DEFAULT 1,
  building_id       BIGINT UNSIGNED NOT NULL,
  room_no           VARCHAR(16) NOT NULL,
  floor             INT NOT NULL,
  capacity          TINYINT NOT NULL DEFAULT 4,
  breaker_rating_w  INT NOT NULL DEFAULT 2200,
  policy_limit_w    INT NOT NULL DEFAULT 1500,
  wash_limit_w      INT NOT NULL DEFAULT 2000,
  night_limit_w     INT NOT NULL DEFAULT 400,
  energy_target_kwh DECIMAL(8,2) NULL,
  status            VARCHAR(16) NOT NULL DEFAULT 'normal',
  violation_count   INT NOT NULL DEFAULT 0,
  last_violation_at DATETIME(3) NULL,
  lock_until        DATETIME(3) NULL,
  credit_score      SMALLINT NOT NULL DEFAULT 100,
  active_alert_id   BIGINT UNSIGNED NULL,
  active_run_id     VARCHAR(16) NULL,
  version           INT NOT NULL DEFAULT 0,
  updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dorm_room (building_id, room_no),
  KEY idx_dorm_status (status),
  KEY idx_dorm_run (active_run_id),
  KEY idx_dorm_school (school_id),
  CONSTRAINT fk_dorm_building FOREIGN KEY (building_id) REFERENCES building(id),
  CONSTRAINT ck_dorm_status CHECK (status IN ('normal','warned','cut_off','locked')),
  CONSTRAINT ck_dorm_credit CHECK (credit_score BETWEEN 0 AND 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_account (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  school_id      BIGINT UNSIGNED NOT NULL DEFAULT 1,
  username       VARCHAR(64) NULL,
  password_hash  VARCHAR(100) NULL,
  role           VARCHAR(16) NOT NULL,
  display_name   VARCHAR(64) NOT NULL,
  student_no_hash CHAR(64) NULL,
  dorm_id        BIGINT UNSIGNED NULL,
  status         VARCHAR(16) NOT NULL DEFAULT 'active',
  last_login_at  DATETIME(3) NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_name (username),
  KEY idx_user_role (role),
  KEY idx_user_dorm (dorm_id),
  KEY idx_user_school (school_id),
  CONSTRAINT fk_user_dorm FOREIGN KEY (dorm_id) REFERENCES dorm(id),
  CONSTRAINT ck_user_role CHECK (role IN
    ('student','manager','counselor','repair','admin','guest')),
  CONSTRAINT ck_user_status CHECK (status IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 二、时序（1）
-- ============================================================

CREATE TABLE load_record (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dorm_id          BIGINT UNSIGNED NOT NULL,
  ts               DATETIME(3) NOT NULL,
  p_active_kw      DECIMAL(8,3) NOT NULL,
  p_reactive_kvar  DECIMAL(8,3) NULL,
  pf               DECIMAL(4,3) NULL,
  voltage_v        DECIMAL(6,2) NULL,
  current_a        DECIMAL(6,3) NULL,
  leakage_ma       SMALLINT NULL,
  temp_c           DECIMAL(5,1) NULL,
  source           VARCHAR(16) NOT NULL,
  demo_run_id      VARCHAR(16) NULL,
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_load_dorm_ts_source (dorm_id, ts, source),
  KEY idx_load_dorm_ts (dorm_id, ts),
  KEY idx_load_run_ts (demo_run_id, ts),
  KEY idx_load_source (source),
  CONSTRAINT fk_load_dorm FOREIGN KEY (dorm_id) REFERENCES dorm(id) ON DELETE CASCADE,
  CONSTRAINT ck_load_source CHECK (source IN
    ('uci_ih','uci_el','paddle','ampds2','mock_event','seed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 三、核心业务（7）
-- ============================================================

CREATE TABLE alert_log (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dorm_id           BIGINT UNSIGNED NOT NULL,
  ts                DATETIME(3) NOT NULL,
  level             VARCHAR(8) NOT NULL,
  alert_type        VARCHAR(16) NOT NULL,
  trigger_value     DECIMAL(10,3) NULL,
  threshold         DECIMAL(10,3) NULL,
  snapshot_json     JSON NULL,
  status            VARCHAR(24) NOT NULL DEFAULT 'open',
  rule_version      VARCHAR(20) NOT NULL,
  demo_run_id       VARCHAR(16) NULL,
  penalty_reverted  TINYINT NOT NULL DEFAULT 0,
  idempotency_key   VARCHAR(128) NOT NULL,
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  notified_at       DATETIME(3) NULL,
  responded_at      DATETIME(3) NULL,
  resolved_at       DATETIME(3) NULL,
  closed_at         DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_alert_idem (idempotency_key),
  KEY idx_alert_ts (ts),
  KEY idx_alert_dorm_ts (dorm_id, ts),
  KEY idx_alert_status (status),
  KEY idx_alert_run (demo_run_id),
  CONSTRAINT fk_alert_dorm FOREIGN KEY (dorm_id) REFERENCES dorm(id),
  CONSTRAINT ck_alert_level CHECK (level IN ('info','warn','urgent')),
  CONSTRAINT ck_alert_type CHECK (alert_type IN
    ('leakage','temp','overload','resistive','night_anomaly')),
  CONSTRAINT ck_alert_status CHECK (status IN
    ('open','notified','student_responded','reviewing','resolved','closed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appeal (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  alert_id         BIGINT UNSIGNED NOT NULL,
  dorm_id          BIGINT UNSIGNED NOT NULL,
  submitted_by     BIGINT UNSIGNED NOT NULL,
  reason_type      VARCHAR(16) NOT NULL,
  statement        TEXT NOT NULL,
  evidence_json    JSON NULL,
  status           VARCHAR(16) NOT NULL DEFAULT 'submitted',
  reviewer_id      BIGINT UNSIGNED NULL,
  review_note      VARCHAR(500) NULL,
  submitted_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reviewed_at      DATETIME(3) NULL,
  deadline_at      DATETIME(3) NOT NULL,
  escalated        TINYINT NOT NULL DEFAULT 0,
  escalated_at     DATETIME(3) NULL,
  original_penalty INT NOT NULL DEFAULT 0,
  idempotency_key  VARCHAR(128) NOT NULL,
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_appeal_idem (idempotency_key),
  KEY idx_appeal_status_deadline (status, deadline_at),
  KEY idx_appeal_alert (alert_id),
  CONSTRAINT fk_appeal_alert FOREIGN KEY (alert_id) REFERENCES alert_log(id),
  CONSTRAINT fk_appeal_dorm FOREIGN KEY (dorm_id) REFERENCES dorm(id),
  CONSTRAINT fk_appeal_user FOREIGN KEY (submitted_by) REFERENCES user_account(id),
  CONSTRAINT fk_appeal_reviewer FOREIGN KEY (reviewer_id) REFERENCES user_account(id),
  CONSTRAINT ck_appeal_reason CHECK (reason_type IN ('misjudge','device_fault','special')),
  CONSTRAINT ck_appeal_status CHECK (status IN
    ('submitted','reviewing','approved','rejected','withdrawn'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE action_log (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  subject_type    VARCHAR(16) NOT NULL,
  subject_id      BIGINT UNSIGNED NOT NULL,
  action          VARCHAR(32) NOT NULL,
  actor_type      VARCHAR(16) NOT NULL,
  actor_id        BIGINT UNSIGNED NULL,
  ts              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  detail_json     JSON NULL,
  request_id      VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_action_idem (idempotency_key),
  KEY idx_action_subject (subject_type, subject_id),
  KEY idx_action_ts (ts),
  CONSTRAINT ck_action_subject CHECK (subject_type IN
    ('alert','appeal','dorm','ticket','learning','notification','regulation','quiz')),
  CONSTRAINT ck_action_actor CHECK (actor_type IN
    ('system','student','manager','repair','admin','guest'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 四、RAG 学习（6）
-- ============================================================

CREATE TABLE regulation_document (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  school_id      BIGINT UNSIGNED NOT NULL DEFAULT 1,
  title          VARCHAR(200) NOT NULL,
  category       VARCHAR(32) NOT NULL DEFAULT 'school',
  version        VARCHAR(32) NOT NULL,
  file_name      VARCHAR(255) NOT NULL,
  file_path      VARCHAR(500) NOT NULL,
  file_type      VARCHAR(16) NOT NULL,
  file_size      BIGINT UNSIGNED NOT NULL,
  checksum       CHAR(64) NOT NULL,
  status         VARCHAR(16) NOT NULL DEFAULT 'uploaded',
  active         TINYINT NOT NULL DEFAULT 0,
  effective_from DATE NULL,
  effective_to   DATE NULL,
  page_count     INT NULL,
  chunk_count    INT NULL,
  uploaded_by    BIGINT UNSIGNED NOT NULL,
  error_message  VARCHAR(500) NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                   ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_reg_checksum (school_id, checksum),
  KEY idx_reg_active (school_id, active, category),
  KEY idx_reg_status (status),
  CONSTRAINT fk_reg_user FOREIGN KEY (uploaded_by) REFERENCES user_account(id),
  CONSTRAINT ck_reg_status CHECK (status IN
    ('uploaded','parsing','indexing','ready','failed','archived')),
  CONSTRAINT ck_reg_active CHECK (active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE regulation_chunk (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  document_id     BIGINT UNSIGNED NOT NULL,
  chunk_no        INT NOT NULL,
  article_no      VARCHAR(32) NULL,
  chapter         VARCHAR(200) NULL,
  page_no         INT NULL,
  content         TEXT NOT NULL,
  char_count      INT NOT NULL,
  token_count     INT NULL,
  embedding_model VARCHAR(64) NULL,
  vector_id       VARCHAR(128) NULL,
  metadata_json   JSON NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_chunk_doc_no (document_id, chunk_no),
  KEY idx_chunk_article (document_id, article_no),
  KEY idx_chunk_vector (vector_id),
  CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES regulation_document(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_paper (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  school_id           BIGINT UNSIGNED NOT NULL DEFAULT 1,
  course_type         VARCHAR(16) NOT NULL,
  alert_type          VARCHAR(16) NULL,
  title               VARCHAR(200) NOT NULL,
  variant             VARCHAR(8) NOT NULL DEFAULT 'A',
  document_set_json   JSON NOT NULL,
  status              VARCHAR(16) NOT NULL DEFAULT 'draft',
  gen_by              VARCHAR(16) NOT NULL,
  model               VARCHAR(64) NULL,
  prompt_version      VARCHAR(32) NULL,
  seed                BIGINT NULL,
  difficulty_mix      JSON NULL,
  material_md         MEDIUMTEXT NULL,
  material_citations_json JSON NULL,
  created_by          BIGINT UNSIGNED NULL,
  reviewed_by         BIGINT UNSIGNED NULL,
  published_at        DATETIME(3) NULL,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_paper_course_status (school_id, course_type, status),
  KEY idx_paper_alert (alert_type),
  CONSTRAINT fk_paper_creator FOREIGN KEY (created_by) REFERENCES user_account(id),
  CONSTRAINT fk_paper_reviewer FOREIGN KEY (reviewed_by) REFERENCES user_account(id),
  CONSTRAINT ck_paper_course CHECK (course_type IN
    ('resistive','overload','safety','general')),
  CONSTRAINT ck_paper_status CHECK (status IN ('draft','published','retired')),
  CONSTRAINT ck_paper_gen CHECK (gen_by IN ('rag','static'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_question (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  paper_id            BIGINT UNSIGNED NOT NULL,
  question_no         INT NOT NULL,
  type                VARCHAR(16) NOT NULL DEFAULT 'single',
  stem                TEXT NOT NULL,
  options_json        JSON NOT NULL,
  answer_json         JSON NOT NULL,
  explanation         TEXT NULL,
  source_chunk_ids_json JSON NULL,
  source_quotes_json  JSON NULL,
  difficulty          VARCHAR(8) NOT NULL DEFAULT 'medium',
  topic               VARCHAR(100) NULL,
  validation_status   VARCHAR(16) NOT NULL DEFAULT 'pending',
  validation_flags_json JSON NULL,
  edited_by           BIGINT UNSIGNED NULL,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_no (paper_id, question_no),
  KEY idx_question_topic (topic),
  KEY idx_question_status (validation_status),
  CONSTRAINT fk_question_paper FOREIGN KEY (paper_id) REFERENCES quiz_paper(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_question_editor FOREIGN KEY (edited_by) REFERENCES user_account(id),
  CONSTRAINT ck_question_type CHECK (type IN ('single','multiple','true_false')),
  CONSTRAINT ck_question_diff CHECK (difficulty IN ('easy','medium','hard')),
  CONSTRAINT ck_question_valid CHECK (validation_status IN
    ('pending','passed','failed','approved'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ingestion_job (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  document_id   BIGINT UNSIGNED NOT NULL,
  job_type      VARCHAR(16) NOT NULL DEFAULT 'parse',
  status        VARCHAR(16) NOT NULL DEFAULT 'queued',
  progress      INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  started_at    DATETIME(3) NULL,
  finished_at   DATETIME(3) NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_job_status (status, created_at),
  KEY idx_job_doc (document_id),
  CONSTRAINT fk_job_doc FOREIGN KEY (document_id) REFERENCES regulation_document(id)
    ON DELETE CASCADE,
  CONSTRAINT ck_job_type CHECK (job_type IN ('parse','embed','reindex')),
  CONSTRAINT ck_job_status CHECK (status IN ('queued','running','success','failed')),
  CONSTRAINT ck_job_progress CHECK (progress BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 五、学习、信用、报修、通知、简报、问答、演示、数据（9）
-- ============================================================

CREATE TABLE learning_record (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  dorm_id         BIGINT UNSIGNED NOT NULL,
  alert_id        BIGINT UNSIGNED NOT NULL,
  course_id       VARCHAR(32) NOT NULL,
  course_type     VARCHAR(16) NOT NULL,
  quiz_id         BIGINT UNSIGNED NULL,
  paper_variant   VARCHAR(8) NULL,
  score           INT NOT NULL,
  answers_json    JSON NOT NULL,
  citations_json  JSON NULL,
  material_viewed TINYINT NOT NULL DEFAULT 0,
  passed          TINYINT NOT NULL,
  attempt_no      INT NOT NULL DEFAULT 1,
  credit_awarded  INT NOT NULL DEFAULT 0,
  certificate_id  VARCHAR(64) NULL,
  completed_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  idempotency_key VARCHAR(128) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_learning_idem (idempotency_key),
  UNIQUE KEY uk_learning_attempt (user_id, course_id, alert_id, attempt_no),
  KEY idx_learning_dorm_time (dorm_id, completed_at),
  KEY idx_learning_quiz (quiz_id),
  CONSTRAINT fk_learning_user FOREIGN KEY (user_id) REFERENCES user_account(id),
  CONSTRAINT fk_learning_dorm FOREIGN KEY (dorm_id) REFERENCES dorm(id),
  CONSTRAINT fk_learning_alert FOREIGN KEY (alert_id) REFERENCES alert_log(id),
  CONSTRAINT fk_learning_quiz FOREIGN KEY (quiz_id) REFERENCES quiz_paper(id),
  CONSTRAINT ck_learning_score CHECK (score BETWEEN 0 AND 100),
  CONSTRAINT ck_learning_attempt CHECK (attempt_no BETWEEN 1 AND 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE credit_ledger (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dorm_id         BIGINT UNSIGNED NOT NULL,
  delta           INT NOT NULL,
  reason          VARCHAR(24) NOT NULL,
  ref_type        VARCHAR(24) NULL,
  ref_id          BIGINT UNSIGNED NULL,
  balance_after   INT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_credit_idem (idempotency_key),
  KEY idx_credit_dorm_time (dorm_id, created_at),
  CONSTRAINT fk_credit_dorm FOREIGN KEY (dorm_id) REFERENCES dorm(id),
  CONSTRAINT ck_credit_reason CHECK (reason IN
    ('violation','learning','no_violation','appeal_revert','repair','admin_adjust')),
  CONSTRAINT ck_credit_balance CHECK (balance_after BETWEEN 0 AND 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE repair_ticket (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dorm_id        BIGINT UNSIGNED NOT NULL,
  user_id        BIGINT UNSIGNED NOT NULL,
  category       VARCHAR(16) NOT NULL,
  description    VARCHAR(500) NOT NULL,
  photo_url      VARCHAR(500) NULL,
  status         VARCHAR(16) NOT NULL DEFAULT 'open',
  assignee_id    BIGINT UNSIGNED NULL,
  rating         TINYINT NULL,
  rating_comment VARCHAR(255) NULL,
  accepted_at    DATETIME(3) NULL,
  resolved_at    DATETIME(3) NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                   ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_repair_dorm_time (dorm_id, created_at),
  KEY idx_repair_status (status),
  KEY idx_repair_assignee (assignee_id),
  CONSTRAINT fk_repair_dorm FOREIGN KEY (dorm_id) REFERENCES dorm(id),
  CONSTRAINT fk_repair_user FOREIGN KEY (user_id) REFERENCES user_account(id),
  CONSTRAINT fk_repair_assignee FOREIGN KEY (assignee_id) REFERENCES user_account(id),
  CONSTRAINT ck_repair_category CHECK (category IN ('socket','trip','heat','other')),
  CONSTRAINT ck_repair_status CHECK (status IN
    ('open','accepted','processing','done','closed')),
  CONSTRAINT ck_repair_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  type            VARCHAR(16) NOT NULL,
  title           VARCHAR(120) NOT NULL,
  body            VARCHAR(500) NOT NULL,
  ref_type        VARCHAR(24) NOT NULL,
  ref_id          BIGINT UNSIGNED NOT NULL DEFAULT 0,
  read_at         DATETIME(3) NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'pending',
  retry_count     INT NOT NULL DEFAULT 0,
  next_retry_at   DATETIME(3) NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_notify_idem (idempotency_key),
  KEY idx_notify_user_read (user_id, read_at),
  KEY idx_notify_retry (status, next_retry_at),
  CONSTRAINT fk_notify_user FOREIGN KEY (user_id) REFERENCES user_account(id),
  CONSTRAINT ck_notify_type CHECK (type IN
    ('alert','appeal','learning','repair','briefing','system')),
  CONSTRAINT ck_notify_status CHECK (status IN ('pending','sent','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE briefing (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  date         DATE NOT NULL,
  building_id  BIGINT UNSIGNED NOT NULL,
  type         VARCHAR(16) NOT NULL DEFAULT 'daily',
  content_md   MEDIUMTEXT NOT NULL,
  gen_by       VARCHAR(8) NOT NULL,
  model        VARCHAR(32) NULL,
  latency_ms   INT NULL,
  tokens       INT NULL,
  guard_json   JSON NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'draft',
  pushed_at    DATETIME(3) NULL,
  channel      VARCHAR(16) NULL,
  created_by   BIGINT UNSIGNED NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_briefing_date_type (date, type),
  CONSTRAINT fk_briefing_building FOREIGN KEY (building_id) REFERENCES building(id),
  CONSTRAINT fk_briefing_creator FOREIGN KEY (created_by) REFERENCES user_account(id),
  CONSTRAINT ck_briefing_type CHECK (type IN ('daily','weekly','notice')),
  CONSTRAINT ck_briefing_gen CHECK (gen_by IN ('llm','tmpl')),
  CONSTRAINT ck_briefing_status CHECK (status IN ('draft','confirmed','archived'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qa_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  dorm_id       BIGINT UNSIGNED NULL,
  question      VARCHAR(500) NOT NULL,
  intent        VARCHAR(24) NOT NULL,
  slots_json    JSON NULL,
  hit_source    VARCHAR(16) NOT NULL,
  answer        TEXT NOT NULL,
  citations_json JSON NULL,
  rag_trace_id  VARCHAR(64) NULL,
  latency_ms    INT NULL,
  llm_ok        TINYINT NOT NULL DEFAULT 0,
  request_id    VARCHAR(64) NOT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_qa_user_time (user_id, created_at),
  CONSTRAINT fk_qa_user FOREIGN KEY (user_id) REFERENCES user_account(id),
  CONSTRAINT ck_qa_hit CHECK (hit_source IN ('rule','llm','fallback','rag'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE demo_run (
  id          VARCHAR(16) NOT NULL,
  scene       VARCHAR(16) NOT NULL,
  seed        BIGINT NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'created',
  params_json JSON NULL,
  started_at  DATETIME(3) NULL,
  reset_at    DATETIME(3) NULL,
  ended_at    DATETIME(3) NULL,
  created_by  BIGINT UNSIGNED NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_run_status (status),
  CONSTRAINT fk_run_creator FOREIGN KEY (created_by) REFERENCES user_account(id),
  CONSTRAINT ck_run_scene CHECK (scene IN ('normal','violation','appeal','night')),
  CONSTRAINT ck_run_status CHECK (status IN ('created','active','archived'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE data_import (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  source       VARCHAR(16) NOT NULL,
  file_name    VARCHAR(255) NOT NULL,
  rows_total   INT NOT NULL DEFAULT 0,
  rows_clean   INT NOT NULL DEFAULT 0,
  rows_dropped INT NOT NULL DEFAULT 0,
  scale_factor DECIMAL(10,6) NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'running',
  checksum     VARCHAR(64) NULL,
  started_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at  DATETIME(3) NULL,
  note         VARCHAR(255) NULL,
  PRIMARY KEY (id),
  KEY idx_import_source_time (source, started_at),
  CONSTRAINT ck_import_status CHECK (status IN ('running','success','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE threshold_version (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  version        VARCHAR(20) NOT NULL,
  yaml_text      MEDIUMTEXT NOT NULL,
  checksum       CHAR(64) NOT NULL,
  active         TINYINT NOT NULL DEFAULT 0,
  effective_from DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by     BIGINT UNSIGNED NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_threshold_version (version),
  KEY idx_threshold_active (active),
  CONSTRAINT fk_threshold_creator FOREIGN KEY (created_by) REFERENCES user_account(id),
  CONSTRAINT ck_threshold_active CHECK (active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 六、RAG 日志（1）
-- ============================================================

CREATE TABLE rag_query_log (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id            VARCHAR(64) NOT NULL,
  user_id               BIGINT UNSIGNED NULL,
  scene                 VARCHAR(16) NOT NULL,
  query_text            TEXT NOT NULL,
  retrieved_chunk_ids_json JSON NULL,
  answer                TEXT NULL,
  citations_json        JSON NULL,
  model                 VARCHAR(64) NULL,
  latency_ms            INT NULL,
  hit_source            VARCHAR(16) NOT NULL,
  guard_json            JSON NULL,
  created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_rag_user_time (user_id, created_at),
  KEY idx_rag_scene (scene),
  CONSTRAINT fk_rag_user FOREIGN KEY (user_id) REFERENCES user_account(id),
  CONSTRAINT ck_rag_scene CHECK (scene IN ('qa','quiz_gen','material','ingest')),
  CONSTRAINT ck_rag_hit CHECK (hit_source IN ('rag','llm','fallback','none'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 七、认证（1）
-- ============================================================

CREATE TABLE auth_refresh_token (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  token_hash  CHAR(64) NOT NULL,
  expires_at  DATETIME(3) NOT NULL,
  revoked_at  DATETIME(3) NULL,
  user_agent  VARCHAR(255) NULL,
  ip_hash     CHAR(64) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_hash (token_hash),
  KEY idx_refresh_user (user_id),
  KEY idx_refresh_expire (expires_at),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES user_account(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
