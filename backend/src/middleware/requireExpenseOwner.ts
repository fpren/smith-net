import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as expensesService from '../expensesService';
import * as jobsService from '../jobsService';

export interface ExpenseOwnerRequest extends AuthenticatedRequest {
  expense?: expensesService.Expense;
  job?: jobsService.Job;
}

export async function requireExpenseOwner(
  req: ExpenseOwnerRequest,
  res: Response,
  next: NextFunction,
) {
  const id = req.params.id;
  if (!id) return res.status(400).json({ error: 'Missing expense id' });
  try {
    const expense = await expensesService.getById(id);
    if (!expense) return res.status(404).json({ error: 'Expense not found' });
    const job = await jobsService.getById(expense.jobId);
    if (!job) return res.status(404).json({ error: 'Parent job not found' });
    if (job.foremanId !== req.user!.id) {
      return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
    }
    req.expense = expense;
    req.job = job;
    next();
  } catch (err) {
    next(err);
  }
}
