ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS credit_card_id UUID;
