ALTER TABLE budgets
    ADD COLUMN IF NOT EXISTS owner_id UUID;

ALTER TABLE budgets
    DROP CONSTRAINT IF EXISTS uk_budget_category_month;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_budget_owner_category_month'
          AND conrelid = 'budgets'::regclass
    ) THEN
        ALTER TABLE budgets
            ADD CONSTRAINT uk_budget_owner_category_month
            UNIQUE (owner_id, category, month_start);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_budgets_owner_id
    ON budgets (owner_id);
