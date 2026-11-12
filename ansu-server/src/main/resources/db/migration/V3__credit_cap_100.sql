-- 信用分上限从 120 调整为 100（规则变更，2026-11-12）
ALTER TABLE dorm DROP CHECK ck_dorm_credit;
ALTER TABLE dorm ADD CONSTRAINT ck_dorm_credit CHECK (credit_score BETWEEN 0 AND 100);

ALTER TABLE credit_ledger DROP CHECK ck_credit_balance;
ALTER TABLE credit_ledger ADD CONSTRAINT ck_credit_balance CHECK (balance_after BETWEEN 0 AND 100);
