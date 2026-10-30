-- Incremental local-development seed for bewidmj@gmail.com only.
-- Run with psql because this file uses \connect and \gset.
--
-- This does not reseed Alice or Bob. An existing Identity account and its
-- password are preserved. Existing rows with this script's seed markers are
-- skipped, making repeated runs safe.
--
-- Stop FinanceFlow while running: FinancialTransaction uses a sequence with
-- Hibernate allocationSize=50, so a running app may have cached transaction
-- IDs. Requires CONNECT to both databases, INSERT/SELECT on the tables below,
-- and USAGE on financial_transaction_seq. The budget ownership migration
-- must already be applied to financeflow_db.

\set ON_ERROR_STOP on
\connect financeflow_identity

BEGIN;

INSERT INTO identity_users
    (id, email, password_hash, display_name, role, created_at)
VALUES
    ('a1100000-0000-4000-8000-000000000003',
     'bewidmj@gmail.com',
     '$2a$10$w2EDFlbrMH/uDGb9pSrgPOP57wyt0aIA9Y1p9V.AwpibWdcVPyE3i',
     'FinanceFlow Dev User', 'USER', CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

SELECT id::text AS bewidmj_user_id
FROM identity_users
WHERE email = 'bewidmj@gmail.com'
\gset

COMMIT;

\connect financeflow_db

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'financial_transactions'
          AND column_name = 'owner_id'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'budgets'
          AND column_name = 'owner_id'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'budgets'::regclass
          AND conname = 'uk_budget_owner_category_month'
    ) OR EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'budgets'::regclass
          AND conname = 'uk_budget_category_month'
    ) OR to_regclass('financial_transaction_seq') IS NULL THEN
        RAISE EXCEPTION
            'Apply the FinanceFlow budget ownership migration and verify the transaction sequence first';
    END IF;
END
$$;

BEGIN;

-- Current month and five prior months, with stable per-month descriptions.
-- IDs are allocated from the FinancialTransaction entity's sequence.
WITH templates(category, transaction_type, day_offset, amount) AS (
    VALUES
        ('salary',     'INCOME',  1, 7200.00::numeric),
        ('rent',       'EXPENSE', 2, 2100.00::numeric),
        ('groceries',  'EXPENSE', 5,  510.00::numeric),
        ('utilities',  'EXPENSE', 7,  185.00::numeric),
        ('dining',     'EXPENSE',10,  230.00::numeric),
        ('shopping',   'EXPENSE',12,  175.00::numeric),
        ('transport',  'EXPENSE',14,  145.00::numeric),
        ('healthcare', 'EXPENSE',17,  125.00::numeric),
        ('travel',     'EXPENSE',20,  460.00::numeric)
),
candidate_transactions AS (
    SELECT
        :'bewidmj_user_id'::uuid AS owner_id,
        templates.amount,
        templates.transaction_type AS type,
        templates.category,
        format(
            'FinanceFlow seed v1:bewidmj:%s:%s',
            to_char(months.month_start, 'YYYY-MM'),
            templates.category
        ) AS description,
        months.month_start + templates.day_offset AS transaction_date
    FROM generate_series(0, 5) AS offsets(month_offset)
    CROSS JOIN LATERAL (
        SELECT (
            date_trunc('month', CURRENT_DATE)::date
            - (offsets.month_offset * INTERVAL '1 month')
        )::date AS month_start
    ) AS months
    CROSS JOIN templates
    WHERE templates.category <> 'travel'
       OR offsets.month_offset IN (0, 2, 4)
)
INSERT INTO financial_transactions
    (id, owner_id, amount, type, category, description, transaction_date)
SELECT
    nextval('financial_transaction_seq'),
    candidates.owner_id,
    candidates.amount,
    candidates.type,
    candidates.category,
    candidates.description,
    candidates.transaction_date
FROM candidate_transactions AS candidates
WHERE NOT EXISTS (
    SELECT 1
    FROM financial_transactions AS existing
    WHERE existing.owner_id = candidates.owner_id
      AND existing.description = candidates.description
);

-- This month's expense budgets; existing owner/category/month budgets remain
-- unchanged, and no other user's budgets are affected.
INSERT INTO budgets (owner_id, category, month_start, amount)
SELECT
    :'bewidmj_user_id'::uuid,
    budget.category,
    date_trunc('month', CURRENT_DATE)::date,
    budget.amount
FROM (
    VALUES
        ('rent',       2200.00::numeric),
        ('groceries',   575.00::numeric),
        ('utilities',   225.00::numeric),
        ('dining',      300.00::numeric),
        ('shopping',    250.00::numeric),
        ('transport',   200.00::numeric),
        ('healthcare',  200.00::numeric),
        ('travel',      550.00::numeric)
) AS budget(category, amount)
ON CONFLICT (owner_id, category, month_start) DO NOTHING;

COMMIT;

-- Verification for the added account and its six-month seed data.
\connect financeflow_identity
SELECT id, email, role
FROM identity_users
WHERE email = 'bewidmj@gmail.com';

\connect financeflow_db
SELECT
    owner_id,
    COUNT(*) AS transaction_count,
    COUNT(*) FILTER (WHERE type = 'INCOME') AS income_count,
    COUNT(*) FILTER (WHERE type = 'EXPENSE') AS expense_count
FROM financial_transactions
WHERE owner_id = :'bewidmj_user_id'::uuid
  AND description LIKE 'FinanceFlow seed v1:bewidmj:%'
GROUP BY owner_id;

SELECT
    owner_id,
    COUNT(*) AS current_month_budget_count
FROM budgets
WHERE owner_id = :'bewidmj_user_id'::uuid
  AND month_start = date_trunc('month', CURRENT_DATE)::date
GROUP BY owner_id;

SELECT
    date_trunc('month', transaction_date)::date AS month_start,
    COUNT(*) AS expense_count,
    SUM(amount) AS monthly_spending
FROM financial_transactions
WHERE owner_id = :'bewidmj_user_id'::uuid
  AND type = 'EXPENSE'
  AND description LIKE 'FinanceFlow seed v1:bewidmj:%'
  AND transaction_date >=
      (date_trunc('month', CURRENT_DATE) - INTERVAL '5 months')::date
  AND transaction_date <
      (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')::date
GROUP BY date_trunc('month', transaction_date)
ORDER BY month_start;
