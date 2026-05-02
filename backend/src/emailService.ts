/**
 * F1.4: transactional email send.
 *
 * Provider: Gmail SMTP (smtp.gmail.com:587, STARTTLS, App Password auth).
 * Daily cap ~500 sends — fine for early-stage; revisit when registrations climb.
 *
 * If SMTP env is unset, we run in DRY-RUN mode: log "would send" + the body so
 * dev can copy the verification link from console without setting up SMTP.
 * This is intentional — email is non-critical to startup, unlike JWT_SECRET.
 */

import nodemailer, { Transporter } from 'nodemailer';

const SMTP_HOST = process.env.SMTP_HOST || 'smtp.gmail.com';
const SMTP_PORT = parseInt(process.env.SMTP_PORT || '587', 10);
const SMTP_USER = process.env.SMTP_USER;
const SMTP_APP_PASSWORD = process.env.SMTP_APP_PASSWORD;
const MAIL_FROM = process.env.MAIL_FROM || (SMTP_USER ? `Smith Net <${SMTP_USER}>` : '');

const isLive = !!(SMTP_USER && SMTP_APP_PASSWORD);

let transporter: Transporter | null = null;

function getTransporter(): Transporter | null {
  if (!isLive) return null;
  if (transporter) return transporter;
  transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port: SMTP_PORT,
    secure: SMTP_PORT === 465, // 465 = implicit TLS, 587 = STARTTLS
    auth: { user: SMTP_USER!, pass: SMTP_APP_PASSWORD! },
  });
  return transporter;
}

export interface EmailMessage {
  to: string;
  subject: string;
  text: string;
  html?: string;
}

export interface SendResult {
  ok: boolean;
  dryRun: boolean;
  error?: string;
}

export async function sendEmail(msg: EmailMessage): Promise<SendResult> {
  const t = getTransporter();
  if (!t) {
    // DRY-RUN: print enough that a dev can grab the verify link out of logs.
    console.warn('[email:dry-run]', { to: msg.to, subject: msg.subject });
    console.warn('[email:dry-run] body:\n' + msg.text);
    return { ok: true, dryRun: true };
  }
  try {
    await t.sendMail({
      from: MAIL_FROM,
      to: msg.to,
      subject: msg.subject,
      text: msg.text,
      html: msg.html,
    });
    return { ok: true, dryRun: false };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error('[email:send-failed]', { to: msg.to, error: message });
    return { ok: false, dryRun: false, error: message };
  }
}

export function isEmailLive(): boolean {
  return isLive;
}

console.log(
  isLive
    ? `[Email] SMTP live via ${SMTP_HOST}:${SMTP_PORT} as ${SMTP_USER}`
    : '[Email] SMTP unset — running in dry-run mode (verification links logged to console)'
);
