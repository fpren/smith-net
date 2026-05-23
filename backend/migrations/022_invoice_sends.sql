-- Sub-project 2: per-action PDF send log. One row per send; the
-- pdf_sends_per_month cap counts rows in the current calendar month. Source of
-- truth for "sends" (the invoices table gets no sent_at column -- last-sent is
-- MAX(invoice_sends.sent_at)).
CREATE TABLE IF NOT EXISTS invoice_sends (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  sent_by    TEXT NOT NULL REFERENCES users(id),
  sent_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invoice_sends_sender_month
  ON invoice_sends (sent_by, sent_at);
