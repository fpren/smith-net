import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { idempotency } from './middleware/idempotency';
import { requireExpenseOwner, ExpenseOwnerRequest } from './middleware/requireExpenseOwner';
import { CreateExpenseBody, UpdateExpenseBody } from './schemas/expenses';
import * as expensesService from './expensesService';
import * as jobsService from './jobsService';
import { requestLogger } from './log';

export const expensesRouter = Router();

// Per-route auth + tier gate. NOT a router-level `.use`: this router is mounted
// at the broad '/api' path, and a router-level requireConsoleTier there 403s
// unrelated /api/* requests (e.g. /api/shifts/* for non-foreman) before they ever
// reach their own router. Gating each route keeps the leak closed.
const consoleGate = [authenticateToken, requireConsoleTier];

expensesRouter.post('/expenses', consoleGate, idempotency(), validateBody(CreateExpenseBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const body = req.body as CreateExpenseBody;
      const job = await jobsService.getById(body.jobId);
      if (!job) return res.status(404).json({ error: 'Job not found' });
      if (job.foremanId !== req.user!.id) {
        return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
      }
      const expense = await expensesService.create(body, req.user!.id);
      res.status(201).json({ expense });
    } catch (e) {
      requestLogger().error({ event: 'expenses_create_error', err: e }, 'expenses create error');
      res.status(500).json({ error: 'Failed to create expense' });
    }
  });

expensesRouter.patch('/expenses/:id', consoleGate, requireExpenseOwner, validateBody(UpdateExpenseBody),
  async (req: ExpenseOwnerRequest, res: Response) => {
    try {
      const body = req.body as UpdateExpenseBody;
      const expense = await expensesService.update(req.expense!.id, body, req.user!.id);
      if (!expense) return res.status(404).json({ error: 'Expense not found' });
      res.json({ expense });
    } catch (e) {
      requestLogger().error({ event: 'expenses_update_error', err: e }, 'expenses update error');
      res.status(500).json({ error: 'Failed to update expense' });
    }
  });

expensesRouter.delete('/expenses/:id', consoleGate, requireExpenseOwner,
  async (req: ExpenseOwnerRequest, res: Response) => {
    try {
      await expensesService.hardDelete(req.expense!.id, req.user!.id);
      res.status(204).send();
    } catch (e) {
      requestLogger().error({ event: 'expenses_delete_error', err: e }, 'expenses delete error');
      res.status(500).json({ error: 'Failed to delete expense' });
    }
  });
