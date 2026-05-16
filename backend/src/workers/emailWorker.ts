// backend/src/workers/emailWorker.ts
//
// Phase 3 Slice 3: email worker.
//
// Drains kind='email' jobs and dispatches by subkind. Today only
// 'verification' is supported; future subkinds: password reset, invoice,
// notification.
//
// Why a worker: SMTP send can take seconds-to-minutes on transient errors,
// and Phase 3 forbids inline fire-and-forget in route handlers. Register
// and resend-verification enqueue and return immediately.

import { pg, isPgEnabled } from '../db';
import { claimNext, complete, fail } from '../queue/queue';
import { sendEmail } from '../emailService';
import { requestLogger } from '../log';

const KIND = 'email';

interface VerificationPayload {
  subkind: 'verification';
  to: string;
  displayName: string;
  token: string;
  baseUrl: string;
}

type EmailPayload = VerificationPayload;

function buildVerificationEmail(displayName: string, link: string) {
  const subject = 'Verify your Smith Net account';
  const text = [
    `Hi ${displayName},`,
    '',
    'Tap to verify your email and finish setting up Smith Net:',
    link,
    '',
    'This link expires in 24 hours. If you did not create a Smith Net account, ignore this email.',
    '',
    '— Smith Net',
  ].join('\n');
  const html = `<p>Hi ${displayName},</p>
<p>Tap to verify your email and finish setting up Smith Net:</p>
<p><a href="${link}">${link}</a></p>
<p>This link expires in 24 hours. If you did not create a Smith Net account, ignore this email.</p>
<p>— Smith Net</p>`;
  return { subject, text, html };
}

export async function tick(workerId: string): Promise<boolean> {
  if (!isPgEnabled() || !pg) return false;
  const job = await claimNext(KIND, workerId);
  if (!job) return false;

  const p = job.payload as unknown as EmailPayload;
  try {
    switch (p.subkind) {
      case 'verification': {
        const link = `${p.baseUrl.replace(/\/+$/, '')}/api/auth/verify?token=${encodeURIComponent(p.token)}`;
        const { subject, text, html } = buildVerificationEmail(p.displayName, link);
        const r = await sendEmail({ to: p.to, subject, text, html });
        if (!r.ok && !r.dryRun) {
          throw new Error(r.error ?? 'sendEmail returned ok=false');
        }
        break;
      }
      default: {
        throw new Error(`unknown email subkind: ${(p as { subkind?: string }).subkind ?? 'undefined'}`);
      }
    }

    await complete(job.id);
    requestLogger().info(
      { event: 'email_sent', jobId: job.id, subkind: p.subkind, to: p.to },
      'email sent'
    );
    return true;
  } catch (err) {
    await fail(job.id, err as Error, { attempts: job.attempts, maxAttempts: job.max_attempts });
    requestLogger().warn(
      { event: 'email_send_failed', jobId: job.id, attempts: job.attempts, err: (err as Error).message },
      'email send failed'
    );
    return true;
  }
}
