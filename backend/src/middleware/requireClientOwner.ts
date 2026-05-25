// backend/src/middleware/requireClientOwner.ts
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as clientsService from '../clientsService';

export interface ClientOwnerRequest extends AuthenticatedRequest {
  client?: clientsService.Client;
}

export async function requireClientOwner(req: ClientOwnerRequest, res: Response, next: NextFunction) {
  const id = req.params.id;
  if (!id) return res.status(400).json({ error: 'Missing client id' });
  try {
    const client = await clientsService.getById(req.user!.id, id);
    if (!client) return res.status(404).json({ error: 'Client not found' });
    req.client = client;
    next();
  } catch (err) {
    next(err);
  }
}
