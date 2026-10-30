-- Local FinanceFlow development seed. Run with psql; this file uses \connect
-- and \gset and is not a generic SQL-editor script.
--
-- Required privileges: CONNECT to financeflow_identity and financeflow_db;
-- INSERT/SELECT on identity_users, financial_transactions, and budgets; and
-- USAGE on financial_transaction_seq. The tables must already exist, and the
-- budget ownership migration must have been applied to financeflow_db.
--
-- Stop FinanceFlow while running this script. FinancialTransaction uses a
-- pooled sequence allocation (allocationSize=50); stopping the app avoids
-- overlap with IDs cached by a running Hibernate process.

\set ON_ERROR_STOP on
\connect financeflow_identity

-- Add three local-development accounts. Email conflicts leave existing accounts and
-- passwords untouched; the BCrypt hash is compatible with Identity's
-- BCryptPasswordEncoder. The deterministic UUIDs are used only for accounts
-- newly inserted by this script.
BEGIN;

INSERT INTO identity_users
    (id, email, password_hash, display_name, role, created_at)
VALUES
    ('a1100000-0000-4000-8000-000000000001',
     'alice.dev@financeflow.local',
     '$2a$10$w2EDFlbrMH/uDGb9pSrgPOP57wyt0aIA9Y1p9V.AwpibWdcVPyE3i',
     'FinanceFlow Dev Alice', 'USER', CURRENT_TIMESTAMP),
    ('a1100000-0000-4000-8000-000000000002',
     'bob.dev@financeflow.local',
     '$2a$10$w2EDFlbrMH/uDGb9pSrgPOP57wyt0aIA9Y1p9V.AwpibWdcVPyE3i',
     'FinanceFlow Dev Bob', 'USER', CURRENT_TIMESTAMP),
    ('a1100000-0000-4000-8000-000000000003',
     'bewidmj@gmail.com',
     '$2a$10$w2EDFlbrMH/uDGb9pSrgPOP57wyt0aIA9Y1p9V.AwpibWdcVPyE3i',
     'FinanceFlow Dev User', 'USER', CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

DO $$
BEGIN
    IF (
        SELECT COUNT(*)
        FROM identity_users
        WHERE email IN (
            'alice.dev@financeflow.local',
            'bob.dev@financeflow.local',
            'bewidmj@gmail.com'
        )
    ) <> 3 THEN
        RAISE EXCEPTION 'FinanceFlow seed accounts could not be resolved';
    END IF;
END
$$;

-- Carry the actual IDs forward; an account that already existed may have an
-- ID different from the deterministic ID specified above.
SELECT
    (SELECT id::text FROM identity_users
     WHERE email = 'alice.dev@financeflow.local') AS user_one_id,
    (SELECT id::text FROM identity_users
     WHERE email = 'bob.dev@financeflow.local') AS user_two_id,
    (SELECT id::text FROM identity_users
     WHERE email = 'bewidmj@gmail.com') AS user_three_id
\gset

COMMIT;

\connect financeflow_db

-- Fail before inserting anything if the local schema is missing the ownership
-- migration or the sequence used by the FinancialTransaction entity.
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

-- Seed six calendar months (current month and five preceding months). Each
-- transaction has a stable description marker, so rerunning this section
-- skips seed rows already present and never updates unrelated transactions.
-- IDs come from the entity's actual sequence; the table has no ID default.
WITH seed_users(owner_id, seed_user) AS (
    VALUES
        (:'user_one_id'::uuid, 'alice'),
        (:'user_two_id'::uuid, 'bob'),
        (:'user_three_id'::uuid, 'bewidmj')
),
templates(
    category, transaction_type, day_offset,
    alice_amount, bob_amount, bewidmj_amount
) AS (
    VALUES
        ('salary',     'INCOME',  1, 6500.00::numeric, 5900.00::numeric, 7200.00::numeric),
        ('rent',       'EXPENSE', 2, 1850.00::numeric, 1650.00::numeric, 2100.00::numeric),
        ('groceries',  'EXPENSE', 5,  440.00::numeric,  390.00::numeric,  510.00::numeric),
        ('utilities',  'EXPENSE', 7,  165.00::numeric,  150.00::numeric,  185.00::numeric),
        ('dining',     'EXPENSE',10,  190.00::numeric,  155.00::numeric,  230.00::numeric),
        ('shopping',   'EXPENSE',12,  135.00::numeric,  110.00::numeric,  175.00::numeric),
        ('transport',  'EXPENSE',14,  105.00::numeric,  120.00::numeric,  145.00::numeric),
        ('healthcare', 'EXPENSE',17,   75.00::numeric,   95.00::numeric,  125.00::numeric),
        ('travel',     'EXPENSE',20,  325.00::numeric,  275.00::numeric,  460.00::numeric)
),
candidate_transactions AS (
    SELECT
        users.owner_id,
        CASE users.seed_user
            WHEN 'alice' THEN templates.alice_amount
            WHEN 'bob' THEN templates.bob_amount
            ELSE templates.bewidmj_amount
        END AS amount,
        templates.transaction_type AS type,
        templates.category,
        format(
            'FinanceFlow seed v1:%s:%s:%s',
            users.seed_user,
            to_char(months.month_start, 'YYYY-MM'),
            templates.category
        ) AS description,
        months.month_start + templates.day_offset AS transaction_date,
        month_offsets.month_offset
    FROM seed_users AS users
    CROSS JOIN generate_series(0, 5) AS month_offsets(month_offset)
    CROSS JOIN LATERAL (
        SELECT (
            date_trunc('month', CURRENT_DATE)::date
            - (month_offsets.month_offset * INTERVAL '1 month')
        )::date AS month_start
    ) AS months
    CROSS JOIN templates
    WHERE templates.category <> 'travel'
       OR month_offsets.month_offset IN (0, 2, 4)
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

-- Add this month's expense budgets. The owner-scoped unique constraint makes
-- reruns safe and allows all three users to budget the same category.
WITH seed_users(owner_id, seed_user) AS (
    VALUES
        (:'user_one_id'::uuid, 'alice'),
        (:'user_two_id'::uuid, 'bob'),
        (:'user_three_id'::uuid, 'bewidmj')
),
budget_templates(category, alice_amount, bob_amount, bewidmj_amount) AS (
    VALUES
        ('rent',       1900.00::numeric, 1700.00::numeric, 2200.00::numeric),
        ('groceries',   500.00::numeric,  450.00::numeric,  575.00::numeric),
        ('utilities',   200.00::numeric,  180.00::numeric,  225.00::numeric),
        ('dining',      250.00::numeric,  200.00::numeric,  300.00::numeric),
        ('shopping',    200.00::numeric,  175.00::numeric,  250.00::numeric),
        ('transport',   150.00::numeric,  160.00::numeric,  200.00::numeric),
        ('healthcare',  150.00::numeric,  175.00::numeric,  200.00::numeric),
        ('travel',      400.00::numeric,  350.00::numeric,  550.00::numeric)
)
INSERT INTO budgets (owner_id, category, month_start, amount)
SELECT
    users.owner_id,
    templates.category,
    date_trunc('month', CURRENT_DATE)::date,
    CASE users.seed_user
        WHEN 'alice' THEN templates.alice_amount
        WHEN 'bob' THEN templates.bob_amount
        ELSE templates.bewidmj_amount
    END
FROM seed_users AS users
CROSS JOIN budget_templates AS templates
ON CONFLICT (owner_id, category, month_start) DO NOTHING;

COMMIT;

-- Verification: seeded account count in the Identity Service database.
\connect financeflow_identity
SELECT COUNT(*) AS seed_user_count
FROM identity_users
WHERE email IN (
    'alice.dev@financeflow.local',
    'bob.dev@financeflow.local',
    'bewidmj@gmail.com'
);

SELECT id, email, role
FROM identity_users
WHERE email IN (
    'alice.dev@financeflow.local',
    'bob.dev@financeflow.local',
    'bewidmj@gmail.com'
)
ORDER BY email;

-- Verification: transaction and budget row counts per resolved user UUID.
\connect financeflow_db
WITH seed_users(owner_id, seed_user) AS (
    VALUES
        (:'user_one_id'::uuid, 'alice'),
        (:'user_two_id'::uuid, 'bob'),
        (:'user_three_id'::uuid, 'bewidmj')
)
SELECT
    users.seed_user,
    users.owner_id,
    COUNT(transactions.id) AS transaction_count,
    COUNT(transactions.id) FILTER (WHERE transactions.type = 'INCOME')
        AS income_count,
    COUNT(transactions.id) FILTER (WHERE transactions.type = 'EXPENSE')
        AS expense_count
FROM seed_users AS users
LEFT JOIN financial_transactions AS transactions
    ON transactions.owner_id = users.owner_id
   AND transactions.description LIKE
       format('FinanceFlow seed v1:%s:%%', users.seed_user)
GROUP BY users.seed_user, users.owner_id
ORDER BY users.seed_user;

WITH seed_users(owner_id, seed_user) AS (
    VALUES
        (:'user_one_id'::uuid, 'alice'),
        (:'user_two_id'::uuid, 'bob'),
        (:'user_three_id'::uuid, 'bewidmj')
)
SELECT
    users.seed_user,
    users.owner_id,
    COUNT(budgets.id) AS budget_count
FROM seed_users AS users
LEFT JOIN budgets
    ON budgets.owner_id = users.owner_id
   AND budgets.month_start = date_trunc('month', CURRENT_DATE)::date
GROUP BY users.seed_user, users.owner_id
ORDER BY users.seed_user;

-- Verification: monthly expense totals for each seed user over the seeded
-- six-month period. Only rows with this script's marker are included.
WITH seed_users(owner_id, seed_user) AS (
    VALUES
        (:'user_one_id'::uuid, 'alice'),
        (:'user_two_id'::uuid, 'bob'),
        (:'user_three_id'::uuid, 'bewidmj')
)
SELECT
    users.seed_user,
    date_trunc('month', transactions.transaction_date)::date AS month_start,
    COUNT(*) AS expense_count,
    SUM(transactions.amount) AS monthly_spending
FROM seed_users AS users
JOIN financial_transactions AS transactions
    ON transactions.owner_id = users.owner_id
WHERE transactions.type = 'EXPENSE'
  AND transactions.description LIKE
      format('FinanceFlow seed v1:%s:%%', users.seed_user)
  AND transactions.transaction_date >=
      (date_trunc('month', CURRENT_DATE) - INTERVAL '5 months')::date
  AND transactions.transaction_date <
      (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')::date
GROUP BY users.seed_user, date_trunc('month', transactions.transaction_date)
ORDER BY users.seed_user, month_start;
